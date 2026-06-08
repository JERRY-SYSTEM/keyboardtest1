// vulkan_ndk_compat.h - NDK r25c vulkan.hpp 兼容性补丁
// 需要在所有 #include <vulkan/vulkan.hpp> 之前 include 此文件
//
// NDK r25c 的 vulkan.hpp 基于较老的 Vulkan-Hpp，缺少大量扩展类型定义。
// 此头文件在 vulkan.hpp 之后注入缺失的类型，使 ggml-vulkan.cpp 能编译。
//
// 用法：在 CMakeLists.txt 中用 -include 编译选项强制 include 此文件

#ifndef VULKAN_NDK_COMPAT_H
#define VULKAN_NDK_COMPAT_H

// 先 include 原始 vulkan.hpp
#include <vulkan/vulkan.hpp>

// ============================================================
// 缺失的 Vulkan 扩展宏定义
// ============================================================

#ifndef VK_KHR_shader_integer_dot_product
#define VK_KHR_shader_integer_dot_product 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR ((VkStructureType)1000280000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR ((VkStructureType)1000280001)
#endif

#ifndef VK_EXT_pipeline_robustness
#define VK_EXT_pipeline_robustness 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_FEATURES_EXT ((VkStructureType)1000280000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_PROPERTIES_EXT ((VkStructureType)1000280001)
#define VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT ((VkStructureType)1000280002)
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT ((VkPipelineRobustnessBufferBehaviorEXT)0)
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_EXT ((VkPipelineRobustnessBufferBehaviorEXT)1)
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2_EXT ((VkPipelineRobustnessBufferBehaviorEXT)2)
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_NO_ROBUST_BUFFER_ACCESS_EXT ((VkPipelineRobustnessBufferBehaviorEXT)3)
#endif

#ifndef VK_KHR_maintenance4
#define VK_KHR_maintenance4 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR ((VkStructureType)1000413000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR ((VkStructureType)1000413001)
#define VK_STRUCTURE_TYPE_DEVICE_BUFFER_MEMORY_REQUIREMENTS_KHR ((VkStructureType)1000413002)
#define VK_STRUCTURE_TYPE_DEVICE_IMAGE_MEMORY_REQUIREMENTS_KHR ((VkStructureType)1000413003)
#endif

#ifndef VK_KHR_cooperative_matrix
#define VK_KHR_cooperative_matrix 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR ((VkStructureType)1000506000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_PROPERTIES_KHR ((VkStructureType)1000506001)
#define VK_STRUCTURE_TYPE_COOPERATIVE_MATRIX_PROPERTIES_KHR ((VkStructureType)1000506002)
#endif

// ============================================================
// 缺失的 C++ wrapper 类型定义
// ============================================================

namespace vk {

// ---- PhysicalDeviceShaderIntegerDotProductPropertiesKHR ----
struct PhysicalDeviceShaderIntegerDotProductPropertiesKHR {
    VkStructureType sType;
    void* pNext;
    VkBool32 integerDotProduct8BitUnsignedAccelerated;
    VkBool32 integerDotProduct8BitSignedAccelerated;
    VkBool32 integerDotProduct8BitMixedSignednessAccelerated;
    VkBool32 integerDotProduct4x8BitPackedUnsignedAccelerated;
    VkBool32 integerDotProduct4x8BitPackedSignedAccelerated;
    VkBool32 integerDotProduct4x8BitPackedMixedSignednessAccelerated;
    VkBool32 integerDotProduct16BitUnsignedAccelerated;
    VkBool32 integerDotProduct16BitSignedAccelerated;
    VkBool32 integerDotProduct16BitMixedSignednessAccelerated;
    VkBool32 integerDotProduct32BitUnsignedAccelerated;
    VkBool32 integerDotProduct32BitSignedAccelerated;
    VkBool32 integerDotProduct32BitMixedSignednessAccelerated;
    VkBool32 integerDotProduct64BitUnsignedAccelerated;
    VkBool32 integerDotProduct64BitSignedAccelerated;
    VkBool32 integerDotProduct64BitMixedSignednessAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating8BitUnsignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating8BitSignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating8BitMixedSignednessAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating4x8BitPackedUnsignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating4x8BitPackedSignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating4x8BitPackedMixedSignednessAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating16BitUnsignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating16BitSignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating16BitMixedSignednessAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating32BitUnsignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating32BitSignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating32BitMixedSignednessAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating64BitUnsignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating64BitSignedAccelerated;
    VkBool32 integerDotProductAccumulatingSaturating64BitMixedSignednessAccelerated;
    PhysicalDeviceShaderIntegerDotProductPropertiesKHR() :
        sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR),
        pNext(nullptr), integerDotProduct8BitUnsignedAccelerated(VK_FALSE),
        integerDotProduct4x8BitPackedMixedSignednessAccelerated(VK_FALSE) {}
};

// ---- PhysicalDeviceShaderIntegerDotProductFeaturesKHR ----
struct PhysicalDeviceShaderIntegerDotProductFeaturesKHR {
    VkStructureType sType;
    void* pNext;
    VkBool32 shaderIntegerDotProduct;
    PhysicalDeviceShaderIntegerDotProductFeaturesKHR() :
        sType(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR),
        pNext(nullptr), shaderIntegerDotProduct(VK_FALSE) {}
};

// ---- PhysicalDeviceMaintenance4Properties ----
struct PhysicalDeviceMaintenance4Properties {
    VkStructureType sType;
    void* pNext;
    VkDeviceSize maxBufferSize;
    PhysicalDeviceMaintenance4Properties() :
        sType(static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR)),
        pNext(nullptr), maxBufferSize(0) {}
};

// ---- PhysicalDeviceMaintenance4Features ----
struct PhysicalDeviceMaintenance4Features {
    VkStructureType sType;
    void* pNext;
    VkBool32 maintenance4;
    PhysicalDeviceMaintenance4Features() :
        sType(static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR)),
        pNext(nullptr), maintenance4(VK_FALSE) {}
};

// ---- PipelineRobustnessBufferBehaviorEXT ----
enum class PipelineRobustnessBufferBehaviorEXT {
    DeviceDefaultEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT,
    RobustBufferAccessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_EXT,
    RobustBufferAccess2EXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2_EXT,
    NoRobustBufferAccessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_NO_ROBUST_BUFFER_ACCESS_EXT,
};

// ---- PipelineRobustnessCreateInfoEXT ----
struct PipelineRobustnessCreateInfoEXT {
    VkStructureType sType;
    const void* pNext;
    PipelineRobustnessBufferBehaviorEXT storageBuffers;
    PipelineRobustnessBufferBehaviorEXT uniformBuffers;
    PipelineRobustnessBufferBehaviorEXT vertexInputs;
    PipelineRobustnessBufferBehaviorEXT images;
    PipelineRobustnessCreateInfoEXT() :
        sType(static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT)),
        pNext(nullptr),
        storageBuffers(PipelineRobustnessBufferBehaviorEXT::DeviceDefaultEXT),
        uniformBuffers(PipelineRobustnessBufferBehaviorEXT::DeviceDefaultEXT),
        vertexInputs(PipelineRobustnessBufferBehaviorEXT::DeviceDefaultEXT),
        images(PipelineRobustnessBufferBehaviorEXT::DeviceDefaultEXT) {}
};

// ---- PhysicalDeviceCooperativeMatrixFeaturesKHR ----
struct PhysicalDeviceCooperativeMatrixFeaturesKHR {
    VkStructureType sType;
    void* pNext;
    VkBool32 cooperativeMatrix;
    VkBool32 cooperativeMatrixRobustBufferAccess;
    PhysicalDeviceCooperativeMatrixFeaturesKHR() :
        sType(static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR)),
        pNext(nullptr), cooperativeMatrix(VK_FALSE), cooperativeMatrixRobustBufferAccess(VK_FALSE) {}
};

} // namespace vk

#endif // VULKAN_NDK_COMPAT_H
