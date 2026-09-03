package dev.devce.rocketnautics.content.blocks.mfd.cartridge;

import dev.devce.rocketnautics.content.blocks.MFDBlock;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui.MFDCartridgeEditorScreen;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import java.util.List;

public class MFDCartridgeItem extends Item {

    public MFDCartridgeItem(Properties properties) {
        super(properties);
    }

    public static String getCartridgeId(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return "";
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag tag = customData.copyTag();
            if (tag.contains("CartridgeId")) {
                return tag.getString("CartridgeId");
            }
        }
        return "";
    }

    public static void setCartridgeId(ItemStack stack, String id) {
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            tag.putString("CartridgeId", id);
        });
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        Player player = context.getPlayer();

        if (state.getBlock() instanceof MFDBlock) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MFDBlockEntity mfd) {
                ItemStack stack = context.getItemInHand();
                String id = getCartridgeId(stack);
                if (id.isEmpty()) {
                    id = "pong";
                    setCartridgeId(stack, id);
                }
                if (!level.isClientSide) {
                    if (mfd.hasCartridge()) {
                        ItemStack prev = mfd.ejectCartridge();
                        if (player != null && !player.getInventory().add(prev)) {
                            player.drop(prev, false);
                        }
                    }
                    mfd.insertCartridge(stack.copy());
                    if (player != null && !player.isCreative()) {
                        stack.shrink(1);
                    }
                    if (player != null) {
                        player.displayClientMessage(Component.literal("§b[MFD] Inserted Cartridge: §e" + id), true);
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return super.useOn(context);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            String id = getCartridgeId(stack);
            if (id.isEmpty()) {
                id = "pong";
                setCartridgeId(stack, id);
            }
            openEditor(id, hand);
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private void openEditor(String id, InteractionHand hand) {
        Minecraft.getInstance().setScreen(new MFDCartridgeEditorScreen(id, hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltipComponents, TooltipFlag tooltipFlag) {
        String id = getCartridgeId(stack);
        if (!id.isEmpty()) {
            CartridgeManager.CartridgeMetadata meta = CartridgeManager.getMetadata(id);
            String title = (meta.title != null && !meta.title.isEmpty()) ? meta.title : id;
            String ver = (meta.version != null && !meta.version.isEmpty()) ? "v" + meta.version : "";
            String auth = (meta.author != null && !meta.author.isEmpty()) ? "by " + meta.author : "";

            tooltipComponents.add(Component.literal(title + (ver.isEmpty() ? "" : "  " + ver)).withStyle(ChatFormatting.AQUA));
            if (!auth.isEmpty()) {
                tooltipComponents.add(Component.literal(auth).withStyle(ChatFormatting.DARK_GRAY));
            }
            if (meta.description != null && !meta.description.isEmpty()) {
                tooltipComponents.add(Component.literal(meta.description).withStyle(ChatFormatting.GRAY));
            }
        } else {
            tooltipComponents.add(Component.literal("Empty Cartridge").withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
