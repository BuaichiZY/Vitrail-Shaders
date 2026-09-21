package dev.vitrail.mixin;

import dev.vitrail.render.GeometryHold;
import dev.vitrail.render.SkyDraw;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.DynamicGpuData;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.SkyRenderer;
import net.minecraft.world.level.MoonPhase;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Supplier;

/**
 * Draws the sky with the programs the pack ships for it, instead of the game's own shaders.
 * <p>
 * The sky is the one piece of the world that opens its own render passes: {@code SkyRenderer} makes
 * one per element, sets a pipeline of the game's and draws a buffer built once at startup. So the
 * hook is not the one the entities will use, and it is smaller: the pipeline is swapped where it is
 * set, and the pass is replaced where it is opened so that the pack's own colour targets are what
 * the piece is drawn into. The two answers are taken on one call of one wrap and cannot part
 * company: a pipeline carries one colour state per attachment the descriptor names, and setting one
 * against a pass built for the other throws by name in the middle of the sky. The two arguments
 * dropped along the way are the clear colour and the clear depth, and all eight methods pass both
 * empty.
 * <p>
 * <strong>An element is recognised by the label the game gives its own pass</strong>, which is the
 * first argument of the call wrapped below. That is what lets one wrap serve eight methods without a
 * table of method names to keep in step with the game: a method whose label this engine has no
 * element for prepares nothing and draws exactly as it did.
 * <p>
 * <strong>The eight are two branches and not one list.</strong> {@code LevelRenderer.addSkyPass}
 * takes the End's branch or the other and never both, at {@code :344-349} and {@code :350-361}: a
 * place under the End's skybox reaches two of these methods and a place under any other reaches the
 * remaining six, and the ones a place never calls cost it nothing.
 * <p>
 * <strong>Each wrap states how many calls it has to bind, and the number is not decoration.</strong>
 * Mixin counts an injector's matches as one total over every method it names, so a wrap listing
 * eight methods and requiring one is satisfied by one of the eight and lets the other seven die
 * without a word. The counts written below are what the game's bytecode holds, one call in each
 * method named, so a method that loses its call refuses the launch instead of the picture.
 * <p>
 * <strong>One of the pack's answers is not a shader at all.</strong> Two of these pieces, the sun
 * and the moon, can be refused outright in {@code shaders.properties}, and a refusal is cancelled at
 * the head of the method rather than served with a program of ours, because the pack has drawn that
 * piece itself.
 * <p>
 * <strong>The order of the wraps is the whole design.</strong> The pass is opened after the game has
 * pushed the model view for this element, so the matrix is final by then and the sun is where the
 * game put it; compiling a pipeline or clearing a target has to happen before the pass exists, which
 * is why the preparation hangs off the opening and not off the head of the method. The texture goes
 * past next, and the block and the samplers are bound after that, once everything the bind needs is
 * known. The draw comes last, and it is the one place a piece of geometry the game has none of can
 * be added to a pass the game built.
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMixin {
    private Matrix4f vitrail$modelView;
    private Vector4f vitrail$colour;

    @Inject(method = "renderSunMoonAndStars", at = @At(value = "INVOKE", ordinal = 0,
            shift = At.Shift.AFTER, target = "Lcom/mojang/blaze3d/vertex/PoseStack;rotateDegrees(Lcom/mojang/math/Axis;F)V"), require = 1)
    private void vitrail$tilt(RenderPass pass, PoseStack pose, float sun, float moon, float stars,
            MoonPhase phase, float rain, float brightness, CallbackInfo callback) {
        pose.rotateDegrees(Axis.ZP, SkyDraw.sunPathRotation());
    }

    private void vitrail$element(String label, RenderPass supplied, java.util.function.Consumer<RenderPass> body) {
        if (!SkyDraw.draws(label)) {
            return;
        }
        dev.vitrail.render.FamilyPass.draw(supplied,
                () -> SkyDraw.element(label, this.vitrail$modelView, this.vitrail$colour),
                () -> SkyDraw.descriptor(dev.vitrail.render.ScenePass.color(), dev.vitrail.render.ScenePass.depth()),
                SkyDraw::bind, SkyDraw::texture,
                (pass, pipeline) -> { if (label.equals("Sky disc")) { SkyDraw.horizon(pass, pipeline); } }, body);
    }
	@WrapOperation(
			method = {"renderSkyDisc", "renderDarkDisc", "renderStars", "renderSunriseAndSunset", "renderSun",
					"renderMoon", "renderEndFlash"},
			require = 7,
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform("
							+ "Lorg/joml/Matrix4f;"
							+ "Lorg/joml/Vector4f;"
							+ ")Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private GpuBufferSlice vitrail$transform(DynamicGpuData uniforms, Matrix4f modelView,
			Vector4f colour, Operation<GpuBufferSlice> original) {
		this.vitrail$modelView = modelView;
		this.vitrail$colour = colour;

		return original.call(uniforms, modelView, colour);
	}

	/**
	 * The same for the End's sky, which is the one pass of the eight that names no colour.
	 * <p>
	 * A wrap of its own and not another name on the one above, because the call is a different
	 * overload: {@code renderEndSky} passes a matrix alone, and {@code DynamicGpuData} fills the
	 * modulator in with its own opaque white. Written out here rather than left at whatever the last
	 * pass wrote, because the modulator is what a pack reads as {@code gl_Color} where the mesh
	 * carries none and half of it where the mesh carries one, and this mesh carries one.
	 */
	@WrapOperation(
			method = "renderEndSky",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/client/renderer/DynamicGpuData;writeTransform("
							+ "Lorg/joml/Matrix4f;"
							+ ")Lcom/mojang/renderpearl/api/buffers/GpuBufferSlice;"))
	private GpuBufferSlice vitrail$endTransform(DynamicGpuData uniforms, Matrix4f modelView,
			Operation<GpuBufferSlice> original) {
		this.vitrail$modelView = modelView;
		this.vitrail$colour = new Vector4f(1.0F, 1.0F, 1.0F, 1.0F);

		return original.call(uniforms, modelView);
	}

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderSkyDisc", require = 1)
    private void vitrail$renderSkyDisc(RenderPass supplied, org.joml.Vector3fc color, Operation<Void> original) {
        vitrail$element("Sky disc", supplied, pass -> original.call(pass, color));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderDarkDisc", require = 1)
    private void vitrail$renderDarkDisc(RenderPass supplied, Operation<Void> original) {
        vitrail$element("Sky dark", supplied, pass -> original.call(pass));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderStars", require = 1)
    private void vitrail$renderStars(RenderPass supplied, float brightness, PoseStack pose, Operation<Void> original) {
        vitrail$element("Stars", supplied, pass -> original.call(pass, brightness, pose));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderSunriseAndSunset", require = 1)
    private void vitrail$renderSunriseAndSunset(RenderPass supplied, PoseStack pose, float angle, org.joml.Vector4fc color, Operation<Void> original) {
        vitrail$element("Sunrise sunset", supplied, pass -> original.call(pass, pose, angle, color));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderSun", require = 1)
    private void vitrail$renderSun(RenderPass supplied, float brightness, PoseStack pose, Operation<Void> original) {
        vitrail$element("Sky sun", supplied, pass -> original.call(pass, brightness, pose));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderMoon", require = 1)
    private void vitrail$renderMoon(RenderPass supplied, MoonPhase phase, float brightness, PoseStack pose, Operation<Void> original) {
        vitrail$element("Sky moon", supplied, pass -> original.call(pass, phase, brightness, pose));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderEndSky", require = 1)
    private void vitrail$renderEndSky(RenderPass supplied, Operation<Void> original) {
        vitrail$element("End sky", supplied, pass -> original.call(pass));
    }

    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "renderEndFlash", require = 1)
    private void vitrail$renderEndFlash(RenderPass supplied, PoseStack pose, float intensity, float x, float y, Operation<Void> original) {
        vitrail$element("End flash", supplied, pass -> original.call(pass, pose, intensity, x, y));
    }
}
