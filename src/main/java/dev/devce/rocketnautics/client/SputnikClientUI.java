package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.client.ui.imgui.SputnikImGuiScreen;
import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import net.minecraft.client.Minecraft;

public final class SputnikClientUI {

    private SputnikClientUI() {
    }

    public static void openNodeScreen(SputnikBlockEntity blockEntity) {
        if (blockEntity == null) return;
        Minecraft.getInstance().setScreen(new SputnikImGuiScreen(
                blockEntity.getBlockPos(),
                blockEntity.getSputnikId(),
                blockEntity.getGraph()
        ));
    }
}
