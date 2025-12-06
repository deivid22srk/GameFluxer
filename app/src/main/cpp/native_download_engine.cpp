#include "native_download_engine.h"
#include <android/log.h>
#include <chrono>
#include <algorithm>

#define LOG_TAG "NativeDownloadEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

NativeDownloadEngine::NativeDownloadEngine() {
    LOGD("NativeDownloadEngine initialized");
}

NativeDownloadEngine::~NativeDownloadEngine() {
    LOGD("NativeDownloadEngine destroying");
    
    std::lock_guard<std::mutex> lock(tasksMutex_);
    for (auto& [id, task] : activeTasks_) {
        task->shouldStop.store(true);
        if (task->thread && task->thread->joinable()) {
            task->thread->join();
        }
    }
    activeTasks_.clear();
}

int NativeDownloadEngine::startDownload(const DownloadConfig& config,
                                         ProgressCallback progressCallback,
                                         CompleteCallback completeCallback,
                                         ErrorCallback errorCallback) {
    int downloadId = nextDownloadId_++;
    
    LOGD("Starting download ID %d: %s", downloadId, config.url.c_str());
    
    auto task = std::make_unique<DownloadTask>();
    task->id = downloadId;
    task->config = config;
    task->progress = std::make_shared<DownloadProgress>();
    task->progressCallback = progressCallback;
    task->completeCallback = completeCallback;
    task->errorCallback = errorCallback;
    task->shouldStop.store(false);
    task->isPaused.store(false);
    
    task->progress->state = DownloadState::DOWNLOADING;
    
    task->thread = std::make_unique<std::thread>(
        &NativeDownloadEngine::downloadThreadFunc, this, downloadId
    );
    
    {
        std::lock_guard<std::mutex> lock(tasksMutex_);
        activeTasks_[downloadId] = std::move(task);
    }
    
    return downloadId;
}

void NativeDownloadEngine::pauseDownload(int downloadId) {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    
    auto it = activeTasks_.find(downloadId);
    if (it != activeTasks_.end()) {
        LOGD("Pausing download ID %d", downloadId);
        it->second->isPaused.store(true);
        it->second->shouldStop.store(true);
        it->second->progress->state = DownloadState::PAUSED;
    }
}

void NativeDownloadEngine::resumeDownload(int downloadId) {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    
    auto it = activeTasks_.find(downloadId);
    if (it != activeTasks_.end()) {
        LOGD("Resuming download ID %d", downloadId);
        it->second->isPaused.store(false);
        it->second->shouldStop.store(false);
        it->second->progress->state = DownloadState::DOWNLOADING;
    }
}

void NativeDownloadEngine::cancelDownload(int downloadId) {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    
    auto it = activeTasks_.find(downloadId);
    if (it != activeTasks_.end()) {
        LOGD("Cancelling download ID %d", downloadId);
        it->second->shouldStop.store(true);
        it->second->progress->state = DownloadState::CANCELLED;
    }
}

DownloadProgress NativeDownloadEngine::getProgress(int downloadId) {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    
    auto it = activeTasks_.find(downloadId);
    if (it != activeTasks_.end() && it->second->progress) {
        return *(it->second->progress);
    }
    
    return DownloadProgress{};
}

void NativeDownloadEngine::downloadThreadFunc(int downloadId) {
    DownloadTask* task = nullptr;
    
    {
        std::lock_guard<std::mutex> lock(tasksMutex_);
        auto it = activeTasks_.find(downloadId);
        if (it == activeTasks_.end()) {
            return;
        }
        task = it->second.get();
    }
    
    try {
        performDownload(*task);
    } catch (const std::exception& e) {
        LOGE("Download exception for ID %d: %s", downloadId, e.what());
        task->progress->state = DownloadState::FAILED;
        task->progress->error = e.what();
        if (task->errorCallback) {
            task->errorCallback(e.what());
        }
    }
    
    cleanupTask(downloadId);
}

void NativeDownloadEngine::performDownload(DownloadTask& task) {
    LOGD("Performing download: %s -> %s", 
         task.config.url.c_str(), task.config.outputPath.c_str());
    
    NativeHttpClient httpClient;
    
    auto wrappedProgressCallback = [&](long long downloaded, long long total, long long speed) {
        task.progress->bytesDownloaded.store(downloaded);
        task.progress->totalBytes.store(total);
        task.progress->speed.store(speed);
        
        if (total > 0) {
            int progress = static_cast<int>((downloaded * 100) / total);
            task.progress->progress.store(progress);
        }
        
        if (task.progressCallback) {
            task.progressCallback(downloaded, total, speed);
        }
    };
    
    bool success = httpClient.downloadFile(
        task.config.url,
        task.config.outputPath,
        task.config.existingBytes,
        task.config.customHeaders,
        wrappedProgressCallback,
        task.shouldStop
    );
    
    if (success && !task.shouldStop.load()) {
        LOGD("Download completed successfully");
        task.progress->state = DownloadState::COMPLETED;
        if (task.completeCallback) {
            task.completeCallback();
        }
    } else if (task.isPaused.load()) {
        LOGD("Download paused");
        task.progress->state = DownloadState::PAUSED;
    } else if (task.shouldStop.load()) {
        LOGD("Download cancelled");
        task.progress->state = DownloadState::CANCELLED;
    } else {
        LOGE("Download failed: %s", httpClient.getError().c_str());
        task.progress->state = DownloadState::FAILED;
        task.progress->error = httpClient.getError();
        if (task.errorCallback) {
            task.errorCallback(httpClient.getError());
        }
    }
}

void NativeDownloadEngine::cleanupTask(int downloadId) {
    std::lock_guard<std::mutex> lock(tasksMutex_);
    
    auto it = activeTasks_.find(downloadId);
    if (it != activeTasks_.end()) {
        if (it->second->thread && it->second->thread->joinable()) {
            it->second->thread->detach();
        }
    }
}

}
