package dev.vitrail.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.vitrail.render.pbr.PbrAtlases;
import dev.vitrail.render.pbr.PbrMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.Identifier;

/** Runtime atlas references must follow resource reloads instead of being decoded as PNG files. */
public record DynamicAtlas(Identifier base, PbrMap map) {
    public static DynamicAtlas parse(String path) {
        Identifier id = Identifier.tryParse(path);
        if (id == null || !id.getPath().startsWith("textures/atlas/") || !id.getPath().endsWith(".png")) return null;
        String name = id.getPath();
        for (PbrMap map : PbrMap.values()) {
            String suffix = map.suffix() + ".png";
            if (name.endsWith(suffix)) {
                return new DynamicAtlas(Identifier.fromNamespaceAndPath(id.getNamespace(),
                        name.substring(0, name.length() - suffix.length()) + ".png"), map);
            }
        }
        return new DynamicAtlas(id, null);
    }

    GpuTextureView view() {
        var texture = Minecraft.getInstance().getTextureManager().getTexture(this.base);
        if (!(texture instanceof TextureAtlas)) return null;
        GpuTextureView baseView = texture.getTextureView();
        if (this.map == null) return baseView;
        GpuTextureView material = PbrAtlases.view(baseView, this.map);
        return material == null ? ConstantTextures.of(RenderSystem.getDevice()).flat(this.map) : material;
    }
}
