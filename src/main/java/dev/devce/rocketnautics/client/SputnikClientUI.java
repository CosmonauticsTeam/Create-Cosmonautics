package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.network.SputnikNodeSyncPayload;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.client.ui.WNodeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

public class SputnikClientUI {
    public static void openNodeScreen(SputnikBlockEntity blockEntity) {
        WGraph editableGraph = new WGraph();
        editableGraph.setContext(blockEntity);
        if (blockEntity.getLevel() != null) {
            editableGraph.setRegistries(blockEntity.getLevel().registryAccess());
        }
        editableGraph.load(blockEntity.graph.save());
        editableGraph.setContext(blockEntity);

        Minecraft.getInstance().setScreen(new WNodeScreen(
            Component.literal("Flight Computer"),
            editableGraph,
            (tag) -> PacketDistributor.sendToServer(new SputnikNodeSyncPayload(blockEntity.getBlockPos(), tag)),
            null
        ));
    }
}
