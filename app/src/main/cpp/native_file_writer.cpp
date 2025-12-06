#include "native_file_writer.h"
#include <android/log.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include <cstring>

#define LOG_TAG "NativeFileWriter"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

NativeFileWriter::NativeFileWriter(const std::string& filePath, bool append)
    : filePath_(filePath), append_(append), isOpen_(false), 
      bytesWritten_(0), fd_(-1), bufferSize_(65536), bufferUsed_(0) {
    writeBuffer_ = std::make_unique<char[]>(bufferSize_);
}

NativeFileWriter::~NativeFileWriter() {
    close();
}

bool NativeFileWriter::createDirectories() {
    size_t pos = 0;
    while ((pos = filePath_.find('/', pos + 1)) != std::string::npos) {
        std::string dir = filePath_.substr(0, pos);
        mkdir(dir.c_str(), 0755);
    }
    
    size_t lastSlash = filePath_.find_last_of('/');
    if (lastSlash != std::string::npos) {
        std::string dir = filePath_.substr(0, lastSlash);
        if (mkdir(dir.c_str(), 0755) == 0) {
            LOGD("Created directory: %s", dir.c_str());
        }
    }
    
    return true;
}

bool NativeFileWriter::open() {
    if (isOpen_) {
        return true;
    }
    
    createDirectories();
    
    int flags = O_WRONLY | O_CREAT;
    if (append_) {
        flags |= O_APPEND;
    } else {
        flags |= O_TRUNC;
    }
    
    fd_ = ::open(filePath_.c_str(), flags, 0644);
    
    if (fd_ < 0) {
        error_ = "Failed to open file: " + std::string(strerror(errno));
        LOGE("%s", error_.c_str());
        return false;
    }
    
    isOpen_ = true;
    LOGD("File opened: %s (append=%d)", filePath_.c_str(), append_);
    return true;
}

bool NativeFileWriter::write(const char* data, size_t size) {
    if (!isOpen_) {
        error_ = "File not open";
        return false;
    }
    
    if (bufferUsed_ + size > bufferSize_) {
        if (!flush()) {
            return false;
        }
    }
    
    if (size >= bufferSize_) {
        ssize_t written = ::write(fd_, data, size);
        if (written < 0) {
            error_ = "Write failed: " + std::string(strerror(errno));
            LOGE("%s", error_.c_str());
            return false;
        }
        bytesWritten_ += written;
        return true;
    }
    
    memcpy(writeBuffer_.get() + bufferUsed_, data, size);
    bufferUsed_ += size;
    bytesWritten_ += size;
    
    return true;
}

bool NativeFileWriter::flush() {
    if (!isOpen_ || bufferUsed_ == 0) {
        return true;
    }
    
    ssize_t written = ::write(fd_, writeBuffer_.get(), bufferUsed_);
    if (written < 0) {
        error_ = "Flush failed: " + std::string(strerror(errno));
        LOGE("%s", error_.c_str());
        return false;
    }
    
    bufferUsed_ = 0;
    return true;
}

void NativeFileWriter::close() {
    if (isOpen_) {
        flush();
        ::close(fd_);
        fd_ = -1;
        isOpen_ = false;
        LOGD("File closed: %lld bytes written", bytesWritten_.load());
    }
}

}
