package dev.vitrail.mixin.access;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.systems.RenderSystem;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The two game caches which may contain pipelines compiled with the old entity mesh. */
@Mixin(RenderSystem.class)
public interface RenderSystemPipelinesAccessor {
    @Accessor("currentPipelineCache")
    static @Nullable PipelineCache vitrail$current() {
        throw new AssertionError();
    }

    @Accessor("fallbackPipelineCache")
    static @Nullable PipelineCache vitrail$fallback() {
        throw new AssertionError();
    }
}
