#ifndef GAMEFLUXER_NATIVE_NETWORK_H
#define GAMEFLUXER_NATIVE_NETWORK_H

#include <string>
#include <memory>

namespace gamefluxer {

class NetworkClient {
public:
    NetworkClient();
    ~NetworkClient();
    
    std::string downloadString(const std::string& url, int timeoutSeconds = 15);
    bool downloadFile(const std::string& url, const std::string& outputPath);
    
    bool isSuccess() const { return success_; }
    int getResponseCode() const { return responseCode_; }
    std::string getError() const { return error_; }
    
private:
    bool success_;
    int responseCode_;
    std::string error_;
    
    static size_t WriteCallback(void* contents, size_t size, size_t nmemb, void* userp);
};

}

#endif
