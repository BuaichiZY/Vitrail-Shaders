package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import dev.vitrail.cache.ModuleCache;
import dev.vitrail.glsl.LoadClock;
import dev.vitrail.render.GeometryStage;
import dev.vitrail.render.PackNames;
import dev.vitrail.render.PackSpvModule;
import dev.vitrail.render.RawLocals;
import dev.vitrail.render.ShaderDebugInfo;
import java.nio.ByteBuffer;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.client.renderer.ShaderDefines;
import org.lwjgl.util.shaderc.Shaderc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Patches and caches pack SPIR-V at RenderPearl's compilation boundary. */
@Mixin(GlslCompiler.class)
public abstract class GlslCompilerMixin {
    @Shadow @Final private boolean isZeroToOne;
    @Shadow @Final private boolean shaderDrawParameters;

    @WrapOperation(method = "compileToSpv", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/frontend/shaders/GlslCompiler;createBaseShaderOptions()J"))
    private long vitrail$locations(GlslCompiler compiler, Operation<Long> original,
            @Local(argsOnly = true, ordinal = 0) String name) {
        long options = original.call(compiler);
        if (RawLocals.ours(name)) {
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            Shaderc.shaderc_compile_options_add_macro_definition(options, "gl_VertexID", "gl_VertexIndex");
            Shaderc.shaderc_compile_options_add_macro_definition(options, "gl_InstanceID", "gl_InstanceIndex");
        }
        return options;
    }

    @ModifyArg(method = "compileToSpv", require = 1, index = 2,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_into_spv(JLjava/nio/ByteBuffer;ILjava/nio/ByteBuffer;Ljava/nio/ByteBuffer;J)J"))
    private int vitrail$geometryKind(int kind) {
        return GeometryStage.compiling() ? Shaderc.shaderc_glsl_geometry_shader : kind;
    }

    @WrapOperation(method = "createBaseShaderOptions", require = 1,
            at = @At(value = "INVOKE", target = "Lorg/lwjgl/util/shaderc/Shaderc;shaderc_compile_options_set_generate_debug_info(J)V"))
    private void vitrail$debugInfo(long options, Operation<Void> original) {
        ShaderDebugInfo.announce();
        if (ShaderDebugInfo.asked()) {
            original.call(options);
        }
    }

    @WrapOperation(method = "compileToSpv", require = 1,
            at = @At(value = "NEW", target = "com/mojang/renderpearl/frontend/shaders/SPIRVModule"))
    private SPIRVModule vitrail$patchModule(ByteBuffer bytes, ShaderType type,
            Operation<SPIRVModule> original, @Local(argsOnly = true, ordinal = 0) String name) {
        if (!RawLocals.ours(name)) {
            return original.call(bytes, type);
        }
        return new PackSpvModule(name, PackNames.patch(name, RawLocals.patch(name, bytes)), type);
    }

    @WrapMethod(method = "compileToSpv", require = 1)
    private SpvModule vitrail$cached(String name, String source, ShaderType type,
            ShaderDefines defines, ShaderSource includes, Operation<SpvModule> original) {
        // Game includes are resource-reload dependent; only pack sources are already expanded.
        if (!RawLocals.ours(name)) {
            return original.call(name, source, type, defines, includes);
        }
        long started = System.nanoTime();
        RawLocals.begin();
        try {
            String recipe = "vulkan-builtins-1/" + (GeometryStage.compiling() ? "GEOMETRY" : type.name())
                    + "/" + this.isZeroToOne + "/" + this.shaderDrawParameters
                    + "/" + RenderSystem.getDevice().getDeviceInfo().hintsAndWorkarounds().isExplicitDepthRequired()
                    + "/" + new TreeMap<>(defines.values()) + "/" + new TreeSet<>(defines.flags());
            String key = ModuleCache.keyOf(source, recipe);
            SpvModule served = ModuleCache.lookup(key, name);
            if (served != null) {
                return served;
            }
            ModuleCache.building(name);
            SpvModule compiled = original.call(name, source, type, defines, includes);
            ModuleCache.store(key, compiled);
            return compiled;
        } finally {
            RawLocals.end();
            LoadClock.module(System.nanoTime() - started);
        }
    }
}
