package dev.vitrail.mixin.access;

import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.api.GpuDeviceBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The backend behind the device the game hands out, which is where the pipeline cache lives.
 * <p>
 * {@code RenderSystem.getDevice()} answers with the front, a plain class that forwards every call
 * and shows the backend to nobody. {@code EntityMesh.settle} needs to ask whether that backend is
 * one {@code VulkanDeviceMixin} taught to set entity pipelines aside, and an instanceof against a
 * private field is an accessor's whole job.
 */
@Mixin(com.mojang.renderpearl.frontend.FrontendGpuDevice.class)
public interface GpuDeviceAccessor {

	@Accessor("backend")
	GpuDeviceBackend vitrail$backend();
}
