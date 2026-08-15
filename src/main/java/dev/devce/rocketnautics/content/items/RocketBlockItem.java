package dev.devce.rocketnautics.content.items;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.loading.FMLEnvironment;

import java.util.List;

public class RocketBlockItem extends BlockItem {
    public RocketBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        if (FMLEnvironment.dist.isClient() && net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            tooltipComponents.add(Component.literal(" ")); 
            tooltipComponents.add(Component.translatable(this.getDescriptionId() + ".tooltip.shift").withStyle(ChatFormatting.GOLD));
            
            
            String id = this.getDescriptionId();
            if (id.contains("rocket_thruster")) {
                tooltipComponents.add(Component.literal(" ")); 
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.rocket_thruster.extra").withStyle(ChatFormatting.GRAY));
            } else if (id.contains("creative_thruster")) {
                tooltipComponents.add(Component.literal(" ")); 
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.creative_thruster.extra").withStyle(ChatFormatting.GRAY));
            } else if (id.contains("booster_thruster")) {
                tooltipComponents.add(Component.literal(" "));
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.booster_thruster.extra").withStyle(ChatFormatting.GRAY));
            } else if (id.contains("vector_thruster")) {
                tooltipComponents.add(Component.literal(" "));
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.vector_thruster.extra").withStyle(ChatFormatting.GRAY));
            } else if (id.contains("separator")) {
                tooltipComponents.add(Component.literal(" "));
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.separator.extra").withStyle(ChatFormatting.GRAY));
            } else if (id.contains("rcs_thruster")) {
                tooltipComponents.add(Component.literal(" "));
                tooltipComponents.add(Component.translatable("rocketnautics.tooltip.rcs_thruster.extra").withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltipComponents.add(Component.translatable("rocketnautics.tooltip.hold_shift").withStyle(ChatFormatting.DARK_GRAY));
        }
        super.appendHoverText(stack, context, tooltipComponents, tooltipFlag);
    }
}
