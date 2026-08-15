package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.devce.rocketnautics.api.orbit.ColorPalette;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class DeepSpaceTexture implements PreparedTexture {
    private final DynamicTexture tex; // keep this just to make sure nothing garbage-collector shaped happens to it
    private final ResourceLocation id;

    public DeepSpaceTexture(DynamicTexture tex, ResourceLocation id) {
        this.tex = tex;
        this.id = id;
    }

    public static DeepSpaceTexture construct(int renderID, ColorPalette renderData) {
        Minecraft mc = Minecraft.getInstance();

        NativeImage singleImage = SkyHandler.composePlanetTexture(renderData);
        int w = singleImage.getWidth();
        int h = singleImage.getHeight();

        NativeImage image = new NativeImage(w * 6, h, false);
        for (int face = 0; face < 6; face++) {
            singleImage.copyRect(image, 0, 0, face * w, 0, w, h, false, false);
        }
        singleImage.close();

        DynamicTexture constructed = new DynamicTexture(image);
        ResourceLocation claimed = mc.getTextureManager().register("rocketnautics_deep_space_planet_" + renderID, constructed);
        constructed.setFilter(false, false);
        image.close();
        return new DeepSpaceTexture(constructed, claimed);
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }
}
