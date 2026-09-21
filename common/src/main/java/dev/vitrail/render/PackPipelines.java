package dev.vitrail.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.frontend.FrontendRenderPipeline;
import com.mojang.renderpearl.backend.vulkan.VulkanRenderPipeline;
import java.util.IdentityHashMap;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/** Owns pack pipelines independently of the game's resource-reload cache. */
public final class PackPipelines {
    private static final Map<RenderPipeline, CompiledRenderPipeline> CACHE = new IdentityHashMap<>();
    private static @Nullable GpuDevice owner;
    private static final Map<VulkanRenderPipeline, RenderPipeline> DESCRIPTIONS = new IdentityHashMap<>();
    private static final Map<CompiledRenderPipeline, RenderPipeline> GAME_DESCRIPTIONS = new java.util.WeakHashMap<>();

    private PackPipelines() {
    }

    /** Compiles on this thread; only the render thread may access the cache. */
    public static @Nullable CompiledRenderPipeline compile(GpuDevice device, RenderPipeline pipeline,
            @Nullable ShaderSource source) {
        RenderSystem.assertOnRenderThread();
        if (source == null) {
            return RenderSystem.getCompiledPipelineNullable(pipeline);
        }
        own(device);
        CompiledRenderPipeline compiled = CACHE.get(pipeline);
        if (valid(compiled)) {
            return compiled;
        }
        compiled = device.compilePipeline(pipeline, source, Runnable::run).join().finishCompile();
        if (compiled != null) {
            CACHE.put(pipeline, compiled);
            remember(pipeline, compiled);
        }
        return compiled;
    }

    /** Retrieves the compiled pack pipeline, or delegates a game pipeline to the game cache. */
    public static CompiledRenderPipeline get(RenderPipeline pipeline) {
        RenderSystem.assertOnRenderThread();
        CompiledRenderPipeline compiled = CACHE.get(pipeline);
        return valid(compiled) ? compiled : RenderSystem.getCompiledPipeline(pipeline);
    }

    public static boolean valid(@Nullable CompiledRenderPipeline compiled) {
        return compiled != null && !compiled.isClosed();
    }

    public static @Nullable CompiledRenderPipeline cached(RenderPipeline pipeline) {
        CompiledRenderPipeline cached = CACHE.get(pipeline);
        return valid(cached) ? cached : null;
    }

    public static void rememberGame(RenderPipeline pipeline, @Nullable CompiledRenderPipeline compiled) {
        if (compiled != null) {
            GAME_DESCRIPTIONS.put(compiled, pipeline);
        }
    }

    public static @Nullable RenderPipeline description(CompiledRenderPipeline compiled) {
        if (compiled instanceof FrontendRenderPipeline front
                && front.backendRenderPipeline() instanceof VulkanRenderPipeline backend) {
            RenderPipeline pack = DESCRIPTIONS.get(backend);
            if (pack != null) {
                return pack;
            }
        }
        return GAME_DESCRIPTIONS.get(compiled);
    }

    /** Transfers an unused worker result to the render thread's ownership. */
    public static boolean adopt(GpuDevice device, RenderPipeline pipeline, CompiledRenderPipeline compiled) {
        RenderSystem.assertOnRenderThread();
        own(device);
        if (CACHE.putIfAbsent(pipeline, compiled) != null) {
            return false;
        }
        remember(pipeline, compiled);
        return true;
    }

    private static void remember(RenderPipeline pipeline, CompiledRenderPipeline compiled) {
        if (compiled instanceof FrontendRenderPipeline front
                && front.backendRenderPipeline() instanceof VulkanRenderPipeline backend) {
            DESCRIPTIONS.put(backend, pipeline);
        }
    }

    public static @Nullable RenderPipeline description(VulkanRenderPipeline pipeline) {
        return DESCRIPTIONS.get(pipeline);
    }

    /** Invalidates both normal and fallback game pipelines when entity vertex layout changes. */
    public static java.util.List<RenderPipeline> dropGameEntityPipelines() {
        RenderSystem.assertOnRenderThread();
        java.util.List<RenderPipeline> dropped = new java.util.ArrayList<>();
        var current = dev.vitrail.mixin.access.RenderSystemPipelinesAccessor.vitrail$current();
        var fallback = dev.vitrail.mixin.access.RenderSystemPipelinesAccessor.vitrail$fallback();
        if (current != null) {
            dropped.addAll(((StalePipelines) current).vitrail$dropEntityPipelines());
        }
        if (fallback != null && fallback != current) {
            dropped.addAll(((StalePipelines) fallback).vitrail$dropEntityPipelines());
        }
        return dropped;
    }

    private static void own(GpuDevice device) {
        if (owner != null && owner != device) {
            throw new IllegalStateException("Pack pipelines outlived their GPU device");
        }
        owner = device;
    }

    /** Closes bound pipelines through the backend's deferred destruction queue. */
    public static void releaseLoad(int load) {
        RenderSystem.assertOnRenderThread();
        String prefix = "pipeline/pack/" + load + "/";
        CACHE.entrySet().removeIf(entry -> {
            if (entry.getKey().getLocation().getPath().startsWith(prefix)) {
                DESCRIPTIONS.values().removeIf(pipeline -> pipeline == entry.getKey());
                entry.getValue().close();
                return true;
            }
            return false;
        });
    }

    /** Frees a Vulkan pipeline that has never been submitted, including worker results. */
    public static void discardUnbound(CompiledRenderPipeline compiled) {
        if (compiled instanceof FrontendRenderPipeline front
                && front.backendRenderPipeline() instanceof VulkanRenderPipeline vulkan) {
            vulkan.destroy();
        } else {
            throw new IllegalArgumentException("Background pack compilation requires Vulkan");
        }
    }

    /** Called after pack workers have stopped and before the GPU device closes. */
    public static void close() {
        RenderSystem.assertOnRenderThread();
        CACHE.values().forEach(CompiledRenderPipeline::close);
        CACHE.clear();
        DESCRIPTIONS.clear();
        GAME_DESCRIPTIONS.clear();
        owner = null;
    }
}
