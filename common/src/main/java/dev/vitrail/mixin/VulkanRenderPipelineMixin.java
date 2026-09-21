package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import dev.vitrail.render.GeometryStage;
import dev.vitrail.render.PackSpvModule;
import org.lwjgl.vulkan.VkPipelineShaderStageCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** The public ShaderType enum has two values; a geometry SPIR-V module needs Vulkan's third stage. */
@Mixin(VulkanRenderPipeline.class)
public abstract class VulkanRenderPipelineMixin {
    @WrapOperation(method = "compile", require = 1,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VkPipelineShaderStageCreateInfo;stage(I)Lorg/lwjgl/vulkan/VkPipelineShaderStageCreateInfo;"))
    private static VkPipelineShaderStageCreateInfo vitrail$geometryStage(VkPipelineShaderStageCreateInfo info,
            int stage, Operation<VkPipelineShaderStageCreateInfo> original,
            @Local BackendRenderPipeline.CreateInfo.Shader shader) {
        return original.call(info, shader.module() instanceof PackSpvModule pack && pack.isGeometry()
                ? GeometryStage.STAGE_BIT : stage);
    }
}
