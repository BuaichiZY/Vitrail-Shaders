package dev.vitrail.render;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.vitrail.glsl.PackProgram;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.regex.Pattern;

/** Image access formats belong to the shader, independently of the sampled texture format. */
public final class ImageAccessFormats {
    private static final Map<RenderPipeline, Map<String, String>> PIPELINES = new WeakHashMap<>();
    private static final Pattern DECLARATION = Pattern.compile(
            "layout\\(([a-z0-9_]+)\\)\\s+(?:(?:restrict|readonly|writeonly|coherent|volatile)\\s+)*uniform\\s+[ui]?image\\w+\\s+(\\w+)\\s*;");

    private ImageAccessFormats() { }

    public static Map<String, String> read(String source) {
        Map<String, String> result = new HashMap<>();
        var matcher = DECLARATION.matcher(source);
        while (matcher.find()) result.putIfAbsent(matcher.group(2), matcher.group(1));
        return Map.copyOf(result);
    }

    static synchronized void note(RenderPipeline pipeline, PackProgram.Loaded loaded) {
        Map<String, String> formats = new HashMap<>();
        loaded.program().stages().values().forEach(unit -> formats.putAll(read(unit.text())));
        PIPELINES.put(pipeline, Map.copyOf(formats));
    }

    static synchronized void noteBeside(RenderPipeline pipeline, RenderPipeline original) {
        PIPELINES.put(pipeline, PIPELINES.getOrDefault(original, Map.of()));
    }

    public static synchronized String format(RenderPipeline pipeline, String name) {
        return PIPELINES.getOrDefault(pipeline, Map.of()).get(name);
    }
}
