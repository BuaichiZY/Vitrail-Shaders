package dev.vitrail.pack.source;

import dev.vitrail.glsl.ProgramTranslator;
import dev.vitrail.pack.model.ProgramStage;
import dev.vitrail.render.ImageAccessFormats;
import dev.vitrail.pack.model.ImageInformation;
import dev.vitrail.pack.texture.CustomImages;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;

/** Small source-only regressions; no game instance or downloaded shader pack is needed. */
public final class CompatibilityRegression {
    public static void main(String[] args) throws Exception {
        var lines = new ArrayList<String>();
        var settings = Map.of("SIZE", "64");
        PropertiesFile.walk(List.of("#if SIZE == 64", "#define WIDTH 192", "#else",
                "#define WIDTH 384", "#endif", "#define ALIAS WIDTH", "image.test=ALIAS",
                "#undef WIDTH", "#ifdef WIDTH", "bad=true", "#endif"), settings, lines::add);
        require(lines.equals(List.of("image.test=192")), "conditional local properties macros: " + lines);
        require(settings.equals(Map.of("SIZE", "64")), "caller settings changed");
        var vertex = unit("test.vsh", "in vec3 vaPosition;\nvoid main(){gl_Position=vec4(vaPosition,1.0);}");
        var fragment = unit("test.fsh", "#define TEX_ALIAS TEX_REAL\n#define TEX_REAL colortex0\n"
                + "uniform sampler2D TEX_ALIAS;\nlayout(r32ui) uniform uimage2D depthImage;\n"
                + "void main(){gl_FragData[0]=texture(TEX_ALIAS,vec2(0.5));imageStore(depthImage,ivec2(0),uvec4(1));}");
        var program = ProgramTranslator.translate(Map.of(ProgramStage.VERTEX, vertex,
                ProgramStage.FRAGMENT, fragment), true);
        String v = program.stages().get(ProgramStage.VERTEX).text();
        String f = program.stages().get(ProgramStage.FRAGMENT).text();
        require(v.contains("#define vaPosition vec3(Position)"), "fullscreen position is not backed by vertices");
        require(f.contains("uniform sampler2D colortex0;"), "macro sampler was not resolved");
        require(v.contains("layout(r32ui) uniform uimage2D depthImage;"), "borrowed image format lost");
        require(f.contains("layout(r32ui) uniform uimage2D depthImage;"), "declared image format lost");
        require(ImageAccessFormats.read(f).get("depthImage").equals("r32ui"), "access format metadata lost");
        var images = new ArrayList<ImageInformation>();
        require(ImageInformation.parse("light_img", "light_sampler RGBA RGBA16F HALF_FLOAT false false 8 8 8", Map.of(), images) == null,
                "image fixture rejected");
        CustomImages.install(new ImageInformation.Reading(images, List.of()));
        try {
            var compute = dev.vitrail.glsl.GlslTranslator.translate(unit("test.csh",
                    "writeonly uniform image3D light_img;\nvoid main(){imageStore(light_img,ivec3(0),vec4(1));}"), ProgramStage.COMPUTE);
            require(compute.text().contains("writeonly uniform image3D light_img;"), "write-only image lost");
            require(!compute.text().contains("layout(rgba16f)"), "format invented for formatless write-only image");
        } finally {
            CustomImages.clear();
        }
        var atlas = dev.vitrail.render.DynamicAtlas.parse("minecraft:textures/atlas/blocks_s.png");
        require(atlas != null && atlas.base().toString().equals("minecraft:textures/atlas/blocks.png")
                && atlas.map() == dev.vitrail.render.pbr.PbrMap.SPECULAR, "specular atlas reference");
        require(dev.vitrail.render.DynamicAtlas.parse("minecraft:textures/block/stone.png") == null,
                "ordinary resource mistaken for atlas");
        colourImages();
        System.out.println("Compatibility regressions passed");
    }
    private static void colourImages() throws Exception {
        var directory = java.nio.file.Files.createTempDirectory("vitrail-image-regression-");
        try {
            var shaders = java.nio.file.Files.createDirectory(directory.resolve("shaders"));
            java.nio.file.Files.writeString(shaders.resolve("composite.fsh"),
                    "#version 430\n#define STORE colorimg9\nlayout(rgba16f) writeonly uniform image2D STORE;\n"
                    + "/* DRAWBUFFERS:0 */\nvoid main(){imageStore(STORE,ivec2(0),vec4(1));gl_FragData[0]=vec4(1);}\n");
            java.nio.file.Files.writeString(shaders.resolve("composite.vsh"),
                    "#version 430\nlayout(rgba16f) readonly uniform image2D colorimg8;\nvoid main(){gl_Position=gl_Vertex;}\n");
            try (var source = ShaderPackSource.open(directory)) {
                var plan = dev.vitrail.pack.target.TargetPlan.build(source,
                        new dev.vitrail.pack.option.OptionIndex.Reader().index(),
                        dev.vitrail.pack.option.SettingSet.defaults(), ShaderProperties.parse(source), "world0");
                require(plan.storageTargets().containsAll(java.util.Set.of(8, 9)), "graphics images not allocated with storage usage");
                require(plan.allocated().containsAll(java.util.Set.of(8, 9)), "macro or vertex image allocation lost");
                var samplers = dev.vitrail.pack.target.SamplerPlan.of(List.of("colorimg9"),
                        Map.of("colorimg9", "image2D"), plan, "composite");
                var binding = samplers.binding("colorimg9");
                require(binding.kind() == dev.vitrail.pack.target.SamplerPlan.Kind.COLOUR_IMAGE
                        && binding.index() == 9 && !binding.defaulted(), "storage image bound as sampled scene");
                require(binding.side() == plan.schedule().step("composite").orElseThrow().read(9), "storage image on wrong ping-pong side");
            }
        } finally {
            try (var paths = java.nio.file.Files.walk(directory)) {
                for (var path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) java.nio.file.Files.delete(path);
            }
        }
    }
    private static IncludeExpander.ExpandedUnit unit(String name, String text) {
        var lines = text.lines().toList();
        var live = new BitSet();
        live.set(0, lines.size());
        return new IncludeExpander.ExpandedUnit(name, lines, "430 compatibility", ExpansionStats.NONE, live, Map.of());
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }
}
