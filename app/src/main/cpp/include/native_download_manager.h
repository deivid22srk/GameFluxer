#ifndef GAMEFLUXER_NATIVE_DOWNLOAD_MANAGER_H
#define GAMEFLUXER_NATIVE_DOWNLOAD_MANAGER_H

#include <string>
#include <functional>
#include <atomic>
#include <memory>

namespace gamefluxer {

enum class DownloadStatus {
    IDLE,
    DOWNLOADING,
    PAUSED,
    COMPLETED,
    FAILED,
    CANCELLED
};

struct DownloadProgress {
    long long bytesDownloaded;
    long long totalBytes;
    float progress;
    float speed;
    DownloadStatus status;
    std::string error;
};

class NativeDownloadManager {
public:
    using ProgressCallback = std::function<void(const DownloadProgress&)>;
    
    NativeDownloadManager();
    ~NativeDownloadManager();
    
    int startDownload(const std::string& url, const std::string& outputPath, ProgressCallback callback);
    void pauseDownload(int downloadId);
    void resumeDownload(int downloadId);
    void cancelDownload(int downloadId);
    
    DownloadProgress getProgress(int downloadId);
    
    void setMaxConcurrentDownloads(int max);
    void setChunkSize(int size);
    void setConnectionTimeout(int seconds);
    
private:
    class Impl;
    std::unique_ptr<Impl> impl_;
};

}

#endif
