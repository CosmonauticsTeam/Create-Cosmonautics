package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.content.redstone.displayLink.DisplayLinkContext;
import com.simibubi.create.content.redstone.displayLink.source.SingleLineDisplaySource;
import com.simibubi.create.content.redstone.displayLink.target.DisplayTargetStats;
import com.simibubi.create.foundation.gui.ModularGuiLineBuilder;
import dev.devce.rocketnautics.content.sputnik.model.SputnikNode;
import dev.devce.rocketnautics.content.sputnik.node.INodeHandler;
import dev.devce.rocketnautics.content.sputnik.node.SputnikNodeRegistry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class SputnikDisplaySource extends SingleLineDisplaySource {

    @Override
    protected MutableComponent provideLine(DisplayLinkContext context, DisplayTargetStats stats) {
        if (!(context.getSourceBlockEntity() instanceof SputnikBlockEntity sputnik))
            return EMPTY_LINE;

        List<SputnikNode> displayNodes = sputnik.getGraph().getNodes().stream()
                .filter(SputnikDisplaySource::isDisplayNode)
                .sorted(Comparator.comparing(SputnikNode::getTitle))
                .toList();

        List<String> bridgeKeys = sputnik.getDisplayBridge().keySet().stream()
                .sorted()
                .toList();

        int index = context.sourceConfig().getInt("NodeIndex");
        if (index < 0) return EMPTY_LINE;

        if (index < displayNodes.size()) {
            SputnikNode targetNode = displayNodes.get(index);
            String val = targetNode.getCustomString();
            return Component.literal(val != null ? val : "");
        } else {
            int bridgeIndex = index - displayNodes.size();
            if (bridgeIndex >= 0 && bridgeIndex < bridgeKeys.size()) {
                String key = bridgeKeys.get(bridgeIndex);
                String val = sputnik.getDisplayBridge().get(key);
                return Component.literal(key + ": " + (val != null ? val : ""));
            }
        }
        return EMPTY_LINE;
    }

    @Override
    protected boolean allowsLabeling(DisplayLinkContext context) {
        return true;
    }

    @Override
    protected String getTranslationKey() {
        return "sputnik";
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void initConfigurationWidgets(DisplayLinkContext context, ModularGuiLineBuilder builder, boolean isFirstLine) {
        super.initConfigurationWidgets(context, builder, isFirstLine);
        if (isFirstLine) return;

        if (!(context.getSourceBlockEntity() instanceof SputnikBlockEntity sputnik)) return;

        List<Component> options = new ArrayList<>();
        List<SputnikNode> displayNodes = sputnik.getGraph().getNodes().stream()
                .filter(SputnikDisplaySource::isDisplayNode)
                .sorted(Comparator.comparing(SputnikNode::getTitle))
                .toList();

        for (SputnikNode n : displayNodes) {
            options.add(Component.literal("Node: " + n.getTitle()));
        }

        List<String> bridgeKeys = sputnik.getDisplayBridge().keySet().stream()
                .sorted()
                .toList();

        for (String k : bridgeKeys) {
            options.add(Component.literal("Bridge: " + k));
        }

        if (options.isEmpty()) {
            options.add(Component.literal("No Display Targets"));
        }

        builder.addSelectionScrollInput(0, 120, (si, l) -> si
                .forOptions(options)
                .titled(Component.literal("Select Display Target")),
                "NodeIndex");
    }

    private static boolean isDisplayNode(SputnikNode node) {
        INodeHandler handler = SputnikNodeRegistry.get(node.getTypeId());
        return handler != null && "Display".equals(handler.getCategory());
    }
}
