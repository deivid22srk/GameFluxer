#include "native_network.h"
#include <sys/socket.h>
#include <netdb.h>
#include <unistd.h>
#include <cstring>
#include <sstream>
#include <algorithm>

namespace gamefluxer {

NetworkClient::NetworkClient() : success_(false), responseCode_(0) {}

NetworkClient::~NetworkClient() {}

size_t NetworkClient::WriteCallback(void* contents, size_t size, size_t nmemb, void* userp) {
    size_t totalSize = size * nmemb;
    std::string* str = static_cast<std::string*>(userp);
    str->append(static_cast<char*>(contents), totalSize);
    return totalSize;
}

std::string NetworkClient::downloadString(const std::string& url, int timeoutSeconds) {
    success_ = false;
    responseCode_ = 0;
    error_.clear();
    
    std::string host, path;
    int port = 80;
    bool useHttps = false;
    
    if (url.substr(0, 8) == "https://") {
        useHttps = true;
        port = 443;
        size_t hostStart = 8;
        size_t pathStart = url.find('/', hostStart);
        if (pathStart == std::string::npos) {
            host = url.substr(hostStart);
            path = "/";
        } else {
            host = url.substr(hostStart, pathStart - hostStart);
            path = url.substr(pathStart);
        }
    } else if (url.substr(0, 7) == "http://") {
        size_t hostStart = 7;
        size_t pathStart = url.find('/', hostStart);
        if (pathStart == std::string::npos) {
            host = url.substr(hostStart);
            path = "/";
        } else {
            host = url.substr(hostStart, pathStart - hostStart);
            path = url.substr(pathStart);
        }
    } else {
        error_ = "Invalid URL protocol";
        return "";
    }
    
    size_t colonPos = host.find(':');
    if (colonPos != std::string::npos) {
        port = std::stoi(host.substr(colonPos + 1));
        host = host.substr(0, colonPos);
    }
    
    struct addrinfo hints{}, *result;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    
    if (getaddrinfo(host.c_str(), std::to_string(port).c_str(), &hints, &result) != 0) {
        error_ = "Failed to resolve host";
        return "";
    }
    
    int sockfd = socket(result->ai_family, result->ai_socktype, result->ai_protocol);
    if (sockfd < 0) {
        error_ = "Failed to create socket";
        freeaddrinfo(result);
        return "";
    }
    
    struct timeval timeout;
    timeout.tv_sec = timeoutSeconds;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    
    if (connect(sockfd, result->ai_addr, result->ai_addrlen) < 0) {
        error_ = "Failed to connect";
        close(sockfd);
        freeaddrinfo(result);
        return "";
    }
    
    freeaddrinfo(result);
    
    std::ostringstream request;
    request << "GET " << path << " HTTP/1.1\r\n";
    request << "Host: " << host << "\r\n";
    request << "User-Agent: GameFluxer/1.0\r\n";
    request << "Connection: close\r\n";
    request << "\r\n";
    
    std::string requestStr = request.str();
    if (send(sockfd, requestStr.c_str(), requestStr.size(), 0) < 0) {
        error_ = "Failed to send request";
        close(sockfd);
        return "";
    }
    
    std::string response;
    char buffer[4096];
    ssize_t bytesRead;
    
    while ((bytesRead = recv(sockfd, buffer, sizeof(buffer), 0)) > 0) {
        response.append(buffer, bytesRead);
    }
    
    close(sockfd);
    
    size_t headerEnd = response.find("\r\n\r\n");
    if (headerEnd == std::string::npos) {
        error_ = "Invalid HTTP response";
        return "";
    }
    
    std::string headers = response.substr(0, headerEnd);
    std::string body = response.substr(headerEnd + 4);
    
    size_t statusPos = headers.find("HTTP/");
    if (statusPos != std::string::npos) {
        size_t codeStart = headers.find(' ', statusPos) + 1;
        size_t codeEnd = headers.find(' ', codeStart);
        std::string codeStr = headers.substr(codeStart, codeEnd - codeStart);
        responseCode_ = std::stoi(codeStr);
    }
    
    if (responseCode_ == 200) {
        success_ = true;
        return body;
    } else if (responseCode_ == 301 || responseCode_ == 302) {
        size_t locPos = headers.find("Location: ");
        if (locPos != std::string::npos) {
            size_t locStart = locPos + 10;
            size_t locEnd = headers.find("\r\n", locStart);
            std::string newUrl = headers.substr(locStart, locEnd - locStart);
            return downloadString(newUrl, timeoutSeconds);
        }
    }
    
    error_ = "HTTP " + std::to_string(responseCode_);
    return "";
}

bool NetworkClient::downloadFile(const std::string& url, const std::string& outputPath) {
    std::string content = downloadString(url);
    if (!success_) {
        return false;
    }
    
    FILE* file = fopen(outputPath.c_str(), "wb");
    if (!file) {
        error_ = "Failed to open output file";
        return false;
    }
    
    fwrite(content.c_str(), 1, content.size(), file);
    fclose(file);
    
    return true;
}

}
