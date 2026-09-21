package dev.vitrail.mixin.access;

import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * The backend behind an encoder wrapper, which is where a blit can be recorded. The wrapper is a
 * new object every call and forwards; the backend is the one that holds the command buffer.
 */
@Mixin(com.mojang.renderpearl.frontend.FrontendCommandEncoder.class)
public interface CommandEncoderAccessor {

	@Invoker("backend")
	CommandEncoderBackend vitrail$backend();
}
