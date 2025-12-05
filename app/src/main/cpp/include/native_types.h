#ifndef GAMEFLUXER_NATIVE_TYPES_H
#define GAMEFLUXER_NATIVE_TYPES_H

#include <string>
#include <vector>
#include <memory>
#include <unordered_map>

namespace gamefluxer {

struct Game {
    std::string id;
    std::string name;
    std::string description;
    std::string version;
    std::string size;
    float rating;
    std::string developer;
    std::string category;
    std::string platform;
    std::string iconUrl;
    std::string bannerUrl;
    std::string screenshots;
    std::string downloadUrl;
    std::string releaseDate;
};

struct Platform {
    std::string name;
    std::string databasePath;
    bool extendedDownloadsEnabled;
};

struct DatabaseConfig {
    std::vector<Platform> platforms;
};

struct ImportResult {
    bool success;
    std::string error;
    DatabaseConfig config;
    std::unordered_map<std::string, std::vector<Game>> games;
    int totalGames;
};

}

#endif
