// vulkan_ndk_compat_pre.h
// 在 vulkan.hpp 之前 include，定义缺失的 Vulkan 扩展宏
// 用法：CMakeLists.txt 中 target_compile_options(ggml-vulkan PRIVATE -include vulkan_ndk_compat_pre.h)

#ifndef VULKAN_NDK_COMPAT_PRE_H
#define VULKAN_NDK_COMPAT_PRE_H

// 确保这些宏在 vulkan.hpp 被 include 之前就已定义
// 这样 vulkan.hpp 内部的 #ifndef 检查会认为扩展已启用

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
#endif

#ifndef VK_KHR_maintenance4
#define VK_KHR_maintenance4 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_FEATURES_KHR ((VkStructureType)1000413000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_MAINTENANCE_4_PROPERTIES_KHR ((VkStructureType)1000413001)
#endif

#ifndef VK_KHR_cooperative_matrix
#define VK_KHR_cooperative_matrix 1
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_FEATURES_KHR ((VkStructureType)1000506000)
#define VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_COOPERATIVE_MATRIX_PROPERTIES_KHR ((VkStructureType)1000506001)
#endif

#endif // VULKAN_NDK_COMPAT_PRE_H
