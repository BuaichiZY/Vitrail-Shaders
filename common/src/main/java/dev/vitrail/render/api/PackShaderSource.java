package dev.vitrail.render.api;

import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/** A pack source whose includes have already been expanded by the pack loader. */
@FunctionalInterface
public interface PackShaderSource extends ShaderSource {

	@Nullable String get(Identifier id, ShaderType type);

	@Override
	default @Nullable String getShader(Identifier id, ShaderType type) {
		return get(id, type);
	}

	@Override
	default @Nullable CachedIncludeSource getInclude(Identifier id) {
		return null;
	}

	@Override
	default void close() {
		// This source owns Java strings only, with no native include allocations.
	}
}
