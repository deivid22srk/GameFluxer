#include "native_json_parser.h"
#include <algorithm>
#include <sstream>
#include <cstring>

namespace gamefluxer {

std::string JsonParser::trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\n\r\"");
    if (first == std::string::npos) return "";
    size_t last = str.find_last_not_of(" \t\n\r\"");
    return str.substr(first, (last - first + 1));
}

std::string JsonParser::unescapeJson(const std::string& str) {
    std::string result;
    result.reserve(str.size());
    
    for (size_t i = 0; i < str.size(); ++i) {
        if (str[i] == '\\' && i + 1 < str.size()) {
            switch (str[i + 1]) {
                case 'n': result += '\n'; ++i; break;
                case 't': result += '\t'; ++i; break;
                case 'r': result += '\r'; ++i; break;
                case '\\': result += '\\'; ++i; break;
                case '"': result += '"'; ++i; break;
                default: result += str[i]; break;
            }
        } else {
            result += str[i];
        }
    }
    return result;
}

std::string JsonParser::extractValue(const std::string& json, const std::string& key) {
    std::string searchKey = "\"" + key + "\"";
    size_t keyPos = json.find(searchKey);
    
    if (keyPos == std::string::npos) {
        return "";
    }
    
    size_t colonPos = json.find(':', keyPos);
    if (colonPos == std::string::npos) {
        return "";
    }
    
    size_t valueStart = json.find_first_not_of(" \t\n\r", colonPos + 1);
    if (valueStart == std::string::npos) {
        return "";
    }
    
    size_t valueEnd;
    if (json[valueStart] == '"') {
        valueStart++;
        valueEnd = valueStart;
        while (valueEnd < json.size()) {
            if (json[valueEnd] == '"' && (valueEnd == 0 || json[valueEnd - 1] != '\\')) {
                break;
            }
            valueEnd++;
        }
    } else if (json[valueStart] == '[') {
        int depth = 1;
        valueEnd = valueStart + 1;
        while (valueEnd < json.size() && depth > 0) {
            if (json[valueEnd] == '[') depth++;
            else if (json[valueEnd] == ']') depth--;
            valueEnd++;
        }
        return json.substr(valueStart, valueEnd - valueStart);
    } else {
        valueEnd = json.find_first_of(",}\n\r", valueStart);
        if (valueEnd == std::string::npos) {
            valueEnd = json.size();
        }
    }
    
    return trim(json.substr(valueStart, valueEnd - valueStart));
}

std::string JsonParser::getString(const std::string& json, const std::string& key) {
    return unescapeJson(extractValue(json, key));
}

float JsonParser::getFloat(const std::string& json, const std::string& key) {
    std::string value = extractValue(json, key);
    if (value.empty()) return 0.0f;
    return std::stof(value);
}

bool JsonParser::isJsonValid(const std::string& json) {
    if (json.empty()) return false;
    std::string trimmed = trim(json);
    return !trimmed.empty() && (trimmed[0] == '{' || trimmed[0] == '[');
}

DatabaseConfig JsonParser::parseConfig(const std::string& json) {
    DatabaseConfig config;
    
    size_t platformsStart = json.find("\"platforms\"");
    if (platformsStart == std::string::npos) {
        return config;
    }
    
    size_t arrayStart = json.find('[', platformsStart);
    if (arrayStart == std::string::npos) {
        return config;
    }
    
    size_t arrayEnd = arrayStart + 1;
    int depth = 1;
    while (arrayEnd < json.size() && depth > 0) {
        if (json[arrayEnd] == '[') depth++;
        else if (json[arrayEnd] == ']') depth--;
        arrayEnd++;
    }
    
    std::string platformsArray = json.substr(arrayStart + 1, arrayEnd - arrayStart - 2);
    
    size_t pos = 0;
    while (pos < platformsArray.size()) {
        size_t objStart = platformsArray.find('{', pos);
        if (objStart == std::string::npos) break;
        
        size_t objEnd = objStart + 1;
        int objDepth = 1;
        while (objEnd < platformsArray.size() && objDepth > 0) {
            if (platformsArray[objEnd] == '{') objDepth++;
            else if (platformsArray[objEnd] == '}') objDepth--;
            objEnd++;
        }
        
        std::string platformJson = platformsArray.substr(objStart, objEnd - objStart);
        
        Platform platform;
        platform.name = getString(platformJson, "name");
        platform.databasePath = getString(platformJson, "databasePath");
        platform.extendedDownloadsEnabled = platformJson.find("\"enabled\"") != std::string::npos &&
                                           platformJson.find("true", platformJson.find("\"enabled\"")) != std::string::npos;
        
        config.platforms.push_back(platform);
        pos = objEnd;
    }
    
    return config;
}

std::vector<Game> JsonParser::parseGames(const std::string& json) {
    std::vector<Game> games;
    
    if (json.empty() || json[0] != '[') {
        return games;
    }
    
    size_t pos = 1;
    while (pos < json.size()) {
        size_t objStart = json.find('{', pos);
        if (objStart == std::string::npos) break;
        
        size_t objEnd = objStart + 1;
        int depth = 1;
        while (objEnd < json.size() && depth > 0) {
            if (json[objEnd] == '{') depth++;
            else if (json[objEnd] == '}') depth--;
            objEnd++;
        }
        
        std::string gameJson = json.substr(objStart, objEnd - objStart);
        
        Game game;
        game.id = getString(gameJson, "id");
        game.name = getString(gameJson, "name");
        game.description = getString(gameJson, "description");
        game.version = getString(gameJson, "version");
        game.size = getString(gameJson, "size");
        game.rating = getFloat(gameJson, "rating");
        game.developer = getString(gameJson, "developer");
        game.category = getString(gameJson, "category");
        game.platform = getString(gameJson, "platform");
        game.iconUrl = getString(gameJson, "iconUrl");
        game.bannerUrl = getString(gameJson, "bannerUrl");
        game.screenshots = getString(gameJson, "screenshots");
        game.downloadUrl = getString(gameJson, "downloadUrl");
        game.releaseDate = getString(gameJson, "releaseDate");
        
        games.push_back(game);
        pos = objEnd;
    }
    
    return games;
}

}
