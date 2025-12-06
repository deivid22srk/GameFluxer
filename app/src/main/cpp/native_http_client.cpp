#include "native_http_client.h"
#include "native_file_writer.h"
#include <android/log.h>
#include <sys/socket.h>
#include <netdb.h>
#include <unistd.h>
#include <fcntl.h>
#include <cstring>
#include <sstream>
#include <algorithm>

#define LOG_TAG "NativeHttpClient"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

NativeHttpClient::NativeHttpClient() : responseCode_(0) {}

NativeHttpClient::~NativeHttpClient() {}

bool NativeHttpClient::parseUrl(const std::string& url, std::string& host, 
                                 std::string& path, int& port, bool& useHttps) {
    useHttps = false;
    port = 80;
    
    size_t schemeEnd = url.find("://");
    if (schemeEnd == std::string::npos) {
        return false;
    }
    
    std::string scheme = url.substr(0, schemeEnd);
    if (scheme == "https") {
        useHttps = true;
        port = 443;
    } else if (scheme != "http") {
        return false;
    }
    
    size_t hostStart = schemeEnd + 3;
    size_t pathStart = url.find('/', hostStart);
    
    if (pathStart == std::string::npos) {
        host = url.substr(hostStart);
        path = "/";
    } else {
        host = url.substr(hostStart, pathStart - hostStart);
        path = url.substr(pathStart);
    }
    
    size_t colonPos = host.find(':');
    if (colonPos != std::string::npos) {
        port = std::stoi(host.substr(colonPos + 1));
        host = host.substr(0, colonPos);
    }
    
    return true;
}

std::string NativeHttpClient::buildHttpRequest(const std::string& host, const std::string& path,
                                                long long startByte, 
                                                const std::map<std::string, std::string>& customHeaders) {
    std::ostringstream request;
    request << "GET " << path << " HTTP/1.1\r\n";
    request << "Host: " << host << "\r\n";
    
    if (customHeaders.empty()) {
        request << "User-Agent: Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.120 Mobile Safari/537.36\r\n";
        request << "Accept: */*\r\n";
        request << "Accept-Encoding: identity\r\n";
    } else {
        for (const auto& [key, value] : customHeaders) {
            request << key << ": " << value << "\r\n";
        }
    }
    
    if (startByte > 0) {
        request << "Range: bytes=" << startByte << "-\r\n";
    }
    
    request << "Connection: close\r\n";
    request << "\r\n";
    
    return request.str();
}

bool NativeHttpClient::parseHttpResponse(const std::string& response, int& statusCode, long long& contentLength) {
    size_t statusLineEnd = response.find("\r\n");
    if (statusLineEnd == std::string::npos) {
        return false;
    }
    
    std::string statusLine = response.substr(0, statusLineEnd);
    
    size_t codeStart = statusLine.find(' ');
    if (codeStart == std::string::npos) {
        return false;
    }
    
    size_t codeEnd = statusLine.find(' ', codeStart + 1);
    if (codeEnd == std::string::npos) {
        return false;
    }
    
    statusCode = std::stoi(statusLine.substr(codeStart + 1, codeEnd - codeStart - 1));
    
    size_t contentLengthPos = response.find("Content-Length:");
    if (contentLengthPos == std::string::npos) {
        contentLengthPos = response.find("content-length:");
    }
    
    if (contentLengthPos != std::string::npos) {
        size_t valueStart = contentLengthPos + 15;
        size_t valueEnd = response.find("\r\n", valueStart);
        if (valueEnd != std::string::npos) {
            std::string lengthStr = response.substr(valueStart, valueEnd - valueStart);
            lengthStr.erase(0, lengthStr.find_first_not_of(" \t"));
            lengthStr.erase(lengthStr.find_last_not_of(" \t") + 1);
            contentLength = std::stoll(lengthStr);
        }
    }
    
    return true;
}

bool NativeHttpClient::connectToServer(const std::string& url, long long startByte,
                                        const std::map<std::string, std::string>& customHeaders,
                                        int& sockfd, std::string& host, std::string& path) {
    int port;
    bool useHttps;
    
    if (!parseUrl(url, host, path, port, useHttps)) {
        error_ = "Invalid URL";
        LOGE("Invalid URL: %s", url.c_str());
        return false;
    }
    
    if (useHttps) {
        error_ = "HTTPS not supported in basic implementation";
        LOGE("%s", error_.c_str());
        return false;
    }
    
    LOGD("Connecting to %s:%d%s", host.c_str(), port, path.c_str());
    
    struct addrinfo hints{}, *result;
    memset(&hints, 0, sizeof(hints));
    hints.ai_family = AF_UNSPEC;
    hints.ai_socktype = SOCK_STREAM;
    
    if (getaddrinfo(host.c_str(), std::to_string(port).c_str(), &hints, &result) != 0) {
        error_ = "Failed to resolve host: " + host;
        LOGE("%s", error_.c_str());
        return false;
    }
    
    sockfd = socket(result->ai_family, result->ai_socktype, result->ai_protocol);
    if (sockfd < 0) {
        error_ = "Failed to create socket";
        LOGE("%s", error_.c_str());
        freeaddrinfo(result);
        return false;
    }
    
    struct timeval timeout;
    timeout.tv_sec = 15;
    timeout.tv_usec = 0;
    setsockopt(sockfd, SOL_SOCKET, SO_RCVTIMEO, &timeout, sizeof(timeout));
    setsockopt(sockfd, SOL_SOCKET, SO_SNDTIMEO, &timeout, sizeof(timeout));
    
    if (connect(sockfd, result->ai_addr, result->ai_addrlen) < 0) {
        error_ = "Failed to connect to server";
        LOGE("%s", error_.c_str());
        close(sockfd);
        freeaddrinfo(result);
        return false;
    }
    
    freeaddrinfo(result);
    LOGD("Connected successfully");
    return true;
}

bool NativeHttpClient::downloadFile(const std::string& url, const std::string& outputPath,
                                     long long existingBytes, 
                                     const std::map<std::string, std::string>& customHeaders,
                                     ProgressCallback progressCallback,
                                     std::atomic<bool>& shouldStop) {
    error_.clear();
    responseCode_ = 0;
    
    int sockfd = -1;
    std::string host, path;
    
    if (!connectToServer(url, existingBytes, customHeaders, sockfd, host, path)) {
        return false;
    }
    
    std::string request = buildHttpRequest(host, path, existingBytes, customHeaders);
    LOGD("Sending HTTP request");
    
    if (send(sockfd, request.c_str(), request.size(), 0) < 0) {
        error_ = "Failed to send HTTP request";
        LOGE("%s", error_.c_str());
        close(sockfd);
        return false;
    }
    
    std::string headerBuffer;
    char byte;
    bool headersComplete = false;
    
    while (!headersComplete && recv(sockfd, &byte, 1, 0) > 0) {
        headerBuffer += byte;
        if (headerBuffer.size() >= 4 &&
            headerBuffer.substr(headerBuffer.size() - 4) == "\r\n\r\n") {
            headersComplete = true;
        }
    }
    
    if (!headersComplete) {
        error_ = "Failed to receive HTTP headers";
        LOGE("%s", error_.c_str());
        close(sockfd);
        return false;
    }
    
    long long contentLength = 0;
    if (!parseHttpResponse(headerBuffer, responseCode_, contentLength)) {
        error_ = "Failed to parse HTTP response";
        LOGE("%s", error_.c_str());
        close(sockfd);
        return false;
    }
    
    LOGD("HTTP %d, Content-Length: %lld", responseCode_, contentLength);
    
    if (responseCode_ < 200 || responseCode_ >= 300) {
        error_ = "HTTP error: " + std::to_string(responseCode_);
        LOGE("%s", error_.c_str());
        close(sockfd);
        return false;
    }
    
    NativeFileWriter writer(outputPath, existingBytes > 0);
    if (!writer.open()) {
        error_ = "Failed to open output file: " + writer.getError();
        LOGE("%s", error_.c_str());
        close(sockfd);
        return false;
    }
    
    const int BUFFER_SIZE = 65536;
    char buffer[BUFFER_SIZE];
    long long totalBytes = existingBytes + contentLength;
    long long downloadedBytes = existingBytes;
    
    auto lastProgressTime = std::chrono::steady_clock::now();
    long long lastDownloadedBytes = downloadedBytes;
    
    LOGD("Starting download loop");
    
    while (!shouldStop.load()) {
        ssize_t bytesRead = recv(sockfd, buffer, BUFFER_SIZE, 0);
        
        if (bytesRead <= 0) {
            break;
        }
        
        if (!writer.write(buffer, bytesRead)) {
            error_ = "Failed to write to file: " + writer.getError();
            LOGE("%s", error_.c_str());
            close(sockfd);
            return false;
        }
        
        downloadedBytes += bytesRead;
        
        auto currentTime = std::chrono::steady_clock::now();
        auto timeDiff = std::chrono::duration_cast<std::chrono::milliseconds>(
            currentTime - lastProgressTime).count();
        
        if (timeDiff >= 500) {
            long long bytesDiff = downloadedBytes - lastDownloadedBytes;
            long long speed = timeDiff > 0 ? (bytesDiff * 1000 / timeDiff) : 0;
            
            if (progressCallback) {
                progressCallback(downloadedBytes, totalBytes, speed);
            }
            
            lastProgressTime = currentTime;
            lastDownloadedBytes = downloadedBytes;
        }
    }
    
    writer.flush();
    writer.close();
    close(sockfd);
    
    if (shouldStop.load()) {
        LOGD("Download stopped by user");
        return false;
    }
    
    LOGD("Download completed: %lld bytes", downloadedBytes);
    return true;
}

long long NativeHttpClient::getContentLength(const std::string& url,
                                              const std::map<std::string, std::string>& customHeaders) {
    int sockfd = -1;
    std::string host, path;
    
    if (!connectToServer(url, 0, customHeaders, sockfd, host, path)) {
        return 0;
    }
    
    std::ostringstream request;
    request << "HEAD " << path << " HTTP/1.1\r\n";
    request << "Host: " << host << "\r\n";
    request << "Connection: close\r\n";
    request << "\r\n";
    
    std::string requestStr = request.str();
    send(sockfd, requestStr.c_str(), requestStr.size(), 0);
    
    std::string headerBuffer;
    char byte;
    bool headersComplete = false;
    
    while (!headersComplete && recv(sockfd, &byte, 1, 0) > 0) {
        headerBuffer += byte;
        if (headerBuffer.size() >= 4 &&
            headerBuffer.substr(headerBuffer.size() - 4) == "\r\n\r\n") {
            headersComplete = true;
        }
    }
    
    close(sockfd);
    
    int statusCode;
    long long contentLength = 0;
    parseHttpResponse(headerBuffer, statusCode, contentLength);
    
    return contentLength;
}

}
