#ifndef GAMEFLUXER_NATIVE_ZIP_IMPORTER_H
#define GAMEFLUXER_NATIVE_ZIP_IMPORTER_H

#include "native_types.h"
#include <string>

namespace gamefluxer {

class ZipImporter {
public:
    explicit ZipImporter(const std::string& zipPath);
    
    ImportResult import();
    
private:
    std::string zipPath_;
    std::string tempDir_;
    
    bool extractZip();
    std::string findConfigFile();
    std::string decodeIfBase64(const std::string& content);
    bool isBase64(const std::string& str);
    void cleanup();
};

}

#endif
