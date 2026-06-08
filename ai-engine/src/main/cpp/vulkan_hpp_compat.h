// vulkan_hpp_compat.h - 补齐 NDK vulkan.hpp 缺失的扩展类型
// 在 ggml-vulkan.cpp 的 #include <vulkan/vulkan.hpp> 之前 include 此文件
#ifndef VULKAN_HPP_COMPAT_H
#define VULKAN_HPP_COMPAT_H

// 确保 C 扩展宏被定义（NDK vulkan_core.h 可能没有自动启用这些扩展）
#ifndef VK_KHR_shader_integer_dot_product
#define VK_KHR_shader_integer_dot_product 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR ((VkStructureType)1000280001)
#endif

#ifndef VK_EXT_pipeline_robustness
#define VK_EXT_pipeline_robustness 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_FEATURES_EXT ((VkStructureType)1000280000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_PIPELINE_ROBUSTNESS_PROPERTIES_EXT ((VkStructureType)1000280001)
#define VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT ((VkStructureType)1000280002)
#endif

// 在 vulkan.hpp 之后定义缺失的 C++ wrapper 类型
// 这些类型在 NDK 的 vulkan.hpp 中缺失，但 C 结构体在 vulkan_core.h 中存在
#include <vulkan/vulkan.hpp>

namespace vk {

// PhysicalDeviceShaderIntegerDotProductPropertiesKHR
struct PhysicalDeviceShaderIntegerDotProductPropertiesKHR : public PhysicalDeviceProperties2 {
    VkPhysicalDeviceShaderIntegerDotProductProperties inner;
    PhysicalDeviceShaderIntegerDotProductPropertiesKHR() {
        sType = static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SHADER_INTEGER_DOT_PRODUCT_PROPERTIES_KHR);
        pNext = nullptr;
    }
    operator VkPhysicalDeviceShaderIntegerDotProductProperties&() { return inner; }
    operator const VkPhysicalDeviceShaderIntegerDotProductProperties&() const { return inner; }
    VkBool32 integerDotProduct4x8BitPackedMixedSignednessAccelerated const { return inner.integerDotProduct4x8BitPackedMixedSignednessAccelerated; }
};

// PipelineRobustnessBufferBehaviorEXT 枚举
enum class PipelineRobustnessBufferBehaviorEXT {
    DefaultRobustnessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_DEVICE_DEFAULT_EXT,
    RobustBufferAccessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_EXT,
    LocalRobustBufferAccessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_ROBUST_BUFFER_ACCESS_2_EXT,
    NoRobustBufferAccessEXT = VK_PIPELINE_ROBUSTNESS_BUFFER_BEHAVIOR_NO_ROBUST_BUFFER_ACCESS_EXT,
};

// PipelineRobustnessCreateInfoEXT
struct PipelineRobustnessCreateInfoEXT {
    VkStructureType sType;
    const void* pNext;
    PipelineRobustnessBufferBehaviorEXT storageBuffers;
    PipelineRobustnessBufferBehaviorEXT uniformBuffers;
    PipelineRobustnessBufferBehaviorEXT vertexInputs;
    PipelineRobustnessBufferBehaviorEXT images;
    PipelineRobustnessCreateInfoEXT() : sType(static_cast<VkStructureType>(VK_STRUCTURE_TYPE_PIPELINE_ROBUSTNESS_CREATE_INFO_EXT)), pNext(nullptr) {}
    operator VkPipelineRobustnessCreateInfoEXT&() { return *reinterpret_cast<VkPipelineRobustnessCreateInfoEXT*>(this); }
};

} // namespace vk

#endif // VULKAN_HPP_COMPAT_H
