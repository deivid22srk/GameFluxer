#ifndef GAMEFLUXER_NATIVE_HTTP_CLIENT_H
#define GAMEFLUXER_NATIVE_HTTP_CLIENT_H

#include "native_download_types.h"
#include <string>
#include <map>
#include <memory>

namespace gamefluxer {

class NativeHttpClient {
public:
    NativeHttpClient();
    ~NativeHttpClient();
    
    bool downloadFile(
        const std::string& url,
        const std::string& outputPath,
        long long existingBytes,
        const std::map<std::string, std::string>& customHeaders,
        ProgressCallback progressCallback,
        std::atomic<bool>& shouldStop
    );
    
    long long getContentLength(
        const std::string& url,
        const std::map<std::string, std::string>& customHeaders
    );
    
    int getResponseCode() const { return responseCode_; }
    std::string getError() const { return error_; }
    
private:
    int responseCode_;
    std::string error_;
    
    bool connectToServer(
        const std::string& url,
        long long startByte,
        const std::map<std::string, std::string>& customHeaders,
        int& sockfd,
        std::string& host,
        std::string& path
    );
    
    bool parseUrl(const std::string& url, std::string& host, std::string& path, int& port, bool& useHttps);
    std::string buildHttpRequest(const std::string& host, const std::string& path, 
                                  long long startByte, const std::map<std::string, std::string>& customHeaders);
    bool parseHttpResponse(const std::string& response, int& statusCode, long long& contentLength);
};

}

#endif
