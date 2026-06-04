#include <jni.h>
#include <string>
#include <fstream>
#include <android/log.h>

#define LOG_TAG "CesiaWhisper"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

#ifdef HAS_WHISPER
#include "whisper.h"

struct WhisperHandle {
    whisper_context* ctx = nullptr;
};

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeInit(
    JNIEnv* env, jobject /* this */, jstring modelPath, jboolean useGpu) {

    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    if (!path) {
        LOGE("nativeInit: modelPath is null");
        return JNI_FALSE;
    }

    LOGI("nativeInit: loading model from '%s', gpu=%d", path, useGpu);

    whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = useGpu;
    // Disable flash_attn on Android to avoid GPU compatibility issues
    cparams.flash_attn = false;

    auto fin = std::ifstream(path, std::ios::binary);
    if (!fin.is_open()) {
        LOGE("nativeInit: failed to open model file: %s", path);
        env->ReleaseStringUTFChars(modelPath, path);
        return JNI_FALSE;
    }

    // Check file size
    fin.seekg(0, std::ios::end);
    auto fileSize = fin.tellg();
    fin.seekg(0, std::ios::beg);
    LOGI("nativeInit: model file size = %ld bytes", (long)fileSize);

    // Check magic number
    uint32_t magic = 0;
    fin.read((char*)&magic, sizeof(magic));
    LOGI("nativeInit: magic = 0x%08x", magic);
    fin.seekg(0, std::ios::beg);

    whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);
    env->ReleaseStringUTFChars(modelPath, path);

    if (!ctx) {
        LOGE("nativeInit: whisper_init_from_file_with_params returned nullptr");
        return JNI_FALSE;
    }

    LOGI("nativeInit: Whisper model loaded successfully");
    // TODO: store ctx
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeTranscribe(
        JNIEnv* env, jobject /* this */, jfloatArray audioData) {

    // TODO: implement — get stored context, call whisper_full, return text
    LOGI("transcribe called — not yet implemented");
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeFree(
        JNIEnv* /* env */, jobject /* this */) {
    // TODO: free stored context
    LOGI("whisper free called");
}

} // extern "C"

#else // !HAS_WHISPER — 空壳占位

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeInit(
        JNIEnv* /* env */, jobject, jstring, jboolean) {
    LOGE("whisper.cpp not compiled in — stub");
    return JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeTranscribe(
        JNIEnv* env, jobject, jfloatArray) {
    return env->NewStringUTF("");
}

JNIEXPORT void JNICALL
Java_com_cesia_input_engine_ai_WhisperEngine_nativeFree(
        JNIEnv*, jobject) {}

} // extern "C"
#endif // HAS_WHISPER
