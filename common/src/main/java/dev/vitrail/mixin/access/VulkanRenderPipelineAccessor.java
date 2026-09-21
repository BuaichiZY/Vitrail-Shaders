package dev.vitrail.mixin.access;

import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Descriptor set layout needed by the non-push-descriptor fallback. */
@Mixin(VulkanRenderPipeline.class)
public interface VulkanRenderPipelineAccessor {
    @Accessor("descriptorSetLayout")
    long vitrail$descriptorSetLayout();
}
