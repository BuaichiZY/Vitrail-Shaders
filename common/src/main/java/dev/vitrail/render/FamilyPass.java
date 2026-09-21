package dev.vitrail.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.jspecify.annotations.Nullable;

/** Delays a family pass until the game has uploaded its geometry and dynamic transforms. */
public final class FamilyPass implements InvocationHandler {
    private final RenderPass supplied;
    private final Supplier<@Nullable RenderPipeline> select;
    private final Supplier<@Nullable RenderPassDescriptor> descriptor;
    private final BiConsumer<RenderPass, RenderPipeline> bind;
    private final BiConsumer<GpuTextureView, GpuSampler> texture;
    private final BiConsumer<RenderPass, RenderPipeline> afterDraw;
    private final List<Call> pending = new ArrayList<>();
    private @Nullable RenderPipeline pipeline;
    private @Nullable RenderPass pass;
    private boolean owned;

    private FamilyPass(RenderPass supplied, Supplier<@Nullable RenderPipeline> select,
            Supplier<@Nullable RenderPassDescriptor> descriptor, BiConsumer<RenderPass, RenderPipeline> bind,
            BiConsumer<GpuTextureView, GpuSampler> texture, BiConsumer<RenderPass, RenderPipeline> afterDraw) {
        this.supplied = supplied;
        this.select = select;
        this.descriptor = descriptor;
        this.bind = bind;
        this.texture = texture;
        this.afterDraw = afterDraw;
    }

    public static void draw(RenderPass supplied, Supplier<@Nullable RenderPipeline> select,
            Supplier<@Nullable RenderPassDescriptor> descriptor, BiConsumer<RenderPass, RenderPipeline> bind,
            BiConsumer<GpuTextureView, GpuSampler> texture, BiConsumer<RenderPass, RenderPipeline> afterDraw,
            Consumer<RenderPass> body) {
        ScenePass.enter(supplied);
        ScenePass.suspend();
        FamilyPass handler = new FamilyPass(supplied, select, descriptor, bind, texture, afterDraw);
        RenderPass proxy = (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[] {RenderPass.class}, handler);
        try {
            body.accept(proxy);
        } finally {
            if (handler.owned && handler.pass != null) {
                handler.pass.close();
            }
            ScenePass.leave();
        }
    }

    @Override
    public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] arguments) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                case "toString" -> "Vitrail family render pass";
                default -> throw new UnsupportedOperationException(name);
            };
        }
        if (name.equals("close")) {
            throw new IllegalStateException("The family owns the pass scope");
        }
        Object[] args = arguments == null ? new Object[0] : arguments.clone();
        if (name.equals("pushConstants")) {
            ByteBuffer source = ((ByteBuffer) args[0]).duplicate();
            args[0] = ByteBuffer.allocateDirect(source.remaining()).order(source.order()).put(source).flip();
        }
        Call call = new Call(method, args);
        boolean draw = name.startsWith("draw") || name.startsWith("multiDraw");
        if (this.pass == null && !draw) {
            this.pending.add(call);
            return null;
        }
        if (this.pass == null) {
            this.pipeline = this.select.get();
            RenderPassDescriptor selected = this.pipeline == null ? null : this.descriptor.get();
            this.owned = selected != null;
            this.pass = selected == null ? this.supplied
                    : GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(), selected);
            RenderSystem.bindDefaultUniforms(this.pass);
            for (Call queued : this.pending) {
                forward(queued);
            }
            this.pending.clear();
        }
        if (draw && this.pipeline != null) {
            this.bind.accept(this.pass, this.pipeline);
        }
        forward(call);
        if (draw && this.pipeline != null) {
            this.afterDraw.accept(this.pass, this.pipeline);
        }
        return null;
    }

    private void forward(Call call) throws Throwable {
        String name = call.method.getName();
        if (this.pipeline != null && name.equals("setPipeline")) {
            this.pass.setPipeline(PackPipelines.get(this.pipeline));
            return;
        }
        try {
            call.method.invoke(this.pass, call.args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
        if (this.pipeline != null && name.equals("setUniform") && call.args.length == 3
                && "Sampler0".equals(call.args[0])) {
            this.texture.accept((GpuTextureView) call.args[1], (GpuSampler) call.args[2]);
        }
    }

    private record Call(Method method, Object[] args) {
    }
}
