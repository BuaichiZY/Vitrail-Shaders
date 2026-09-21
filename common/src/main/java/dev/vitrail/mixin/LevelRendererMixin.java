package dev.vitrail.mixin;

import dev.vitrail.render.PackChain;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps scene depth before the explicit always-on-top pass clears its depth attachment. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(method = "executeAlwaysOnTop", at = @At("HEAD"), require = 1)
    private void vitrail$scene(CallbackInfo callback) {
        PackChain.markSceneDepth();
    }
}
