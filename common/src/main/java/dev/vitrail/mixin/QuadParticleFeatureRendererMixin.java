package dev.vitrail.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import dev.vitrail.render.GeometryHold;
import dev.vitrail.render.ParticleDraw;
import dev.vitrail.render.ScenePass;
import java.util.List;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.oit.OitStage;
import org.spongepowered.asm.mixin.Mixin;

/** Draws particle groups into their pack attachments, with the supplied game pass as fallback. */
@Mixin(QuadParticleFeatureRenderer.class)
public abstract class QuadParticleFeatureRendererMixin {
    @WrapMethod(method = "executeGroup", require = 1)
    private void vitrail$group(FeatureFrameContext context, OitStage stage, RenderPass supplied, int groupIndex,
            List<QuadParticleFeatureRenderer.Submit> submits, boolean strictlyOrdered, Operation<Void> original) {
        ScenePass.enter(supplied);
        try {
            if (stage != null || submits.isEmpty()) {
                original.call(context, stage, supplied, groupIndex, submits, strictlyOrdered);
                return;
            }
            ScenePass.suspend();
            var pipeline = ParticleDraw.group(submits.getFirst().translucent(), ScenePass.color(), ScenePass.depth());
            var descriptor = pipeline == null ? null : ParticleDraw.descriptor();
            if (descriptor == null) {
                ParticleDraw.opened(supplied);
                original.call(context, stage, supplied, groupIndex, submits, strictlyOrdered);
            } else {
                try (RenderPass pass = GeometryHold.open(RenderSystem.getDevice().createCommandEncoder(), descriptor)) {
                    ParticleDraw.opened(pass);
                    original.call(context, stage, pass, groupIndex, submits, strictlyOrdered);
                }
            }
        } finally {
            ParticleDraw.endGroup();
            ScenePass.leave();
        }
    }
}
