package dev.vitrail.render;

import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanUtils;
import com.mojang.renderpearl.util.ShaderCompileException;
import java.util.ArrayList;
import java.util.List;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.spvc.Spvc;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkShaderModuleCreateInfo;

/** Rebinds compute resources through RenderPearl reflection, then creates the Vulkan module. */
public final class ComputeShader {
    private ComputeShader() {
    }

    public record Compiled(long module, List<BindGroupLayout.UniformDescription> entries) {
    }

    public static Compiled compile(VulkanDevice device, SpvModule module) throws ShaderCompileException {
        List<BindGroupLayout.UniformDescription> entries = new ArrayList<>();
        for (SpvModule.Reflection.Descriptor descriptor : module.reflect().descriptors()) {
            UniformType type = switch (descriptor.resourceType()) {
                case Spvc.SPVC_RESOURCE_TYPE_UNIFORM_BUFFER, Spvc.SPVC_RESOURCE_TYPE_STORAGE_BUFFER -> UniformType.UNIFORM_BUFFER;
                case Spvc.SPVC_RESOURCE_TYPE_SAMPLED_IMAGE, Spvc.SPVC_RESOURCE_TYPE_STORAGE_IMAGE -> UniformType.COMBINED_IMAGE_SAMPLER;
                default -> throw new ShaderCompileException("Unsupported compute resource " + descriptor.name());
            };
            descriptor.binding(entries.size());
            descriptor.descriptorSetIndex(0);
            entries.add(new BindGroupLayout.UniformDescription(descriptor.name(), type));
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(module.spv());
            var handle = stack.callocLong(1);
            VulkanUtils.crashIfFailure(device, VK12.vkCreateShaderModule(device.vkDevice(), info, null, handle),
                    "Cannot compile Vitrail compute module");
            return new Compiled(handle.get(0), List.copyOf(entries));
        }
    }
}
