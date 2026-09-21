package dev.vitrail.mixin.sodium;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuSampler;
import dev.vitrail.render.GeometryHold;
import dev.vitrail.mixin.access.ShaderChunkRendererAccessor;
import dev.vitrail.render.ScenePass;
import dev.vitrail.render.TerrainDraw;
import dev.vitrail.render.timing.RingTimings;
import dev.vitrail.sodium.SodiumPasses;
import net.caffeinemc.mods.sodium.client.gui.SodiumOptions;
import net.caffeinemc.mods.sodium.client.render.chunk.ChunkRenderMatrices;
import net.caffeinemc.mods.sodium.client.render.chunk.DefaultChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.lists.ChunkRenderListIterable;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.render.viewport.CameraTransform;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces Sodium's supplied scene pass while retaining its geometry and draw command generation. */
@Mixin(value = DefaultChunkRenderer.class, remap = false)
public abstract class MixinDefaultChunkRenderer {
    @Inject(method = "rotate", at = @At("HEAD"), require = 1)
    private void vitrail$rotateBegin(CallbackInfo callback) { RingTimings.beginRotate(); }

    @Inject(method = "rotate", at = @At("RETURN"), require = 1)
    private void vitrail$rotateEnd(CallbackInfo callback) { RingTimings.endRotate(); }

    @WrapOperation(method = "prepare", require = 1,
            at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/gui/SodiumOptions$PerformanceSettings;useBlockFaceCulling:Z"))
    private boolean vitrail$shadowFaces(SodiumOptions.PerformanceSettings settings, Operation<Boolean> original) {
        return !TerrainDraw.drawingShadow() && original.call(settings);
    }

    @WrapMethod(method = "render", require = 1)
    private void vitrail$pass(ChunkRenderMatrices matrices, ChunkRenderListIterable lists, TerrainRenderPass terrain,
            CameraTransform camera, FogParameters fog, boolean sorted, RenderPass supplied, GpuSampler sampler,
            GpuBufferSlice globals, GpuBuffer sectionTimes, OitStage stage, Operation<Void> original) {
        var ours = SodiumPasses.of(terrain);
        if (ours == null || stage != null) {
            original.call(matrices, lists, terrain, camera, fog, sorted, supplied, sampler, globals, sectionTimes, stage);
            return;
        }
        ScenePass.enter(supplied);
        try {
            ScenePass.suspend();
            // begin chooses the pack program before its attachment descriptor is requested.
            ((ShaderChunkRendererAccessor) this).vitrail$begin(terrain, fog, sampler, stage);
            var descriptor = TerrainDraw.descriptor(ours, ScenePass.color(), ScenePass.depth());
            if (descriptor == null) {
                if (!TerrainDraw.drawingShadow()) {
                    original.call(matrices, lists, terrain, camera, fog, sorted, supplied, sampler, globals, sectionTimes, stage);
                }
            } else {
                try (RenderPass pass = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(), descriptor)) {
                    RenderSystem.bindDefaultUniforms(pass);
                    original.call(matrices, lists, terrain, camera, fog, sorted, pass, sampler, globals, sectionTimes, stage);
                }
            }
        } finally {
            ScenePass.leave();
        }
    }
}
