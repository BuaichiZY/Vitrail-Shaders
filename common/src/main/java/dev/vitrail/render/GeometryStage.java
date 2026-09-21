package dev.vitrail.render;

import dev.vitrail.glsl.GeometryFold;
import dev.vitrail.glsl.PackProgram;
import dev.vitrail.glsl.TranslatedUnit;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.Vitrail;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderType;
import com.mojang.renderpearl.frontend.shaders.GlslCompiler;
import com.mojang.renderpearl.backend.api.SpvModule;
import dev.vitrail.render.api.PackShaderSource;
import com.mojang.renderpearl.util.ShaderCompileException;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VK12;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The geometry stage a pack ships, bound between the vertex stage and the fragment stage of the
 * program it belongs to.
 * <p>
 * Iris links a {@code .gsh} whenever the pack ships one, building it as a third shader and passing
 * it to the link ({@code gl/program/ProgramBuilder.java:44-45,55}), so a pack that renames its
 * varyings in that stage is written for a pipeline of three. This engine bound two, and the
 * fragment stage
 * then asked for inputs nothing wrote: {@code IntermediaryShaderModule.rebind} refuses the module
 * over the names left unpaired, which took iterationT's terrain and its moon out of every frame.
 * <p>
 * <strong>The game has no geometry stage at all</strong>, so every step of the road has to be
 * opened by hand and each is opened at the narrowest point:
 * <ul>
 * <li>{@code ShaderType} carries VERTEX and FRAGMENT, and {@code GlslCompiler.createIntermediary}
 * reads the shaderc kind off it. The unit is handed in under VERTEX while {@link #begin} holds a
 * flag up for the length of that one call, which {@code GlslCompilerMixin} reads through
 * {@link #compiling} to ask shaderc for kind 3 and to key the module cache under this stage rather
 * than under the vertex one. What that road then does to the unit, the compiler's own two defines,
 * the debug information the compiler was or was not asked for, the local zeroes and the cache
 * itself, it does for this stage as it does for the two others. The pipeline's own defines are laid on here, which is
 * what {@code GeometryProgram} does for the vertex and fragment stages it compiles itself.</li>
 * <li>{@code GlslCompiler.compile} builds the bind group out of two modules and pairs them by
 * name. The third module joins the same group and is rebound between the two: the vertex stage's
 * outputs name the geometry stage's inputs, and the geometry stage's outputs name the fragment
 * stage's, which is the chain OpenGL links by name and Vulkan numbers by location.</li>
 * <li>{@code VulkanBindGroupLayout.create} writes {@code stageFlags} of vertex and fragment, which
 * names the stages a binding may be read from and leaves out the one between them. The geometry bit
 * joins them on the layouts of the pipelines that carry such a stage, and on no others.
 * iterationT's terrain stage reads {@code atlasSize}.</li>
 * <li>{@code VulkanRenderPipeline.compile} callocs two stage descriptions and puts a vertex and a
 * fragment into them. A third is put behind them for a pipeline that has one.</li>
 * </ul>
 * <p>
 * <strong>The device has to allow it.</strong> {@code geometryShader} is an optional Vulkan
 * feature, enabled in {@code VulkanBackendMixin} and answered here, and Metal has no such stage at
 * all. Where it is missing nothing is filed: a world program whose stage only hands each corner on
 * is drawn with that stage folded into its fragment stage, which {@link #fragment} answers, and any
 * other is set aside instead of drawn with its middle stage gone.
 * <p>
 * <strong>What this road does NOT reach.</strong> The clip depth is converted in the vertex
 * stage's epilogue, this engine rasterising with reversed Z where Iris runs against an OpenGL
 * volume, so a geometry stage reads a position already converted. One that passes
 * {@code gl_in[i].gl_Position} through, which is what the corpus writes, carries it untouched;
 * one that did arithmetic on the depth it reads back would be working in the wrong volume. Same
 * shape for the varyings this engine names for itself, the entity overlay colour among them: they
 * are emitted into the vertex and fragment stages off one union and a stage between the two would
 * not carry them, so such a program is refused by the game's own pairing rather than drawn wrong.
 * No program of the corpus does either.
 * <p>
 * <strong>Why the module in flight lives on the thread.</strong> A pipeline is built on the
 * render thread and on the pack-load worker both, and the two halves of the road, the compiler and
 * the pipeline, are two calls in a row on whichever thread is building. The pipeline being built
 * is carried with it so that a leftover from a build that threw between the two is recognised and
 * dropped rather than bound into the next pipeline that comes past.
 *
 * @see <a href="https://github.com/IrisShaders/Iris">Iris ProgramBuilder, LGPL-3.0</a>
 */
public final class GeometryStage {

	/** {@code VK_SHADER_STAGE_GEOMETRY_BIT}. */
	public static final int STAGE_BIT = VK12.VK_SHADER_STAGE_GEOMETRY_BIT;

	/**
	 * The stage each pipeline ships, weak on the pipeline for the reason {@link ShadowCompare}'s
	 * registry is: the entry describes the pipeline's lifetime and goes with it on a pack change.
	 */
	private static final Map<RenderPipeline, String> SHIPPED =
			Collections.synchronizedMap(new WeakHashMap<>());

	/** Whether anything is filed at all, so the compile road asks one flag before the map. */
	private static volatile boolean noted;

	private static volatile boolean served;

	private static final ThreadLocal<Boolean> COMPILING = new ThreadLocal<>();


	private GeometryStage() {
	}

	/**
	 * Whether the device gave {@code geometryShader}, answered by {@code VulkanBackendMixin} once
	 * the device is made. Off until it says otherwise, which is what anything with no device to
	 * ask gets, and which makes {@link #note} refuse rather than promise.
	 */
	public static void serve(boolean answer) {
		served = answer;
	}

	/**
	 * Files the geometry stage this program ships, which is the question the compile road asks
	 * back at every build. Nothing is filed for a program without one, which is nearly all of
	 * them, and nothing is filed on a device that refused the feature.
	 * <p>
	 * <strong>The answer is whether the program can be served at all</strong>, and it is false on
	 * exactly one road: the pack ships a stage and the device has not got the feature. Drawing it
	 * without would be a picture missing whatever that stage worked out per primitive, and a stage
	 * that only renames its varyings would be caught by the game's own pairing while one that
	 * passes them through would not, so the same pack would be refused on one card and quietly
	 * wrong on another. A full screen pass asks this and has no fallback, nothing else drawing a
	 * composite, so it says so and is drawn without the stage; no pack of the corpus writes one. A
	 * world program asks {@link #fragment} instead, which can fold the stage away.
	 *
	 * @param pipeline the pipeline this program's stages were built into
	 * @param path     the pack-side path of the program, for the line a refusal prints
	 * @param loaded   the translated program, whose geometry unit is taken if it has one
	 * @return whether this program can be drawn here
	 */
	public static boolean note(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return true;
		}

		if (!served) {
			Vitrail.logger().error("{} ships a geometry stage and this device has no "
					+ "geometryShader feature, so the program is set aside rather than drawn "
					+ "without the stage", path);

			return false;
		}

		file(pipeline, unit);

		return true;
	}

	/**
	 * The fragment stage a world program is drawn with, its geometry stage filed on the way, or
	 * null where the program cannot be drawn here.
	 * <p>
	 * The program's own fragment stage wherever it ships no geometry stage or the device runs one,
	 * filed as {@link #note} files it. On a device without {@code geometryShader} the stage goes to
	 * {@link GeometryFold}: one that only hands each corner on comes back folded into the fragment
	 * stage, which is compiled in place of both and pairs with the vertex stage's outputs directly,
	 * and any other is refused. The program is set aside on that refusal and the game's own shader
	 * draws instead, which is what this engine does with every other shape it cannot serve.
	 * <p>
	 * The fold reads the stage the way the compiler will, after the preprocessor has settled every
	 * branch under the pipeline's defines. iterationT chooses between working its texture size out of
	 * the triangle and taking a constant by a {@code #if}, and a reading with both branches standing
	 * could not tell which of the two it was folding.
	 *
	 * @param pipeline the pipeline this program's stages were built into
	 * @param path     the pack-side path of the program, for the line the answer prints
	 * @param loaded   the translated program
	 * @return the text to compile the fragment stage from, or null to set the program aside
	 */
	public static String fragment(RenderPipeline pipeline, String path, PackProgram.Loaded loaded) {
		String fragment = loaded.program().stages().get(ProgramStage.FRAGMENT).text();
		TranslatedUnit unit = loaded.program().stages().get(ProgramStage.GEOMETRY);
		if (unit == null) {
			return fragment;
		}

		if (served) {
			file(pipeline, unit);

			return fragment;
		}

		GeometryFold.Result fold = fold(pipeline, path, unit.text(), fragment);
		if (!fold.folded()) {
			Vitrail.logger().error("{} ships a geometry stage and this device has no "
					+ "geometryShader feature, and the stage {}, so the program is set aside rather "
					+ "than drawn without it", path, fold.refusal());

			return null;
		}

		Vitrail.logger().info("{} ships a geometry stage and this device has no geometryShader "
				+ "feature; the stage only hands each corner on, so it is folded into the fragment "
				+ "stage", path);

		return fold.fragment();
	}

	private static void file(RenderPipeline pipeline, TranslatedUnit unit) {
		SHIPPED.put(pipeline, unit.text());
		noted = true;
	}

	/**
	 * The geometry stage preprocessed under the pipeline's defines and handed to the fold. A
	 * compiler of its own for the one call: this runs for the few programs shipping a stage, on a
	 * device that cannot run one, once per build.
	 */
	private static GeometryFold.Result fold(RenderPipeline pipeline, String path, String geometry,
			String fragment) {
		long compiler = Shaderc.shaderc_compiler_initialize();
		if (compiler == 0L) {
			return GeometryFold.Result.refused("could not be read, shaderc giving no compiler");
		}

		// On the heap rather than through the overloads taking text, which encode on the thread's
		// memory stack: a translated stage carries the pack's whole settings header and runs to tens
		// of kilobytes, against a stack of sixty-four.
		ByteBuffer source = MemoryUtil.memUTF8(
				geometry, false);
		ByteBuffer name = MemoryUtil.memUTF8(path);
		ByteBuffer entry = MemoryUtil.memUTF8("main");
        long options = Shaderc.shaderc_compile_options_initialize();
        pipeline.getShaderDefines().values().forEach((key, value) ->
                Shaderc.shaderc_compile_options_add_macro_definition(options, key, value));
        pipeline.getShaderDefines().flags().forEach(flag ->
                Shaderc.shaderc_compile_options_add_macro_definition(options, flag, ""));
		try {
			long result = Shaderc.shaderc_compile_into_preprocessed_text(compiler, source,
					Shaderc.shaderc_glsl_geometry_shader, name, entry, options);
			if (result == 0L) {
				return GeometryFold.Result.refused("could not be read, shaderc giving no result");
			}

			try {
				ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
				if (Shaderc.shaderc_result_get_compilation_status(result)
						!= Shaderc.shaderc_compilation_status_success || bytes == null) {
					return GeometryFold.Result.refused("did not preprocess: "
							+ Shaderc.shaderc_result_get_error_message(result));
				}

				return GeometryFold.fold(StandardCharsets.UTF_8.decode(bytes).toString(), fragment);
			} finally {
				Shaderc.shaderc_result_release(result);
			}
		} finally {
			MemoryUtil.memFree(entry);
			MemoryUtil.memFree(name);
			MemoryUtil.memFree(source);
			Shaderc.shaderc_compile_options_release(options);
			Shaderc.shaderc_compiler_release(compiler);
		}
	}

	/**
	 * Files a rebuilt variant beside the pipeline it was rebuilt from. A reshape swaps the vertex
	 * layout and nothing the middle stage depends on, so the text is the base's.
	 */
	public static void noteBeside(RenderPipeline variant, RenderPipeline base) {
		String text = SHIPPED.get(base);
		if (text != null) {
			SHIPPED.put(variant, text);
		}
	}

	/** Whether any pipeline ships one, asked before the per-pipeline question is worth asking. */
	public static boolean noted() {
		return noted;
	}

	/** Compiles an optional geometry stage through the same RenderPearl compiler as other stages. */
	public static SpvModule begin(GlslCompiler compiler, RenderPipeline pipeline) throws ShaderCompileException {
		String text = SHIPPED.get(pipeline);
		if (text == null) {
			return null;
		}
		COMPILING.set(Boolean.TRUE);
		try {
			PackShaderSource noIncludes = (id, type) -> null;
			return compiler.compileToSpv(pipeline.getLocation().toDebugFileName() + "/gsh", text,
					ShaderType.VERTEX, pipeline.getShaderDefines(), noIncludes);
		} finally {
			COMPILING.remove();
		}
	}

	/** Selects shaderc's geometry stage while compiling that source. */
	public static boolean compiling() {
		return Boolean.TRUE.equals(COMPILING.get());
	}

	/** Called when the client shuts down. */
	public static void close() {
		SHIPPED.clear();
		noted = false;
	}
}
