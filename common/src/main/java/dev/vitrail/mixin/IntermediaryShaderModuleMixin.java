package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.BackendRenderPipeline;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.frontend.shaders.PipelineBuilder;
import com.mojang.renderpearl.util.ShaderCompileException;
import dev.vitrail.Vitrail;
import dev.vitrail.render.GeometryStage;
import dev.vitrail.render.PackSpvModule;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import it.unimi.dsi.fastutil.objects.ReferenceArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/** Links geometry between vertex and fragment before RenderPearl assigns shared resource bindings. */
@Mixin(PipelineBuilder.class)
public abstract class IntermediaryShaderModuleMixin {
    @Shadow @Final private GlslCompiler compiler;
    @Unique private static final ThreadLocal<SpvModule> VITRAIL$GEOMETRY = new ThreadLocal<>();

    @WrapMethod(method = "generateBackendCreateInfo", require = 1)
    private BackendRenderPipeline.CreateInfo vitrail$geometry(RenderPipeline pipeline, ShaderSource source,
            ReferenceArrayList<BackendRenderPipeline.CreateInfo.Shader> shaders,
            Object2IntOpenHashMap<String> bindings, Operation<BackendRenderPipeline.CreateInfo> original) {
        SpvModule geometry;
        try {
            geometry = GeometryStage.begin(this.compiler, pipeline);
        } catch (ShaderCompileException e) {
            Vitrail.logger().error("Could not compile geometry stage for {}", pipeline.getLocation(), e);
            return null;
        }
        if (geometry == null) {
            return original.call(pipeline, source, shaders, bindings);
        }
        VITRAIL$GEOMETRY.set(geometry);
        try {
            return original.call(pipeline, source, shaders, bindings);
        } finally {
            VITRAIL$GEOMETRY.remove();
            // Once added, PipelineBuilder owns the module on success and on compilation failure.
            if (shaders.stream().noneMatch(shader -> shader.module() == geometry)) {
                geometry.close();
            }
        }
    }

    @WrapOperation(method = "generateBackendCreateInfo", require = 1,
            at = @At(value = "INVOKE", target = "Lcom/mojang/renderpearl/backend/api/SpvModule$Reflection;outputs()Ljava/util/List;"))
    private List<SpvModule.Reflection.InterfaceVariable> vitrail$linkGeometry(SpvModule.Reflection vertex,
            Operation<List<SpvModule.Reflection.InterfaceVariable>> original,
            @Local(argsOnly = true) ReferenceArrayList<BackendRenderPipeline.CreateInfo.Shader> shaders)
            throws ShaderCompileException {
        List<SpvModule.Reflection.InterfaceVariable> outputs = original.call(vertex);
        SpvModule geometry = VITRAIL$GEOMETRY.get();
        if (geometry == null) {
            var fragment = shaders.stream().filter(shader -> shader.module() instanceof PackSpvModule
                    && shader.module().type() == ShaderType.FRAGMENT).findFirst();
            if (fragment.isPresent()) {
                Map<String, SpvModule.Reflection.InterfaceVariable> producers = outputs.stream()
                        .collect(Collectors.toMap(SpvModule.Reflection.InterfaceVariable::name, Function.identity()));
                for (var input : fragment.get().module().reflect().inputs()) {
                    var producer = producers.get(input.name());
                    if (producer == null) {
                        throw new ShaderCompileException("Fragment input has no vertex output: " + input.name());
                    }
                    input.location(producer.location());
                }
            }
            return outputs;
        }
        SpvModule.Reflection middle = geometry.reflect();
        Map<String, SpvModule.Reflection.InterfaceVariable> producers = outputs.stream()
                .collect(Collectors.toMap(SpvModule.Reflection.InterfaceVariable::name, Function.identity()));
        for (SpvModule.Reflection.InterfaceVariable input : middle.inputs()) {
            var producer = producers.get(input.name());
            if (producer == null || producer.type().baseType() != input.type().baseType()
                    || producer.type().vectorSize() != input.type().vectorSize()) {
                throw new ShaderCompileException("Geometry input has no compatible vertex output: " + input.name());
            }
            input.location(producer.location());
        }
        var fragment = shaders.stream().filter(shader -> shader.module().type() == ShaderType.FRAGMENT)
                .findFirst().orElseThrow().module().reflect();
        Map<String, SpvModule.Reflection.InterfaceVariable> middleOutputs = middle.outputs().stream()
                .collect(Collectors.toMap(SpvModule.Reflection.InterfaceVariable::name, Function.identity()));
        for (var input : fragment.inputs()) {
            var producer = middleOutputs.get(input.name());
            if (producer == null) {
                throw new ShaderCompileException("Fragment input has no geometry output: " + input.name());
            }
            input.location(producer.location());
        }
        shaders.add(new BackendRenderPipeline.CreateInfo.Shader("vitrail geometry", "main", geometry));
        return middle.outputs();
    }
}
