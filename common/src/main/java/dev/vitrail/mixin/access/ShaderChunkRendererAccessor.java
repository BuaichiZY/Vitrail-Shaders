package dev.vitrail.mixin.access;

import com.mojang.renderpearl.api.textures.GpuSampler;
import net.caffeinemc.mods.sodium.client.render.chunk.ShaderChunkRenderer;
import net.caffeinemc.mods.sodium.client.render.chunk.terrain.TerrainRenderPass;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Invokes the program selection on the class that declares it. */
@Mixin(value = ShaderChunkRenderer.class, remap = false)
public interface ShaderChunkRendererAccessor {
    @Invoker("begin")
    void vitrail$begin(TerrainRenderPass pass, FogParameters fog, GpuSampler sampler, OitStage stage);
}
