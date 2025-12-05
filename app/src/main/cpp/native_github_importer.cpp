#include "native_github_importer.h"
#include "native_network.h"
#include "native_json_parser.h"
#include <algorithm>
#include <android/log.h>
#include <sstream>
#include <cstring>

#define LOG_TAG "NativeGitHubImporter"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

static const char BASE64_CHARS[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

std::string base64_decode(const std::string& encoded) {
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

GitHubImporter::GitHubImporter(const std::string& repoUrl) 
    : repoUrl_(repoUrl) {
}

bool GitHubImporter::parseRepoUrl() {
    std::string cleanUrl = repoUrl_;
    
    if (cleanUrl.back() == '/') {
        cleanUrl.pop_back();
    }
    
    if (cleanUrl.size() > 4 && cleanUrl.substr(cleanUrl.size() - 4) == ".git") {
        cleanUrl = cleanUrl.substr(0, cleanUrl.size() - 4);
    }
    
    size_t pos = cleanUrl.find("github.com/");
    if (pos == std::string::npos) {
        return false;
    }
    
    std::string path = cleanUrl.substr(pos + 11);
    
    size_t slashPos = path.find('/');
    if (slashPos == std::string::npos) {
        return false;
    }
    
    owner_ = path.substr(0, slashPos);
    repo_ = path.substr(slashPos + 1);
    
    slashPos = repo_.find('/');
    if (slashPos != std::string::npos) {
        repo_ = repo_.substr(0, slashPos);
    }
    
    LOGD("Parsed repo: %s/%s", owner_.c_str(), repo_.c_str());
    return !owner_.empty() && !repo_.empty();
}

bool GitHubImporter::isBase64(const std::string& str) {
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

std::string GitHubImporter::decodeIfBase64(const std::string& content) {
    std::string trimmed = content;
    trimmed.erase(0, trimmed.find_first_not_of(" \t\n\r"));
    trimmed.erase(trimmed.find_last_not_of(" \t\n\r") + 1);
    
    if (!trimmed.empty() && (trimmed[0] == '{' || trimmed[0] == '[')) {
        return content;
    }
    
    if (isBase64(trimmed)) {
        LOGD("Detected Base64 content, decoding...");
        return base64_decode(trimmed);
    }
    
    return content;
}

std::string GitHubImporter::downloadFile(const std::string& path) {
    NetworkClient client;
    
    std::string url = "https://raw.githubusercontent.com/" + owner_ + "/" + repo_ + "/main/" + path;
    LOGD("Downloading: %s", url.c_str());
    
    std::string content = client.downloadString(url, 15);
    
    if (!client.isSuccess() && client.getResponseCode() == 404) {
        url = "https://raw.githubusercontent.com/" + owner_ + "/" + repo_ + "/master/" + path;
        LOGD("Trying master branch: %s", url.c_str());
        content = client.downloadString(url, 15);
    }
    
    if (client.isSuccess()) {
        LOGD("Downloaded %s: %zu bytes", path.c_str(), content.size());
        return content;
    }
    
    LOGE("Failed to download %s: %s", path.c_str(), client.getError().c_str());
    return "";
}

ImportResult GitHubImporter::import() {
    ImportResult result{};
    result.success = false;
    result.totalGames = 0;
    
    LOGD("Starting import from GitHub: %s", repoUrl_.c_str());
    
    if (!parseRepoUrl()) {
        result.error = "Invalid GitHub repository URL";
        LOGE("%s", result.error.c_str());
        return result;
    }
    
    std::string configJson = downloadFile("config.json");
    if (configJson.empty()) {
        result.error = "config.json not found in repository";
        LOGE("%s", result.error.c_str());
        return result;
    }
    
    std::string decodedConfigJson = decodeIfBase64(configJson);
    LOGD("Config JSON downloaded and decoded");
    
    if (!JsonParser::isJsonValid(decodedConfigJson)) {
        result.error = "Invalid config.json format";
        LOGE("%s", result.error.c_str());
        return result;
    }
    
    result.config = JsonParser::parseConfig(decodedConfigJson);
    
    for (const auto& platform : result.config.platforms) {
        std::string dbContent = downloadFile(platform.databasePath);
        if (!dbContent.empty()) {
            std::string decodedDbContent = decodeIfBase64(dbContent);
            std::vector<Game> games = JsonParser::parseGames(decodedDbContent);
            LOGD("Loaded %zu games for platform %s", games.size(), platform.name.c_str());
            result.games[platform.name] = games;
            result.totalGames += games.size();
        } else {
            LOGD("Database file not found: %s", platform.databasePath.c_str());
        }
    }
    
    result.success = true;
    LOGD("Import successful. Total games: %d", result.totalGames);
    return result;
}

}
