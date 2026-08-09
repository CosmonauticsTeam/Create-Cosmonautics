package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.network.SputnikNodeSyncPayload;
import dev.devce.websnodelib.api.WGraph;
import dev.devce.websnodelib.client.ui.WNodeScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SputnikClientUI {
    private static final Map<String, CompoundTag> CLIENT_GRAPH_CACHE = new HashMap<>();

    public static void openNodeScreen(SputnikBlockEntity blockEntity) {
        CompoundTag serverGraph = blockEntity.graph.save();
        CompoundTag initialGraph = chooseInitialGraph(blockEntity, serverGraph);

        WGraph editableGraph = new WGraph();
        editableGraph.setContext(blockEntity);
        if (blockEntity.getLevel() != null) {
            editableGraph.setRegistries(blockEntity.getLevel().registryAccess());
        }
        editableGraph.load(initialGraph);
        applyRuntimeStateRecursively(editableGraph, blockEntity,
            blockEntity.getLevel() != null ? blockEntity.getLevel().registryAccess() : null);
        hydrateClientOnlyNodeElements(editableGraph, initialGraph);

        Minecraft.getInstance().setScreen(new WNodeScreen(
            Component.literal("Flight Computer"),
            editableGraph,
            (tag) -> {
                CompoundTag copy = tag.copy();
                CLIENT_GRAPH_CACHE.put(cacheKey(blockEntity), copy);
                PacketDistributor.sendToServer(new SputnikNodeSyncPayload(blockEntity.getBlockPos(), copy));
            },
            null
        ));
    }

    private static CompoundTag chooseInitialGraph(SputnikBlockEntity blockEntity, CompoundTag serverGraph) {
        CompoundTag cachedGraph = CLIENT_GRAPH_CACHE.get(cacheKey(blockEntity));
        if (cachedGraph != null && sameNodeShape(serverGraph, cachedGraph)) {
            return cachedGraph.copy();
        }
        return serverGraph.copy();
    }

    private static boolean sameNodeShape(CompoundTag left, CompoundTag right) {
        var leftNodes = left.getList("nodes", 10);
        var rightNodes = right.getList("nodes", 10);
        var leftConns = left.getList("conns", 10);
        var rightConns = right.getList("conns", 10);

        if (leftNodes.size() != rightNodes.size() || leftConns.size() != rightConns.size()) {
            return false;
        }

        Set<String> leftIds = new HashSet<>();
        Set<String> rightIds = new HashSet<>();
        for (int i = 0; i < leftNodes.size(); i++) {
            CompoundTag a = leftNodes.getCompound(i);
            CompoundTag b = rightNodes.getCompound(i);
            if (!a.contains("typeId") || !b.contains("typeId") || !a.hasUUID("id") || !b.hasUUID("id")) {
                return false;
            }
            leftIds.add(a.getUUID("id") + "|" + a.getString("typeId"));
            rightIds.add(b.getUUID("id") + "|" + b.getString("typeId"));
        }
        return leftIds.equals(rightIds);
    }

    private static void hydrateClientOnlyNodeElements(WGraph graph, CompoundTag sourceTag) {
        Map<String, CompoundTag> savedNodesById = new HashMap<>();
        var nodesTag = sourceTag.getList("nodes", 10);
        for (int i = 0; i < nodesTag.size(); i++) {
            CompoundTag nodeTag = nodesTag.getCompound(i);
            if (nodeTag.hasUUID("id")) {
                savedNodesById.put(nodeTag.getUUID("id").toString(), nodeTag);
            }
        }

        for (var node : graph.getNodes()) {
            CompoundTag savedNode = savedNodesById.get(node.getId().toString());
            if (savedNode == null) continue;

            var savedElements = savedNode.getList("elements", 10);
            if (!savedElements.isEmpty()) {
                int before = node.getElements().size();
                node.evaluate();
                List<dev.devce.websnodelib.api.WElement> runtimeElements = node.getElements();
                if (runtimeElements.size() > before) {
                    for (int i = 0; i < Math.min(runtimeElements.size(), savedElements.size()); i++) {
                        runtimeElements.get(i).load(savedElements.getCompound(i));
                    }
                }
            }

            if (savedNode.contains("internalGraph") && node.getInternalGraph() != null) {
                hydrateClientOnlyNodeElements(node.getInternalGraph(), savedNode.getCompound("internalGraph"));
            }
        }
    }

    private static void applyRuntimeStateRecursively(WGraph graph, Object context,
                                                     net.minecraft.core.HolderLookup.Provider registries) {
        graph.setContext(context);
        graph.setRegistries(registries);
        for (var node : graph.getNodes()) {
            if (node.getInternalGraph() != null) {
                applyRuntimeStateRecursively(node.getInternalGraph(), context, registries);
            }
        }
    }

    private static String cacheKey(SputnikBlockEntity blockEntity) {
        String dimension = blockEntity.getLevel() != null
            ? blockEntity.getLevel().dimension().location().toString()
            : "unknown";
        BlockPos pos = blockEntity.getBlockPos();
        return dimension + "|" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }
}
