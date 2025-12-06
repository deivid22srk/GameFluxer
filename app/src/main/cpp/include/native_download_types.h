#ifndef GAMEFLUXER_NATIVE_DOWNLOAD_TYPES_H
#define GAMEFLUXER_NATIVE_DOWNLOAD_TYPES_H

#include <string>
#include <map>
#include <atomic>
#include <functional>

namespace gamefluxer {

enum class DownloadState {
    IDLE = 0,
    DOWNLOADING = 1,
    PAUSED = 2,
    COMPLETED = 3,
    FAILED = 4,
    CANCELLED = 5
};

struct DownloadProgress {
    std::atomic<long long> bytesDownloaded{0};
    std::atomic<long long> totalBytes{0};
    std::atomic<long long> speed{0};
    std::atomic<int> progress{0};
    DownloadState state{DownloadState::IDLE};
    std::string error;
};

struct DownloadConfig {
    std::string url;
    std::string outputPath;
    long long existingBytes{0};
    std::map<std::string, std::string> customHeaders;
    int bufferSize{65536};
    int maxRetries{3};
    int connectionTimeout{15};
    bool enableChunking{false};
    int numChunks{4};
};

using ProgressCallback = std::function<void(long long, long long, long long)>;
using CompleteCallback = std::function<void()>;
using ErrorCallback = std::function<void(const std::string&)>;

}

#endif
