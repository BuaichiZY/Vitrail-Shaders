package dev.vitrail.render;

import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.backend.api.SpvModule;
import com.mojang.renderpearl.frontend.shaders.SPIRVModule;
import com.mojang.renderpearl.util.ShaderCompileException;
import java.nio.ByteBuffer;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Native reflection remains owned by SPIRVModule; this view filters unreachable samplers. */
public final class PackSpvModule extends SPIRVModule {
    private final String name;
    private final boolean geometry;
    private SpvModule.@Nullable Reflection visible;

    public PackSpvModule(String name, ByteBuffer bytes, ShaderType type) {
        super(bytes, type);
        this.name = name;
        this.geometry = GeometryStage.compiling();
    }

    public boolean isGeometry() {
        return this.geometry;
    }

    @Override
    public SpvModule.Reflection reflect() throws ShaderCompileException {
        if (this.visible == null) {
            SpvModule.Reflection raw = super.reflect();
            List<SpvModule.Reflection.Descriptor> descriptors = SamplerReach.narrow(this.name, spv(), raw.descriptors())
                    .stream().map(PackDescriptor::new).map(SpvModule.Reflection.Descriptor.class::cast).toList();
            this.visible = new SpvModule.Reflection() {
                @Override public List<InterfaceVariable> inputs() { return raw.inputs(); }
                @Override public List<InterfaceVariable> outputs() { return raw.outputs(); }
                @Override public List<Descriptor> descriptors() { return descriptors; }
                @Override public List<Descriptor> descriptors(int type) {
                    return descriptors.stream().filter(d -> d.resourceType() == type).toList();
                }
                @Override public List<PushConstant> pushConstants() { return raw.pushConstants(); }
            };
        }
        return this.visible;
    }

    @Override
    public SpvModule.@Nullable Reflection getReflectionInfoIfAvailable() {
        return this.visible;
    }

    /**
     * The frontend exposes only buffer and sampled-image bindings. Pack storage bindings use those
     * slots, while the Vulkan descriptor hooks supply the actual storage types by uniform name.
     * This adapter changes validation metadata only; SPIR-V types and native reflection stay intact.
     */
    private record PackDescriptor(SpvModule.Reflection.Descriptor raw) implements SpvModule.Reflection.Descriptor {
        @Override public String name() { return this.raw.name(); }
        @Override public SpvModule.Reflection.Type type() { return new PackType(this.raw.type()); }
        @Override public int resourceType() {
            return switch (this.raw.resourceType()) {
                case 2 -> 1;
                case 6 -> 7;
                default -> this.raw.resourceType();
            };
        }
        @Override public int descriptorSetIndex() { return this.raw.descriptorSetIndex(); }
        @Override public void descriptorSetIndex(int index) { this.raw.descriptorSetIndex(index); }
        @Override public int binding() { return this.raw.binding(); }
        @Override public void binding(int binding) { this.raw.binding(binding); }
    }

    private record PackType(SpvModule.Reflection.Type raw) implements SpvModule.Reflection.Type {
        @Override public int baseType() { return this.raw.baseType(); }
        @Override public int dimensions() {
            int dimensions = this.raw.dimensions();
            return dimensions == 0 || dimensions == 2 ? 1 : dimensions;
        }
        @Override public int vectorSize() { return this.raw.vectorSize(); }
        @Override public int arrayDimensions() { return this.raw.arrayDimensions(); }
        @Override public int arrayLength(int dimension) { return this.raw.arrayLength(dimension); }
    }
}
