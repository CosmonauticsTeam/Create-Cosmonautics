package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class VectorThrusterScreenHelper {

    public static void openScreen(VectorThrusterBlockEntity blockEntity) {
        Minecraft.getInstance().setScreen(new VectorThrusterGuiScreen(blockEntity));
    }
}
