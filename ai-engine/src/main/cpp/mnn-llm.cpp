//
//  mnn-llm.cpp — MNN LLM JNI bridge for Cesia
//
//  JNI 对应: com.cesia.input.engine.ai.MNNEngine
//

#include <jni.h>
#include <string>
#include <memory>
#include <sstream>
#include <android/log.h>

#include "MNN/llm/llm.hpp"

#define LOG_TAG "MNN-LLM"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

using namespace MNN::Transformer;

static Llm* g_llm = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeInit(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring configPath
) {
    if (g_llm != nullptr) {
        LOGI("LLM already loaded, destroying old instance first");
        Llm::destroy(g_llm);
        g_llm = nullptr;
    }

    const char* path = env->GetStringUTFChars(configPath, nullptr);
    std::string configStr(path);
    env->ReleaseStringUTFChars(configPath, path);

    LOGI("Loading MNN LLM from: %s", configStr.c_str());

    try {
        g_llm = Llm::createLLM(configStr);
        if (g_llm == nullptr) {
            LOGE("Llm::createLLM returned null");
            return JNI_FALSE;
        }

        // 基础配置
        g_llm->set_config("{\"async\":true}");
        // 关闭 thinking
        g_llm->set_config("{\"jinja\":{\"context\":{\"enable_thinking\":false}}}");
        // 生成参数：低温 + 重复惩罚，与云端对齐
        g_llm->set_config("{\"sampler\":{\"temperature\":0.1,\"repetition_penalty\":1.2,\"top_k\":20,\"top_p\":0.9}}");

        bool loaded = g_llm->load();
        if (!loaded) {
            LOGE("llm->load() failed");
            Llm::destroy(g_llm);
            g_llm = nullptr;
            return JNI_FALSE;
        }

        LOGI("MNN LLM loaded successfully");
        return JNI_TRUE;

    } catch (const std::exception& e) {
        LOGE("Exception during init: %s", e.what());
        if (g_llm) {
            Llm::destroy(g_llm);
            g_llm = nullptr;
        }
        return JNI_FALSE;
    }
}

/**
 * 同步生成文本
 * promptStr 可能是：
 *   格式A（极简）："只输出润色结果，不解释不重复：xxx\n"
 *   格式B（Chat）："system内容\n\n输入：user内容\n\n输出："
 * end_with="。\n" 让模型在句号后停止，避免无限生成
 */
JNIEXPORT jstring JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeGenerate(
    JNIEnv* env,
    jobject /*thiz*/,
    jstring prompt,
    jint maxTokens
) {
    if (g_llm == nullptr) {
        LOGE("nativeGenerate: LLM not initialized");
        return env->NewStringUTF("");
    }

    const char* promptC = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptC);
    env->ReleaseStringUTFChars(prompt, promptC);

    try {
        std::ostringstream outputStream;

        // 检查是否是 Chat 格式（包含 "\n\n输入：" 标记）
        std::string::size_type splitPos = promptStr.find("\n\n输入：");
        if (splitPos != std::string::npos) {
            // Chat 格式：拆成 system + user
            std::string systemPart = promptStr.substr(0, splitPos);
            std::string::size_type inputStart = splitPos + 8; // "\n\n输入：" 长度
            std::string::size_type inputEnd = promptStr.find("\n\n输出：", inputStart);
            std::string userPart = (inputEnd != std::string::npos)
                ? promptStr.substr(inputStart, inputEnd - inputStart)
                : promptStr.substr(inputStart);

            ChatMessages messages;
            messages.emplace_back("system", systemPart);
            messages.emplace_back("user", userPart);
            g_llm->response(messages, &outputStream, "。\n", maxTokens);
        } else {
            // 极简格式：直接传入，用换行作为停止标记
            g_llm->response(promptStr, &outputStream, "\n", maxTokens);
        }

        auto context = g_llm->getContext();
        if (context->status == LlmStatus::INTERNAL_ERROR) {
            LOGE("nativeGenerate: LLM internal error");
            return env->NewStringUTF("");
        }

        std::string result = outputStream.str();
        LOGI("Generate complete: %d chars, %d tokens",
             (int)result.size(), (int)context->gen_seq_len);

        // 清理输出
        // 1. 去除模型可能带出的前缀
        if (result.find("输出：") != std::string::npos) {
            std::string::size_type outPos = result.rfind("输出：");
            if (outPos != std::string::npos) {
                result = result.substr(outPos + 9);
            }
        }
        // 2. 去除润色指令中重复的部分（如果模型把 prompt 也输出了）
        std::string::size_type repeatPos = result.find("只输出");
        if (repeatPos != std::string::npos && repeatPos > 0) {
            // 模型把 prompt 指令当作输出截断了
            // 保留 repeatPos 之前的内容
        }
        // 3. trim 首尾空白
        std::string::size_type start = result.find_first_not_of(" \t\n\r");
        if (start != std::string::npos) result = result.substr(start);
        else result = "";
        std::string::size_type end = result.find_last_not_of(" \t\n\r");
        if (end != std::string::npos) result = result.substr(0, end + 1);

        // 4. 如果结果比原文长很多（>3倍），截断到合理长度——防止模型自由发挥
        // （调用方传入的 text 长度存于 prompt 中，无法直接比较，此处简单截断到 1000 字符）
        if ((int)result.size() > 1000) {
            result = result.substr(0, 1000);
            // 截到最近一个句号
            std::string::size_type lastPeriod = result.rfind("。");
            if (lastPeriod != std::string::npos && lastPeriod > 100) {
                result = result.substr(0, lastPeriod + 1);
            }
        }

        return env->NewStringUTF(result.c_str());

    } catch (const std::exception& e) {
        LOGE("Exception during generate: %s", e.what());
        return env->NewStringUTF("");
    }
}

JNIEXPORT void JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeGenerateStreaming(
    JNIEnv* env,
    jobject thiz,
    jstring prompt,
    jint maxTokens,
    jobject callback
) {
    if (g_llm == nullptr || callback == nullptr) {
        LOGE("nativeGenerateStreaming: LLM or callback is null");
        return;
    }

    const char* promptC = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptC);
    env->ReleaseStringUTFChars(prompt, promptC);

    jclass cbClass = env->GetObjectClass(callback);
    jmethodID onToken = env->GetMethodID(cbClass, "onToken", "(Ljava/lang/String;)V");
    jmethodID onComplete = env->GetMethodID(cbClass, "onComplete", "()V");
    jmethodID onError = env->GetMethodID(cbClass, "onError", "(Ljava/lang/String;)V");

    if (!onToken || !onComplete || !onError) {
        LOGE("nativeGenerateStreaming: callback methods not found");
        return;
    }

    try {
        std::ostringstream outputStream;
        std::string::size_type splitPos = promptStr.find("\n\n输入：");
        if (splitPos != std::string::npos) {
            std::string systemPart = promptStr.substr(0, splitPos);
            std::string::size_type inputStart = splitPos + 8;
            std::string::size_type inputEnd = promptStr.find("\n\n输出：", inputStart);
            std::string userPart = (inputEnd != std::string::npos)
                ? promptStr.substr(inputStart, inputEnd - inputStart)
                : promptStr.substr(inputStart);

            ChatMessages messages;
            messages.emplace_back("system", systemPart);
            messages.emplace_back("user", userPart);
            g_llm->response(messages, &outputStream, "。\n", maxTokens);
        } else {
            g_llm->response(promptStr, &outputStream, "\n", maxTokens);
        }

        auto context = g_llm->getContext();
        if (context->status == LlmStatus::INTERNAL_ERROR) {
            std::string errMsg = "LLM internal error";
            env->CallVoidMethod(callback, onError, env->NewStringUTF(errMsg.c_str()));
        } else {
            std::string result = outputStream.str();
            if (!result.empty()) {
                jstring jResult = env->NewStringUTF(result.c_str());
                env->CallVoidMethod(callback, onToken, jResult);
                env->DeleteLocalRef(jResult);
            }
            env->CallVoidMethod(callback, onComplete);
        }

    } catch (const std::exception& e) {
        std::string errMsg = std::string("Exception: ") + e.what();
        env->CallVoidMethod(callback, onError, env->NewStringUTF(errMsg.c_str()));
    }
}

JNIEXPORT void JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeFree(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    if (g_llm != nullptr) {
        LOGI("Destroying MNN LLM");
        Llm::destroy(g_llm);
        g_llm = nullptr;
    }
}

JNIEXPORT jboolean JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeIsLoaded(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return (g_llm != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeGetLog(
    JNIEnv* env,
    jobject /*thiz*/
) {
    if (g_llm == nullptr) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(g_llm->getLog().c_str());
}

JNIEXPORT void JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeReset(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    if (g_llm != nullptr) {
        LOGI("Resetting LLM context");
        g_llm->reset();
    }
}

} // extern "C"
