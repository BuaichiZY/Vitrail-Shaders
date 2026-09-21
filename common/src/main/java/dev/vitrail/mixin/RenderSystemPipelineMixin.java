package dev.vitrail.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.vitrail.render.PackPipelines;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Routes pack descriptions through their own cache and remembers game descriptions for adapters. */
@Mixin(RenderSystem.class)
public abstract class RenderSystemPipelineMixin {
    @Inject(method = "getCompiledPipelineNullable", at = @At("HEAD"), cancellable = true, require = 1)
    private static void vitrail$pack(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> callback) {
        CompiledRenderPipeline cached = PackPipelines.cached(pipeline);
        if (cached != null) {
            callback.setReturnValue(cached);
        }
    }

    @Inject(method = "getCompiledPipelineNullable", at = @At("RETURN"), require = 1)
    private static void vitrail$remember(RenderPipeline pipeline, CallbackInfoReturnable<CompiledRenderPipeline> callback) {
        PackPipelines.rememberGame(pipeline, callback.getReturnValue());
    }
}
