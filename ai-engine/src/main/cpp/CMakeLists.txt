cmake_minimum_required(VERSION 3.22.1)
project(ai-engine VERSION 1.0)
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================================
# MNN LLM 引擎 - 使用 MNN 3.5.0 官方 .so 动态库
# ============================================================
set(MNN_DIR "${CMAKE_CURRENT_SOURCE_DIR}/mnn")
set(MNN_LIBS_DIR "${MNN_DIR}/libs")
set(MNN_INCLUDE_DIR "${MNN_DIR}/include")

# MNN 3.5.0 官方 Android 包下载 URL
set(MNN_350_URL "https://github.com/alibaba/MNN/releases/download/3.5.0/mnn_3.5.0_android_armv7_armv8_cpu_opencl_vulkan.zip")
set(MNN_350_ZIP "${MNN_DIR}/mnn_3.5.0_android.zip")
set(MNN_350_EXTRACTED "${MNN_DIR}/mnn_3.5.0_extracted")

# 如果 .so 文件不存在，下载并解压 MNN 3.5.0 官方包
if (NOT EXISTS "${MNN_LIBS_DIR}/libMNN.so" OR NOT EXISTS "${MNN_LIBS_DIR}/libllm.so")
    message(STATUS "MNN 3.5.0 .so libraries not found, downloading from official release...")

    # 下载 zip
    if (NOT EXISTS "${MNN_350_ZIP}")
        file(DOWNLOAD "${MNN_350_URL}" "${MNN_350_ZIP}"
             SHOW_PROGRESS STATUS MNN_DL)
        list(GET MNN_DL 0 MNN_DL_CODE)
        if (NOT MNN_DL_CODE EQUAL 0)
            list(GET MNN_DL 1 MNN_DL_ERR)
            message(FATAL_ERROR "Failed to download MNN 3.5.0: ${MNN_DL_ERR}")
        endif()
    endif()

    # 解压
    file(MAKE_DIRECTORY "${MNN_350_EXTRACTED}")
    execute_process(
        COMMAND ${CMAKE_COMMAND} -E tar xf "${MNN_350_ZIP}"
        WORKING_DIRECTORY "${MNN_350_EXTRACTED}"
        RESULT_VARIABLE EXTRACT_RESULT
    )
    if (NOT EXTRACT_RESULT EQUAL 0)
        message(FATAL_ERROR "Failed to extract MNN 3.5.0 zip")
    endif()

    # 复制所有 MNN 相关的 .so（libllm.so 依赖多个 MNN 子模块）
    set(MNN_SO_DIR "${MNN_350_EXTRACTED}/mnn_3.5.0_android_armv7_armv8_cpu_opencl_vulkan/arm64-v8a")
    file(GLOB ALL_MNN_SO "${MNN_SO_DIR}/*.so")
    # 复制到 ai-engine/libs（CMake 链接用）和 jniLibs（打包用）
    # 注意：MNN 3.5.0 的 libc++_shared.so 必须打包，因为它包含新版 C++ 符号
    # rime-engine 的旧版 libc++_shared.so 缺少这些符号会导致 MNN so 加载失败
    set(MNN_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a")
    file(MAKE_DIRECTORY "${MNN_JNI_DIR}")
    foreach(SO_PATH ${ALL_MNN_SO})
        file(COPY "${SO_PATH}" DESTINATION "${MNN_LIBS_DIR}")
        file(COPY "${SO_PATH}" DESTINATION "${MNN_JNI_DIR}")
    endforeach()
    list(LENGTH ALL_MNN_SO SO_COUNT)
    message(STATUS "Copied ${SO_COUNT} MNN .so files")

    message(STATUS "MNN 3.5.0 .so libraries extracted successfully")
endif()

# 确保 MNN 3.5.0 的 libc++_shared.so 被复制到 jniLibs
# 这个版本包含新版 C++ 符号（如 verbose_abort），NDK 自带的版本太旧
set(MNN_JNI_DIR "${CMAKE_CURRENT_SOURCE_DIR}/../jniLibs/arm64-v8a")
set(MNN_SO_DIR "${MNN_350_EXTRACTED}/mnn_3.5.0_android_armv7_armv8_cpu_opencl_vulkan/arm64-v8a")
if (EXISTS "${MNN_SO_DIR}/libc++_shared.so")
    file(COPY "${MNN_SO_DIR}/libc++_shared.so" DESTINATION "${MNN_JNI_DIR}")
    message(STATUS "Copied MNN 3.5.0 libc++_shared.so to jniLibs")
else()
    message(WARNING "MNN 3.5.0 libc++_shared.so not found at ${MNN_SO_DIR}")
endif()

# MNN 核心库（动态链接）
add_library(mnn SHARED IMPORTED)
set_target_properties(mnn PROPERTIES
    IMPORTED_LOCATION "${MNN_LIBS_DIR}/libMNN.so")

# MNN LLM 引擎（动态链接）
add_library(mnn_llm SHARED IMPORTED)
set_target_properties(mnn_llm PROPERTIES
    IMPORTED_LOCATION "${MNN_LIBS_DIR}/libllm.so")

# MNN LLM JNI 桥接
add_library(mnn-llm-bridge SHARED mnn-llm.cpp)
target_include_directories(mnn-llm-bridge PRIVATE
    ${MNN_INCLUDE_DIR}
    ${MNN_INCLUDE_DIR}/MNN)
target_link_libraries(mnn-llm-bridge mnn_llm mnn android log)
target_compile_options(mnn-llm-bridge PRIVATE -O3 -ffast-math)
