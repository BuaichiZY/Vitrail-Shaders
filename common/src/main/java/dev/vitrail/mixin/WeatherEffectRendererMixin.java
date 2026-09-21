package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.vitrail.render.FamilyPass;
import dev.vitrail.render.ScenePass;
import dev.vitrail.render.WeatherDraw;
import net.minecraft.client.renderer.WeatherEffectRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Routes the explicit 26.3 scene pass through the pack's weatherdraw attachments. */
@Mixin(WeatherEffectRenderer.class)
public abstract class WeatherEffectRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/client/renderer/state/level/WeatherRenderState;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", require = 1)
    private void vitrail$render(net.minecraft.client.renderer.state.level.WeatherRenderState state, RenderPass supplied, Operation<Void> original) {
        if (!WeatherDraw.draws()) { return; }
        FamilyPass.draw(supplied, () -> WeatherDraw.element(net.minecraft.client.renderer.RenderPipelines.WEATHER, ScenePass.color(), ScenePass.depth()),
                () -> WeatherDraw.descriptor(), WeatherDraw::bind, WeatherDraw::texture, (pass, pipeline) -> {},
                pass -> original.call(state, pass));
    }
}
