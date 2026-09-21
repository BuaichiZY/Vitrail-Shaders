package dev.vitrail.mixin;

import com.mojang.blaze3d.pipeline.PipelineCache;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.vitrail.mixin.access.RenderPipelineAccessor;
import dev.vitrail.render.StalePipelines;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

/** Evicts changed entity layouts from the 26.3 game cache, using deferred GPU destruction. */
@Mixin(PipelineCache.class)
public abstract class VulkanDeviceMixin implements StalePipelines {
    @Shadow @Final
    private Map<RenderPipeline, CompiledRenderPipeline> cache;

    @Override
    public List<RenderPipeline> vitrail$dropEntityPipelines() {
        List<RenderPipeline> dropped = new ArrayList<>();
        this.cache.entrySet().removeIf(entry -> {
            boolean entity = ((RenderPipelineAccessor) entry.getKey()).vitrail$declaredFormats()
                    .stream().anyMatch(format -> format == DefaultVertexFormat.ENTITY);
            if (entity) {
                dropped.add(entry.getKey());
                entry.getValue().close();
            }
            return entity;
        });
        return dropped;
    }
}
