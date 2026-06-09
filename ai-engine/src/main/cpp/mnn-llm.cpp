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

// 全局 LLM 实例（单实例，线程安全由 MNN 内部保证）
static Llm* g_llm = nullptr;

extern "C" {

/**
 * 初始化 MNN LLM 模型
 */
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
        g_llm->set_config(R"({"async":true})");
        // 关闭 thinking（Qwen3.5 默认开启，润色任务不需要推理链）
        g_llm->set_config(R"({"jinja":{"context":{"enable_thinking":false}}})");
        // 设置生成参数：temperature + 重复惩罚（与云端对齐）
        g_llm->set_config("{\"sampler\":{\"temperature\":0.3,\"repetition_penalty\":1.15,\"top_k\":40,\"top_p\":0.95}}");

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
 * 同步生成文本（阻塞直到完成）
 * 使用与云端相同的 Chat 格式：system + user 消息
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

        // 解析 prompt：前半部分是 system instruction，后半部分是用户输入
        // 格式："{system_prompt}\n\n输入：{text}\n\n输出："
        // 拆分成 system + user 两条消息，用 MNN 的 chat template 处理
        std::string::size_type splitPos = promptStr.find("\n\n输入：");
        if (splitPos != std::string::npos) {
            std::string systemPart = promptStr.substr(0, splitPos);
            // 提取用户输入（"输入：xxx\n\n输出：" 中间的部分）
            std::string::size_type inputStart = splitPos + 8; // "\n\n输入：" 长度
            std::string::size_type inputEnd = promptStr.find("\n\n输出：", inputStart);
            std::string userPart = (inputEnd != std::string::npos)
                ? promptStr.substr(inputStart, inputEnd - inputStart)
                : promptStr.substr(inputStart);

            // 使用 Chat 格式调用（与云端一致）
            ChatMessages messages;
            messages.emplace_back("system", systemPart);
            messages.emplace_back("user", userPart);

            // end_with="\n\n" 防止模型无限生成
            g_llm->response(messages, &outputStream, "\n\n", maxTokens);
        } else {
            // 没有 split 标记，直接当普通文本
            g_llm->response(promptStr, &outputStream, "\n\n", maxTokens);
        }

        auto context = g_llm->getContext();
        if (context->status == LlmStatus::INTERNAL_ERROR) {
            LOGE("nativeGenerate: LLM internal error");
            return env->NewStringUTF("");
        }

        std::string result = outputStream.str();
        LOGI("Generate complete: %d chars, %d tokens",
             (int)result.size(), (int)context->gen_seq_len);

        // 清理输出：去除 "输出：" 前缀和多余空白（模型可能带出的）
        std::string::size_type outPos = result.find("输出：");
        if (outPos != std::string::npos) {
            result = result.substr(outPos + 9); // "输出：" 长度
        }
        // trim
        std::string::size_type start = result.find_first_not_of(" \t\n\r");
        if (start != std::string::npos) result = result.substr(start);
        std::string::size_type end = result.find_last_not_of(" \t\n\r");
        if (end != std::string::npos) result = result.substr(0, end + 1);

        return env->NewStringUTF(result.c_str());

    } catch (const std::exception& e) {
        LOGE("Exception during generate: %s", e.what());
        return env->NewStringUTF("");
    }
}

/**
 * 流式生成文本（回调方式）
 */
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

        // 同样用 Chat 格式
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
            g_llm->response(messages, &outputStream, "\n\n", maxTokens);
        } else {
            g_llm->response(promptStr, &outputStream, "\n\n", maxTokens);
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

/**
 * 释放 LLM 资源
 */
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

/**
 * 检查 LLM 是否已加载
 */
JNIEXPORT jboolean JNICALL
Java_com_cesia_input_engine_ai_MNNEngine_nativeIsLoaded(
    JNIEnv* /*env*/,
    jobject /*thiz*/
) {
    return (g_llm != nullptr) ? JNI_TRUE : JNI_FALSE;
}

/**
 * 获取 LLM 日志缓冲区内容
 */
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

/**
 * 重置 LLM 对话历史
 */
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
