#include "native_database.h"
#include "native_github_importer.h"
#include "native_zip_importer.h"
#include <android/log.h>
#include <sstream>

#define LOG_TAG "NativeDatabase"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace gamefluxer {

std::string NativeDatabase::serializeImportResult(const ImportResult& result) {
    std::ostringstream json;
    json << "{";
    json << "\"success\":" << (result.success ? "true" : "false") << ",";
    json << "\"totalGames\":" << result.totalGames << ",";
    json << "\"error\":\"" << result.error << "\",";
    
    json << "\"platforms\":[";
    bool firstPlatform = true;
    for (const auto& platform : result.config.platforms) {
        if (!firstPlatform) json << ",";
        firstPlatform = false;
        
        json << "{";
        json << "\"name\":\"" << platform.name << "\",";
        json << "\"databasePath\":\"" << platform.databasePath << "\"";
        json << "}";
    }
    json << "],";
    
    json << "\"games\":{";
    bool firstGamePlatform = true;
    for (const auto& [platformName, games] : result.games) {
        if (!firstGamePlatform) json << ",";
        firstGamePlatform = false;
        
        json << "\"" << platformName << "\":[";
        bool firstGame = true;
        for (const auto& game : games) {
            if (!firstGame) json << ",";
            firstGame = false;
            
            json << "{";
            json << "\"id\":\"" << game.id << "\",";
            json << "\"name\":\"" << game.name << "\",";
            json << "\"description\":\"" << game.description << "\",";
            json << "\"version\":\"" << game.version << "\",";
            json << "\"size\":\"" << game.size << "\",";
            json << "\"rating\":" << game.rating << ",";
            json << "\"developer\":\"" << game.developer << "\",";
            json << "\"category\":\"" << game.category << "\",";
            json << "\"platform\":\"" << game.platform << "\",";
            json << "\"iconUrl\":\"" << game.iconUrl << "\",";
            json << "\"bannerUrl\":\"" << game.bannerUrl << "\",";
            json << "\"screenshots\":\"" << game.screenshots << "\",";
            json << "\"downloadUrl\":\"" << game.downloadUrl << "\",";
            json << "\"releaseDate\":\"" << game.releaseDate << "\"";
            json << "}";
        }
        json << "]";
    }
    json << "}";
    
    json << "}";
    return json.str();
}

jstring NativeDatabase::importFromGitHub(JNIEnv* env, jobject thiz, jstring repoUrl) {
    const char* repoUrlChars = env->GetStringUTFChars(repoUrl, nullptr);
    std::string repoUrlStr(repoUrlChars);
    env->ReleaseStringUTFChars(repoUrl, repoUrlChars);
    
    LOGD("importFromGitHub called with URL: %s", repoUrlStr.c_str());
    
    GitHubImporter importer(repoUrlStr);
    ImportResult result = importer.import();
    
    std::string resultJson = serializeImportResult(result);
    LOGD("Import result: %s", resultJson.c_str());
    
    return env->NewStringUTF(resultJson.c_str());
}

jstring NativeDatabase::importFromZip(JNIEnv* env, jobject thiz, jstring zipPath) {
    const char* zipPathChars = env->GetStringUTFChars(zipPath, nullptr);
    std::string zipPathStr(zipPathChars);
    env->ReleaseStringUTFChars(zipPath, zipPathChars);
    
    LOGD("importFromZip called with path: %s", zipPathStr.c_str());
    
    ZipImporter importer(zipPathStr);
    ImportResult result = importer.import();
    
    std::string resultJson = serializeImportResult(result);
    LOGD("Import result: %s", resultJson.c_str());
    
    return env->NewStringUTF(resultJson.c_str());
}

}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gamestore_app_util_NativeImporter_importFromGitHubNative(
    JNIEnv* env, jobject thiz, jstring repoUrl) {
    return gamefluxer::NativeDatabase::importFromGitHub(env, thiz, repoUrl);
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_gamestore_app_util_NativeImporter_importFromZipNative(
    JNIEnv* env, jobject thiz, jstring zipPath) {
    return gamefluxer::NativeDatabase::importFromZip(env, thiz, zipPath);
}
