#include "native_zip_importer.h"
#include "native_json_parser.h"
#include <android/log.h>
#include <zlib.h>
#include <fstream>
#include <sstream>
#include <cstring>
#include <sys/stat.h>
#include <dirent.h>
#include <unistd.h>

#define LOG_TAG "NativeZipImporter"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

static const char BASE64_CHARS[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string base64_decode_zip(const std::string& encoded) {
    std::string decoded;
    decoded.reserve((encoded.size() * 3) / 4);
    
    int val = 0;
    int valb = -8;
    
    for (unsigned char c : encoded) {
        if (c == '=') break;
        
        const char* pos = strchr(BASE64_CHARS, c);
        if (!pos) continue;
        
        val = (val << 6) + (pos - BASE64_CHARS);
        valb += 6;
        
        if (valb >= 0) {
            decoded.push_back(char((val >> valb) & 0xFF));
            valb -= 8;
        }
    }
    
    return decoded;
}

struct ZipLocalFileHeader {
    uint32_t signature;
    uint16_t versionNeeded;
    uint16_t flags;
    uint16_t compression;
    uint16_t modTime;
    uint16_t modDate;
    uint32_t crc32;
    uint32_t compressedSize;
    uint32_t uncompressedSize;
    uint16_t filenameLength;
    uint16_t extraFieldLength;
};

ZipImporter::ZipImporter(const std::string& zipPath) 
    : zipPath_(zipPath), tempDir_("/data/local/tmp/gamefluxer_import") {
}

bool ZipImporter::isBase64(const std::string& str) {
    if (str.empty() || str.length() < 100 || str.length() % 4 != 0) {
        return false;
    }
    
    for (char c : str) {
        if (!isalnum(c) && c != '+' && c != '/' && c != '=') {
            return false;
        }
    }
    
    return true;
}

std::string ZipImporter::decodeIfBase64(const std::string& content) {
    std::string trimmed = content;
    trimmed.erase(0, trimmed.find_first_not_of(" \t\n\r"));
    trimmed.erase(trimmed.find_last_not_of(" \t\n\r") + 1);
    
    if (!trimmed.empty() && (trimmed[0] == '{' || trimmed[0] == '[')) {
        return content;
    }
    
    if (isBase64(trimmed)) {
        LOGD("Detected Base64 content, decoding...");
        return base64_decode_zip(trimmed);
    }
    
    return content;
}

void ZipImporter::cleanup() {
    std::string cmd = "rm -rf " + tempDir_;
    system(cmd.c_str());
}

bool ZipImporter::extractZip() {
    mkdir(tempDir_.c_str(), 0777);
    
    std::ifstream zipFile(zipPath_, std::ios::binary);
    if (!zipFile.is_open()) {
        LOGE("Failed to open ZIP file: %s", zipPath_.c_str());
        return false;
    }
    
    while (true) {
        ZipLocalFileHeader header{};
        zipFile.read(reinterpret_cast<char*>(&header.signature), 4);
        
        if (header.signature != 0x04034b50) {
            break;
        }
        
        zipFile.read(reinterpret_cast<char*>(&header.versionNeeded), 2);
        zipFile.read(reinterpret_cast<char*>(&header.flags), 2);
        zipFile.read(reinterpret_cast<char*>(&header.compression), 2);
        zipFile.read(reinterpret_cast<char*>(&header.modTime), 2);
        zipFile.read(reinterpret_cast<char*>(&header.modDate), 2);
        zipFile.read(reinterpret_cast<char*>(&header.crc32), 4);
        zipFile.read(reinterpret_cast<char*>(&header.compressedSize), 4);
        zipFile.read(reinterpret_cast<char*>(&header.uncompressedSize), 4);
        zipFile.read(reinterpret_cast<char*>(&header.filenameLength), 2);
        zipFile.read(reinterpret_cast<char*>(&header.extraFieldLength), 2);
        
        std::string filename(header.filenameLength, '\0');
        zipFile.read(&filename[0], header.filenameLength);
        
        zipFile.ignore(header.extraFieldLength);
        
        std::string outputPath = tempDir_ + "/" + filename;
        
        if (filename.back() == '/') {
            mkdir(outputPath.c_str(), 0777);
            LOGD("Created directory: %s", outputPath.c_str());
        } else {
            size_t lastSlash = outputPath.find_last_of('/');
            if (lastSlash != std::string::npos) {
                std::string dir = outputPath.substr(0, lastSlash);
                mkdir(dir.c_str(), 0777);
            }
            
            std::vector<char> compressedData(header.compressedSize);
            zipFile.read(compressedData.data(), header.compressedSize);
            
            if (header.compression == 0) {
                std::ofstream outFile(outputPath, std::ios::binary);
                outFile.write(compressedData.data(), header.compressedSize);
                outFile.close();
            } else if (header.compression == 8) {
                std::vector<char> uncompressedData(header.uncompressedSize);
                
                z_stream stream{};
                stream.next_in = reinterpret_cast<Bytef*>(compressedData.data());
                stream.avail_in = header.compressedSize;
                stream.next_out = reinterpret_cast<Bytef*>(uncompressedData.data());
                stream.avail_out = header.uncompressedSize;
                
                if (inflateInit2(&stream, -MAX_WBITS) == Z_OK) {
                    inflate(&stream, Z_FINISH);
                    inflateEnd(&stream);
                    
                    std::ofstream outFile(outputPath, std::ios::binary);
                    outFile.write(uncompressedData.data(), header.uncompressedSize);
                    outFile.close();
                }
            }
            
            LOGD("Extracted: %s (%u bytes)", filename.c_str(), header.uncompressedSize);
        }
    }
    
    zipFile.close();
    return true;
}

std::string ZipImporter::findConfigFile() {
    std::function<std::string(const std::string&)> searchDir = [&](const std::string& dir) -> std::string {
        DIR* d = opendir(dir.c_str());
        if (!d) return "";
        
        struct dirent* entry;
        while ((entry = readdir(d)) != nullptr) {
            if (entry->d_name[0] == '.') continue;
            
            std::string fullPath = dir + "/" + entry->d_name;
            
            if (entry->d_type == DT_REG && strcmp(entry->d_name, "config.json") == 0) {
                closedir(d);
                return fullPath;
            } else if (entry->d_type == DT_DIR) {
                std::string found = searchDir(fullPath);
                if (!found.empty()) {
                    closedir(d);
                    return found;
                }
            }
        }
        
        closedir(d);
        return "";
    };
    
    return searchDir(tempDir_);
}

ImportResult ZipImporter::import() {
    ImportResult result{};
    result.success = false;
    result.totalGames = 0;
    
    LOGD("Starting import from ZIP: %s", zipPath_.c_str());
    
    cleanup();
    
    if (!extractZip()) {
        result.error = "Failed to extract ZIP file";
        LOGE("%s", result.error.c_str());
        return result;
    }
    
    std::string configPath = findConfigFile();
    if (configPath.empty()) {
        result.error = "config.json not found in ZIP file";
        LOGE("%s", result.error.c_str());
        cleanup();
        return result;
    }
    
    LOGD("Found config.json at: %s", configPath.c_str());
    
    std::string baseDir = configPath.substr(0, configPath.find_last_of('/'));
    
    std::ifstream configFile(configPath);
    std::stringstream buffer;
    buffer << configFile.rdbuf();
    std::string configJson = buffer.str();
    configFile.close();
    
    std::string decodedConfigJson = decodeIfBase64(configJson);
    LOGD("Config JSON loaded and decoded");
    
    if (!JsonParser::isJsonValid(decodedConfigJson)) {
        result.error = "Invalid config.json format";
        LOGE("%s", result.error.c_str());
        cleanup();
        return result;
    }
    
    result.config = JsonParser::parseConfig(decodedConfigJson);
    
    for (const auto& platform : result.config.platforms) {
        std::string dbPath = baseDir + "/" + platform.databasePath;
        
        std::ifstream dbFile(dbPath);
        if (dbFile.is_open()) {
            std::stringstream dbBuffer;
            dbBuffer << dbFile.rdbuf();
            std::string dbContent = dbBuffer.str();
            dbFile.close();
            
            std::string decodedDbContent = decodeIfBase64(dbContent);
            std::vector<Game> games = JsonParser::parseGames(decodedDbContent);
            LOGD("Loaded %zu games for platform %s", games.size(), platform.name.c_str());
            result.games[platform.name] = games;
            result.totalGames += games.size();
        } else {
            LOGD("Database file not found: %s", dbPath.c_str());
        }
    }
    
    cleanup();
    
    result.success = true;
    LOGD("Import successful. Total games: %d", result.totalGames);
    return result;
}

}
