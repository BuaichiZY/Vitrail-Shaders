package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.vitrail.render.FamilyPass;
import dev.vitrail.render.ScenePass;
import dev.vitrail.render.CloudDraw;
import net.minecraft.client.renderer.CloudRenderer;
import org.spongepowered.asm.mixin.Mixin;

/** Routes the explicit 26.3 scene pass through the pack's clouddraw attachments. */
@Mixin(CloudRenderer.class)
public abstract class CloudRendererMixin {
    @WrapMethod(method = "render(Lnet/minecraft/client/CloudStatus;Lcom/mojang/renderpearl/api/commands/RenderPass;)V", require = 1)
    private void vitrail$render(net.minecraft.client.CloudStatus cloudStatus, RenderPass supplied, Operation<Void> original) {
        FamilyPass.draw(supplied, () -> CloudDraw.pipeline(cloudStatus == net.minecraft.client.CloudStatus.FANCY),
                () -> CloudDraw.descriptor(ScenePass.color(), ScenePass.depth()), CloudDraw::bind, (view, sampler) -> {}, (pass, pipeline) -> {},
                pass -> original.call(cloudStatus, pass));
    }
}
