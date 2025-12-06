#ifndef GAMEFLUXER_NATIVE_FILE_WRITER_H
#define GAMEFLUXER_NATIVE_FILE_WRITER_H

#include <string>
#include <fstream>
#include <memory>
#include <atomic>

namespace gamefluxer {

class NativeFileWriter {
public:
    explicit NativeFileWriter(const std::string& filePath, bool append = false);
    ~NativeFileWriter();
    
    bool open();
    bool write(const char* data, size_t size);
    bool flush();
    void close();
    
    bool isOpen() const { return isOpen_; }
    long long getBytesWritten() const { return bytesWritten_; }
    std::string getError() const { return error_; }
    
private:
    std::string filePath_;
    bool append_;
    bool isOpen_;
    std::atomic<long long> bytesWritten_;
    std::string error_;
    
    int fd_;
    std::unique_ptr<char[]> writeBuffer_;
    size_t bufferSize_;
    size_t bufferUsed_;
    
    bool createDirectories();
};

}

#endif
