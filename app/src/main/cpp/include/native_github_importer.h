#ifndef GAMEFLUXER_NATIVE_GITHUB_IMPORTER_H
#define GAMEFLUXER_NATIVE_GITHUB_IMPORTER_H

#include "native_types.h"
#include <string>

namespace gamefluxer {

class GitHubImporter {
public:
    explicit GitHubImporter(const std::string& repoUrl);
    
    ImportResult import();
    
private:
    std::string repoUrl_;
    std::string owner_;
    std::string repo_;
    
    bool parseRepoUrl();
    std::string downloadFile(const std::string& path);
    std::string decodeIfBase64(const std::string& content);
    bool isBase64(const std::string& str);
};

}

#endif
