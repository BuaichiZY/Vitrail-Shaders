package dev.vitrail.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import java.util.List;

/** Removes game pipelines whose baked entity format no longer matches the active mesh. */
public interface StalePipelines {
    /** Destruction is deferred by RenderPearl until already recorded commands have completed. */
    List<RenderPipeline> vitrail$dropEntityPipelines();
}
