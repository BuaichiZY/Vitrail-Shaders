package dev.vitrail.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Suspends the shared 26.3 scene pass while a shader pack records a pass of its own.
 * State is retained across resumptions; attachment clear operations run only once.
 */
public final class ScenePass implements InvocationHandler {
    public static @Nullable GpuTextureView colorOverride;
    public static @Nullable GpuTextureView depthOverride;
    private static @Nullable ScenePass recording;
    private static boolean opening;
    private static @Nullable ScenePass drawing;
    private final CommandEncoder encoder;
    private final RenderPassDescriptor descriptor;
    private final Map<String, Call> state = new LinkedHashMap<>();
    private final List<Call> groups = new ArrayList<>();
    private @Nullable RenderPass real;
    private @Nullable GpuTextureView recordedColor;
    private @Nullable GpuTextureView recordedDepth;
    private boolean used;
    private boolean closed;

    private ScenePass(CommandEncoder encoder, RenderPassDescriptor descriptor) {
        this.encoder = encoder;
        this.descriptor = descriptor;
    }

    /** Only game scene passes are deferred; pack passes remain ordinary GPU passes. */
    public static boolean wants(RenderPassDescriptor descriptor) {
        if (opening || GeometryHold.opening() || !PackChain.drawingPack()) {
            return false;
        }
        return switch (descriptor.label().get()) {
            case "Sky", "Main", "Solid", "Item in hand", "See through features", "Always on top features" -> true;
            default -> false;
        };
    }

    public static RenderPass create(CommandEncoder encoder, RenderPassDescriptor descriptor) {
        ScenePass handler = new ScenePass(encoder, descriptor);
        // Clears belong at pass creation, before any replacement pack draws.
        if (handler.hasClear()) {
            try {
                handler.resume();
                suspend();
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Throwable e) {
                throw new IllegalStateException("Could not clear scene attachments", e);
            }
        }
        return (RenderPass) Proxy.newProxyInstance(RenderPass.class.getClassLoader(),
                new Class<?>[] {RenderPass.class}, handler);
    }

    /** A logical main-target pass for hand and shadow feature submissions. */
    public static RenderPass features() {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var builder = RenderPassDescriptor.builder(() -> "Vitrail feature submissions")
                .withColorAttachment(target.getColorTextureView());
        if (target.getDepthTextureView() != null) {
            builder.withDepthAttachment(target.getDepthTextureView());
        }
        RenderPass pass = create(RenderSystem.getDevice().createCommandEncoder(), builder.build());
        RenderSystem.bindDefaultUniforms(pass);
        return pass;
    }

    public static void enter(RenderPass pass) {
        drawing = Proxy.isProxyClass(pass.getClass()) && Proxy.getInvocationHandler(pass) instanceof ScenePass scene
                ? scene : null;
    }

    public static void leave() {
        drawing = null;
    }

    public static @Nullable GpuTextureView color() {
        if (colorOverride != null) {
            return colorOverride;
        }
        if (drawing != null && !drawing.descriptor.colorAttachments().isEmpty()) {
            var attachment = drawing.descriptor.colorAttachments().getFirst();
            return attachment == null ? null : attachment.textureView();
        }
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
    }

    public static @Nullable GpuTextureView depth() {
        if (depthOverride != null) {
            return depthOverride;
        }
        if (drawing != null) {
            var attachment = drawing.descriptor.depthAttachment();
            return attachment == null ? null : attachment.textureView();
        }
        return Minecraft.getInstance().gameRenderer.mainRenderTarget().getDepthTextureView();
    }

    public static boolean onMainTarget() {
        if (drawing == null) {
            return false;
        }
        var colors = drawing.descriptor.colorAttachments();
        return colors.size() == 1 && colors.getFirst() != null
                && colors.getFirst().textureView() == Minecraft.getInstance().gameRenderer.mainRenderTarget().getColorTextureView();
    }

    /** Must precede foreign passes, transfers, and attachment changes. */
    public static void suspend() {
        ScenePass scene = recording;
        recording = null;
        if (scene != null && scene.real != null) {
            for (int i = scene.groups.size(); i > 0; i--) {
                scene.real.popDebugGroup();
            }
            scene.real.close();
            scene.real = null;
        }
    }

    private RenderPass resume() throws Throwable {
        if (this.real != null && (this.recordedColor != colorOverride || this.recordedDepth != depthOverride)) {
            suspend();
        }
        if (this.real == null) {
            suspend();
            GeometryHold.flush(() -> "resuming the shared scene pass");
            var colors = new ArrayList<>(this.descriptor.colorAttachments());
            for (int i = 0; i < colors.size(); i++) {
                var attachment = colors.get(i);
                if (attachment != null) {
                    colors.set(i, new RenderPassDescriptor.Attachment<>(
                            i == 0 && colorOverride != null ? colorOverride : attachment.textureView(),
                            this.used ? Optional.empty() : attachment.clearValue()));
                }
            }
            var depth = this.descriptor.depthAttachment();
            if (depth != null) {
                depth = new RenderPassDescriptor.Attachment<>(depthOverride != null ? depthOverride : depth.textureView(),
                        this.used ? OptionalDouble.empty() : depth.clearValue());
            }
            opening = true;
            try {
                this.real = this.encoder.createRenderPass(new RenderPassDescriptor(this.descriptor.label(),
                        colors, depth, this.descriptor.renderArea()));
            } finally {
                opening = false;
            }
            this.used = true;
            this.recordedColor = colorOverride;
            this.recordedDepth = depthOverride;
            recording = this;
            Call pipeline = this.state.get("setPipeline");
            if (pipeline != null) {
                pipeline.invoke(this.real);
            }
            for (Call call : this.state.values()) {
                if (call != pipeline) {
                    call.invoke(this.real);
                }
            }
            for (Call call : this.groups) {
                call.invoke(this.real);
            }
        }
        return this.real;
    }

    @Override
    public @Nullable Object invoke(Object proxy, Method method, Object @Nullable [] arguments) throws Throwable {
        String name = method.getName();
        if (method.getDeclaringClass() == Object.class) {
            return switch (name) {
                case "toString" -> "Vitrail scene pass: " + this.descriptor.label().get();
                case "hashCode" -> System.identityHashCode(proxy);
                case "equals" -> proxy == arguments[0];
                default -> throw new UnsupportedOperationException(name);
            };
        }
        if (name.equals("close")) {
            if (!this.closed) {
                if (!this.used && hasClear()) {
                    resume();
                }
                if (recording == this) {
                    suspend();
                }
                this.closed = true;
                this.state.clear();
                this.groups.clear();
            }
            return null;
        }
        if (this.closed) {
            throw new IllegalStateException("Scene pass is closed");
        }
        Object[] args = arguments == null ? new Object[0] : arguments.clone();
        if (name.equals("pushConstants")) {
            ByteBuffer source = ((ByteBuffer) args[0]).duplicate();
            args[0] = ByteBuffer.allocateDirect(source.remaining()).order(source.order()).put(source).flip();
        }
        Call call = new Call(method, args);
        if (name.startsWith("set") || name.equals("pushConstants") || name.endsWith("Scissor")) {
            if (name.equals("setPipeline") && (!this.state.containsKey(name)
                    || this.state.get(name).args[0] != args[0])) {
                this.state.remove("pushConstants");
            }
            String key = name.equals("setUniform") || name.equals("setVertexBuffer") ? name + ":" + args[0]
                    : name.endsWith("Scissor") ? "scissor" : name;
            this.state.remove(key);
            this.state.put(key, call);
            if (this.real != null) {
                call.invoke(this.real);
            }
        } else if (name.equals("pushDebugGroup")) {
            this.groups.add(call);
            if (this.real != null) {
                call.invoke(this.real);
            }
        } else if (name.equals("popDebugGroup")) {
            this.groups.removeLast();
            if (this.real != null) {
                call.invoke(this.real);
            }
        } else {
            call.invoke(resume());
        }
        return null;
    }

    private boolean hasClear() {
        return this.descriptor.colorAttachments().stream().anyMatch(a -> a != null && a.clearValue().isPresent())
                || (this.descriptor.depthAttachment() != null && this.descriptor.depthAttachment().clearValue().isPresent());
    }

    private record Call(Method method, Object[] args) {
        private void invoke(RenderPass pass) throws Throwable {
            try {
                this.method.invoke(pass, this.args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
