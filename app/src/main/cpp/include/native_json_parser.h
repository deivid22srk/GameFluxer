#ifndef GAMEFLUXER_NATIVE_JSON_PARSER_H
#define GAMEFLUXER_NATIVE_JSON_PARSER_H

#include "native_types.h"
#include <string>
#include <vector>

namespace gamefluxer {

class JsonParser {
public:
    static DatabaseConfig parseConfig(const std::string& json);
    static std::vector<Game> parseGames(const std::string& json);
    static std::string getString(const std::string& json, const std::string& key);
    static float getFloat(const std::string& json, const std::string& key);
    static bool isJsonValid(const std::string& json);
    
private:
    static std::string extractValue(const std::string& json, const std::string& key);
    static std::string trim(const std::string& str);
    static std::string unescapeJson(const std::string& str);
};

}

#endif
