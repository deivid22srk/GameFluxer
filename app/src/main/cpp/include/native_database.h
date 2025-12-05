#ifndef GAMEFLUXER_NATIVE_DATABASE_H
#define GAMEFLUXER_NATIVE_DATABASE_H

#include "native_types.h"
#include <jni.h>

namespace gamefluxer {

class NativeDatabase {
public:
    static jstring importFromGitHub(JNIEnv* env, jobject thiz, jstring repoUrl);
    static jstring importFromZip(JNIEnv* env, jobject thiz, jstring zipPath);
    
private:
    static std::string serializeImportResult(const ImportResult& result);
    static jobject createGameObject(JNIEnv* env, const Game& game);
};

}

#endif
