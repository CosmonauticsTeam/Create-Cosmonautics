package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.function.Function;

@FunctionalInterface
@OnlyIn(Dist.CLIENT)
public interface PreparedTexture {

    default void retire() {
        Minecraft.getInstance().getTextureManager().release(getId());
    }

    ResourceLocation getId();

    default ResourceLocation getNormalId() {
        return null;
    }

    default void setShaderTexture() {
        RenderSystem.setShaderTexture(0, getId());
    }

    default RenderType attachType(Function<ResourceLocation, RenderType> renderType) {
        return renderType.apply(getId());
    }
}
