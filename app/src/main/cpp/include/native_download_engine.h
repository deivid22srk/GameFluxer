#ifndef GAMEFLUXER_NATIVE_DOWNLOAD_ENGINE_H
#define GAMEFLUXER_NATIVE_DOWNLOAD_ENGINE_H

#include "native_download_types.h"
#include "native_http_client.h"
#include "native_file_writer.h"
#include <memory>
#include <thread>
#include <mutex>
#include <unordered_map>
#include <atomic>

namespace gamefluxer {

class NativeDownloadEngine {
public:
    NativeDownloadEngine();
    ~NativeDownloadEngine();
    
    int startDownload(const DownloadConfig& config,
                      ProgressCallback progressCallback,
                      CompleteCallback completeCallback,
                      ErrorCallback errorCallback);
    
    void pauseDownload(int downloadId);
    void resumeDownload(int downloadId);
    void cancelDownload(int downloadId);
    
    DownloadProgress getProgress(int downloadId);
    
private:
    struct DownloadTask {
        int id;
        DownloadConfig config;
        std::shared_ptr<DownloadProgress> progress;
        std::unique_ptr<std::thread> thread;
        std::atomic<bool> shouldStop{false};
        std::atomic<bool> isPaused{false};
        ProgressCallback progressCallback;
        CompleteCallback completeCallback;
        ErrorCallback errorCallback;
    };
    
    std::unordered_map<int, std::unique_ptr<DownloadTask>> activeTasks_;
    std::mutex tasksMutex_;
    std::atomic<int> nextDownloadId_{1};
    
    void downloadThreadFunc(int downloadId);
    void performDownload(DownloadTask& task);
    void cleanupTask(int downloadId);
};

}

#endif
