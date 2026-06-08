// vulkan_ndk_compat_post.h
// 在 vulkan.hpp 之后 include，补充 NDK r25c 缺失的 Vulkan 扩展类型和枚举

#ifndef VULKAN_NDK_COMPAT_POST_H
#define VULKAN_NDK_COMPAT_POST_H

// ============================================================
// 绕过 NDK vulkan.hpp 的 const sType 限制
// NDK r25c 的 vulkan.hpp 中结构体的 sType 是 const，不能直接赋值
// 用 memcpy 绕过
// ============================================================
#define VULKAN_HPP_CONST_ASSIGN(member, value) \
    do { \
        auto _v = (value); \
        std::memcpy(const_cast<void*>(static_cast<const void*>(&(member))), &_v, sizeof(_v)); \
    } while(0)

#include <cstring>  // for std::memcpy

// ============================================================
// 缺失的 Vulkan 结构体类型宏（NDK r25c vulkan_core.h 中没有）
// 这些值来自 Vulkan 规范，是固定不变的
// ============================================================

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR ((VkStructureType)1000280001)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_FEATURES_KHR ((VkStructureType)1000280000)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR ((VkStructureType)1000413001)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR ((VkStructureType)1000413000)
#endif

#ifndef VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT
#define VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT ((VkStructureType)1000280002)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_FEATURES_EXT
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_FEATURES_EXT ((VkStructureType)1000280000)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_PROPERTIES_EXT
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_PROPERTIES_EXT ((VkStructureType)1000280001)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR ((VkStructureType)1000506000)
#endif

#ifndef VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_PROPERTIES_KHR
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_PROPERTIES_KHR ((VkStructureType)1000506001)
#endif

// ============================================================
// 缺失的 C 枚举类型（NDK r25c 中没有）
// ============================================================

#ifndef VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT 0
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_EXT 1
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2_EXT 2
#define VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_NO_ROBUST_BUFFER_ACCESS_EXT 3
#endif

// VkPipelineRobustnessBufferBehaviorEXT 枚举类型
typedef int VkPipelineRobustnessBufferBehaviorEXT;

// ============================================================
// 缺失的 C 结构体类型
// ============================================================

// VkPhysicalDevicePipelineRobustnessFeaturesEXT
typedef struct VkPhysicalDevicePipelineRobustnessFeaturesEXT {
    VkStructureType sType;
    void* pNext;
    VkBool32 pipelineRobustness;
} VkPhysicalDevicePipelineRobustnessFeaturesEXT;

// VkPhysicalDevicePipelineRobustnessPropertiesEXT
typedef struct VkPhysicalDevicePipelineRobustnessPropertiesEXT {
    VkStructureType sType;
    void* pNext;
    VkPipelineRobustnessBufferBehaviorEXT defaultRobustnessStorageBuffers;
    VkPipelineRobustnessBufferBehaviorEXT defaultRobustnessUniformBuffers;
    VkPipelineRobustnessBufferBehaviorEXT defaultRobustnessVertexInputs;
    VkPipelineRobustnessBufferBehaviorEXT defaultRobustnessImages;
} VkPhysicalDevicePipelineRobustnessPropertiesEXT;

// VkPipelineRobustnessCreateInfoEXT
typedef struct VkPipelineRobustnessCreateInfoEXT {
    VkStructureType sType;
    const void* pNext;
    VkPipelineRobustnessBufferBehaviorEXT storageBuffers;
    VkPipelineRobustnessBufferBehaviorEXT uniformBuffers;
    VkPipelineRobustnessBufferBehaviorEXT vertexInputs;
    VkPipelineRobustnessBufferBehaviorEXT images;
} VkPipelineRobustnessCreateInfoEXT;

// VkPhysicalDeviceMaintenance4Features
#ifndef VkPhysicalDeviceMaintenance4Features
typedef struct VkPhysicalDeviceMaintenance4Features {
    VkStructureType sType;
    const void* pNext;
    VkBool32 maintenance4;
} VkPhysicalDeviceMaintenance4Features;
#endif

// VkPhysicalDeviceMaintenance4Properties
typedef struct VkPhysicalDeviceMaintenance4Properties {
    VkStructureType sType;
    void* pNext;
    VkDeviceSize maxBufferSize;
} VkPhysicalDeviceMaintenance4Properties;

// VkPhysicalDeviceCooperativeMatrixFeaturesKHR
typedef struct VkPhysicalDeviceCooperativeMatrixFeaturesKHR {
    VkStructureType sType;
    const void* pNext;
    VkBool32 cooperativeMatrix;
    VkBool32 cooperativeMatrixRobustBufferAccess;
} VkPhysicalDeviceCooperativeMatrixFeaturesKHR;

// VkPhysicalDeviceShaderIntegerDotProductFeaturesKHR
typedef struct VkPhysicalDeviceShaderIntegerDotProductFeaturesKHR {
    VkStructureType sType;
    const void* pNext;
    VkBool32 shaderIntegerDotProduct;
} VkPhysicalDeviceShaderIntegerDotProductFeaturesKHR;

// ============================================================
// 缺失的 C++ wrapper 类型
// ============================================================

namespace vk {

// ---- PhysicalDeviceShaderIntegerDotProductPropertiesKHR ----
// 注意：NDK vulkan.hpp 的 sType 是 const，不能赋值
// 使用 placement new 绕过 const 限制
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

    PhysicalDeviceShaderIntegerDotProductPropertiesKHR() {
        sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR;
        pNext = nullptr;
        integerDotProduct4x8BitPackedMixedSignednessAccelerated = VK_FALSE;
    }
};

// ---- PhysicalDeviceMaintenance4Properties ----
struct PhysicalDeviceMaintenance4Properties {
    VkStructureType sType;
    void* pNext;
    VkDeviceSize maxBufferSize;
    PhysicalDeviceMaintenance4Properties() {
        sType = static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR);
        pNext = nullptr;
        maxBufferSize = 0;
    }
};

// ---- PhysicalDeviceMaintenance4Features ----
struct PhysicalDeviceMaintenance4Features {
    VkStructureType sType;
    const void* pNext;
    VkBool32 maintenance4;
    PhysicalDeviceMaintenance4Features() {
        sType = static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR);
        pNext = nullptr;
        maintenance4 = VK_FALSE;
    }
};

// ---- PipelineRobustnessCreateInfoEXT ----
struct PipelineRobustnessCreateInfoEXT {
    VkStructureType sType;
    const void* pNext;
    VkPipelineRobustnessBufferBehaviorEXT storageBuffers;
    VkPipelineRobustnessBufferBehaviorEXT uniformBuffers;
    VkPipelineRobustnessBufferBehaviorEXT vertexInputs;
    VkPipelineRobustnessBufferBehaviorEXT images;
    PipelineRobustnessCreateInfoEXT() {
        sType = static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT);
        pNext = nullptr;
        storageBuffers = static_cast<VkPipelineRobustnessBufferBehaviorEXT>(VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT);
        uniformBuffers = static_cast<VkPipelineRobustnessBufferBehaviorEXT>(VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT);
        vertexInputs = static_cast<VkPipelineRobustnessBufferBehaviorEXT>(VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT);
        images = static_cast<VkPipelineRobustnessBufferBehaviorEXT>(VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT);
    }
};

// ---- PhysicalDeviceCooperativeMatrixFeaturesKHR ----
struct PhysicalDeviceCooperativeMatrixFeaturesKHR {
    VkStructureType sType;
    const void* pNext;
    VkBool32 cooperativeMatrix;
    VkBool32 cooperativeMatrixRobustBufferAccess;
    PhysicalDeviceCooperativeMatrixFeaturesKHR() {
        sType = static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR);
        pNext = nullptr;
        cooperativeMatrix = VK_FALSE;
        cooperativeMatrixRobustBufferAccess = VK_FALSE;
    }
};

} // namespace vk

#endif // VULKAN_NDK_COMPAT_POST_H
