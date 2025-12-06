#include "native_downloader.h"
#include <android/log.h>
#include <sstream>

#define LOG_TAG "NativeDownloader"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

NativeDownloader& NativeDownloader::getInstance() {
    static NativeDownloader instance;
    return instance;
}

NativeDownloader::NativeDownloader() : jvm_(nullptr) {
    LOGD("NativeDownloader created");
}

NativeDownloader::~NativeDownloader() {
    LOGD("NativeDownloader destroyed");
    
    std::lock_guard<std::mutex> lock(callbacksMutex_);
    if (jvm_) {
        JNIEnv* env = getEnv();
        if (env) {
            for (auto& [id, callback] : javaCallbacks_) {
                env->DeleteGlobalRef(callback);
            }
        }
    }
    javaCallbacks_.clear();
}

JNIEnv* NativeDownloader::getEnv() {
    if (!jvm_) return nullptr;
    
    JNIEnv* env = nullptr;
    int getEnvStat = jvm_->GetEnv((void**)&env, JNI_VERSION_1_6);
    
    if (getEnvStat == JNI_EDETACHED) {
        if (jvm_->AttachCurrentThread(&env, nullptr) != 0) {
            LOGE("Failed to attach thread");
            return nullptr;
        }
    } else if (getEnvStat == JNI_EVERSION) {
        LOGE("GetEnv: version not supported");
        return nullptr;
    }
    
    return env;
}

void NativeDownloader::invokeProgressCallback(JNIEnv* env, jobject callback,
                                               long long downloaded, long long total, long long speed) {
    if (!env || !callback) return;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(callbackClass, "onProgress", "(JJJ)V");
    
    if (method) {
        env->CallVoidMethod(callback, method, (jlong)downloaded, (jlong)total, (jlong)speed);
    }
    
    env->DeleteLocalRef(callbackClass);
}

void NativeDownloader::invokeCompleteCallback(JNIEnv* env, jobject callback) {
    if (!env || !callback) return;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(callbackClass, "onComplete", "()V");
    
    if (method) {
        env->CallVoidMethod(callback, method);
    }
    
    env->DeleteLocalRef(callbackClass);
}

void NativeDownloader::invokeErrorCallback(JNIEnv* env, jobject callback, const std::string& error) {
    if (!env || !callback) return;
    
    jclass callbackClass = env->GetObjectClass(callback);
    jmethodID method = env->GetMethodID(callbackClass, "onError", "(Ljava/lang/String;)V");
    
    if (method) {
        jstring jerror = env->NewStringUTF(error.c_str());
        env->CallVoidMethod(callback, method, jerror);
        env->DeleteLocalRef(jerror);
    }
    
    env->DeleteLocalRef(callbackClass);
}

int NativeDownloader::startDownload(JNIEnv* env, jobject javaCallback,
                                     const std::string& url,
                                     const std::string& outputPath,
                                     long long existingBytes,
                                     const std::map<std::string, std::string>& customHeaders) {
    if (!jvm_) {
        env->GetJavaVM(&jvm_);
    }
    
    jobject globalCallback = env->NewGlobalRef(javaCallback);
    
    DownloadConfig config;
    config.url = url;
    config.outputPath = outputPath;
    config.existingBytes = existingBytes;
    config.customHeaders = customHeaders;
    
    int downloadId = engine_.startDownload(
        config,
        [this, globalCallback](long long downloaded, long long total, long long speed) {
            JNIEnv* env = getEnv();
            if (env) {
                invokeProgressCallback(env, globalCallback, downloaded, total, speed);
            }
        },
        [this, globalCallback]() {
            JNIEnv* env = getEnv();
            if (env) {
                invokeCompleteCallback(env, globalCallback);
            }
        },
        [this, globalCallback](const std::string& error) {
            JNIEnv* env = getEnv();
            if (env) {
                invokeErrorCallback(env, globalCallback, error);
            }
        }
    );
    
    {
        std::lock_guard<std::mutex> lock(callbacksMutex_);
        javaCallbacks_[downloadId] = globalCallback;
    }
    
    return downloadId;
}

void NativeDownloader::pauseDownload(int downloadId) {
    engine_.pauseDownload(downloadId);
}

void NativeDownloader::resumeDownload(int downloadId) {
    engine_.resumeDownload(downloadId);
}

void NativeDownloader::cancelDownload(int downloadId) {
    engine_.cancelDownload(downloadId);
    
    std::lock_guard<std::mutex> lock(callbacksMutex_);
    auto it = javaCallbacks_.find(downloadId);
    if (it != javaCallbacks_.end()) {
        JNIEnv* env = getEnv();
        if (env) {
            env->DeleteGlobalRef(it->second);
        }
        javaCallbacks_.erase(it);
    }
}

std::string NativeDownloader::getProgressJson(int downloadId) {
    DownloadProgress progress = engine_.getProgress(downloadId);
    
    std::ostringstream json;
    json << "{";
    json << "\"downloadId\":" << downloadId << ",";
    json << "\"bytesDownloaded\":" << progress.bytesDownloaded.load() << ",";
    json << "\"totalBytes\":" << progress.totalBytes.load() << ",";
    json << "\"speed\":" << progress.speed.load() << ",";
    json << "\"progress\":" << progress.progress.load() << ",";
    json << "\"state\":" << static_cast<int>(progress.state) << ",";
    json << "\"error\":\"" << progress.error << "\"";
    json << "}";
    
    return json.str();
}

}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_gamestore_app_util_NativeDownloader_startDownloadNative(
    JNIEnv* env, jobject thiz, jstring jurl, jstring joutputPath,
    jlong existingBytes, jobjectArray jheaders, jobject jcallback) {
    
    const char* urlChars = env->GetStringUTFChars(jurl, nullptr);
    const char* outputPathChars = env->GetStringUTFChars(joutputPath, nullptr);
    
    std::string url(urlChars);
    std::string outputPath(outputPathChars);
    
    env->ReleaseStringUTFChars(jurl, urlChars);
    env->ReleaseStringUTFChars(joutputPath, outputPathChars);
    
    std::map<std::string, std::string> customHeaders;
    
    if (jheaders) {
        jsize headerCount = env->GetArrayLength(jheaders);
        for (jsize i = 0; i < headerCount; i++) {
            jstring jheader = (jstring)env->GetObjectArrayElement(jheaders, i);
            const char* headerChars = env->GetStringUTFChars(jheader, nullptr);
            std::string header(headerChars);
            env->ReleaseStringUTFChars(jheader, headerChars);
            env->DeleteLocalRef(jheader);
            
            size_t colonPos = header.find(':');
            if (colonPos != std::string::npos) {
                std::string key = header.substr(0, colonPos);
                std::string value = header.substr(colonPos + 1);
                
                value.erase(0, value.find_first_not_of(" \t"));
                value.erase(value.find_last_not_of(" \t") + 1);
                
                customHeaders[key] = value;
            }
        }
    }
    
    return gamefluxer::NativeDownloader::getInstance().startDownload(
        env, jcallback, url, outputPath, existingBytes, customHeaders
    );
}

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_pauseDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId) {
    gamefluxer::NativeDownloader::getInstance().pauseDownload(downloadId);
}

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_resumeDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId) {
    gamefluxer::NativeDownloader::getInstance().resumeDownload(downloadId);
}

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_cancelDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId) {
    gamefluxer::NativeDownloader::getInstance().cancelDownload(downloadId);
}

JNIEXPORT jstring JNICALL
Java_com_gamestore_app_util_NativeDownloader_getProgressJsonNative(
    JNIEnv* env, jobject thiz, jint downloadId) {
    std::string json = gamefluxer::NativeDownloader::getInstance().getProgressJson(downloadId);
    return env->NewStringUTF(json.c_str());
}

}
