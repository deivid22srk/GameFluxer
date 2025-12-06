#ifndef GAMEFLUXER_NATIVE_DOWNLOADER_H
#define GAMEFLUXER_NATIVE_DOWNLOADER_H

#include <jni.h>
#include "native_download_types.h"
#include "native_download_engine.h"
#include <memory>
#include <map>

namespace gamefluxer {

class NativeDownloader {
public:
    static NativeDownloader& getInstance();
    
    int startDownload(JNIEnv* env, jobject javaCallback,
                      const std::string& url,
                      const std::string& outputPath,
                      long long existingBytes,
                      const std::map<std::string, std::string>& customHeaders);
    
    void pauseDownload(int downloadId);
    void resumeDownload(int downloadId);
    void cancelDownload(int downloadId);
    
    std::string getProgressJson(int downloadId);
    
private:
    NativeDownloader();
    ~NativeDownloader();
    
    NativeDownloadEngine engine_;
    JavaVM* jvm_;
    std::map<int, jobject> javaCallbacks_;
    std::mutex callbacksMutex_;
    
    void invokeProgressCallback(JNIEnv* env, jobject callback, 
                                long long downloaded, long long total, long long speed);
    void invokeCompleteCallback(JNIEnv* env, jobject callback);
    void invokeErrorCallback(JNIEnv* env, jobject callback, const std::string& error);
    
    JNIEnv* getEnv();
};

}

extern "C" {

JNIEXPORT jint JNICALL
Java_com_gamestore_app_util_NativeDownloader_startDownloadNative(
    JNIEnv* env, jobject thiz, jstring jurl, jstring joutputPath, 
    jlong existingBytes, jobjectArray jheaders, jobject jcallback);

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_pauseDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId);

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_resumeDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId);

JNIEXPORT void JNICALL
Java_com_gamestore_app_util_NativeDownloader_cancelDownloadNative(
    JNIEnv* env, jobject thiz, jint downloadId);

JNIEXPORT jstring JNICALL
Java_com_gamestore_app_util_NativeDownloader_getProgressJsonNative(
    JNIEnv* env, jobject thiz, jint downloadId);

}

#endif
