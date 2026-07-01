package dev.devce.rocketnautics.content.items.hose;

import com.simibubi.create.content.fluids.FluidPropagator;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlock;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.simulated_team.simulated.content.blocks.rope.RopeStrandHolderBehavior;

public class FluidHoseItem extends Item {

    public FluidHoseItem(Properties properties) {
        super(properties);
    }

    private void setFirstPos(ItemStack stack, BlockPos pos, Direction face) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.putLong("FirstPos", pos.asLong());
        tag.putInt("FirstFace", face.ordinal());
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    private BlockPos getFirstPos(ItemStack stack) {
        CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (tag.contains("FirstPos")) {
            return BlockPos.of(tag.getLong("FirstPos"));
        }
        return null;
    }

    private Direction getFirstFace(ItemStack stack) {
        CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
        if (customData == null) return null;
        CompoundTag tag = customData.copyTag();
        if (tag.contains("FirstFace")) {
            return Direction.values()[tag.getInt("FirstFace")];
        }
        return null;
    }

    private void clearFirstPos(ItemStack stack) {
        CustomData customData = stack.getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.EMPTY);
        CompoundTag tag = customData.copyTag();
        tag.remove("FirstPos");
        tag.remove("FirstFace");
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA, net.minecraft.world.item.component.CustomData.of(tag));
    }

    private static class ConnectionPoint {
        final BlockPos anchorPos;
        final Direction facing;
        final boolean needsPlacement;

        ConnectionPoint(BlockPos anchorPos, Direction facing, boolean needsPlacement) {
            this.anchorPos = anchorPos;
            this.facing = facing;
            this.needsPlacement = needsPlacement;
        }
    }

    private ConnectionPoint resolveConnectionPoint(Level level, BlockPos clickedPos, Direction clickedFace) {
        BlockState clickedState = level.getBlockState(clickedPos);
        if (clickedState.getBlock() instanceof HoseAnchorBlock) {
            return new ConnectionPoint(clickedPos, clickedState.getValue(HoseAnchorBlock.FACING), false);
        }

        boolean isValidPipe = FluidPropagator.hasFluidCapability(level, clickedPos, clickedFace)
                || clickedState.getBlock() instanceof com.simibubi.create.content.fluids.pipes.FluidPipeBlock;

        if (isValidPipe) {
            BlockPos anchorPos = clickedPos.relative(clickedFace);
            BlockState anchorState = level.getBlockState(anchorPos);
            if (anchorState.canBeReplaced() || anchorState.getBlock() instanceof HoseAnchorBlock) {
                return new ConnectionPoint(anchorPos, clickedFace, true);
            }
        }

        return null;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        Direction clickedFace = context.getClickedFace();
        ItemStack heldStack = context.getItemInHand();
        Player player = context.getPlayer();

        if (player != null && player.isShiftKeyDown()) {
            clearFirstPos(heldStack);
            if (level.isClientSide) {
                player.displayClientMessage(Component.literal("Hose cleared!"), true);
            }
            return InteractionResult.SUCCESS;
        }

        ConnectionPoint point = resolveConnectionPoint(level, clickedPos, clickedFace);
        if (point == null) {
            return InteractionResult.PASS;
        }

        BlockPos firstPos = getFirstPos(heldStack);
        Direction firstFace = getFirstFace(heldStack);

        if (firstPos == null) {
            setFirstPos(heldStack, clickedPos, clickedFace);
            level.playSound(player, clickedPos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 0.6F, 1.2F);
            if (level.isClientSide && player != null) {
                player.displayClientMessage(Component.literal("Input set! Now click the output pipe or anchor."), true);
            }
            return InteractionResult.SUCCESS;
        } else {
            if (firstPos.equals(clickedPos) && firstFace == clickedFace) {
                return InteractionResult.PASS;
            }

            ConnectionPoint firstPoint = resolveConnectionPoint(level, firstPos, firstFace);
            if (firstPoint == null) {
                clearFirstPos(heldStack);
                if (level.isClientSide && player != null) {
                    player.displayClientMessage(Component.literal("First connection point is no longer valid!"), true);
                }
                return InteractionResult.SUCCESS;
            }

            if (!level.isClientSide) {
                if (firstPoint.needsPlacement) {
                    BlockState state = RocketBlocks.HOSE_ANCHOR.get().defaultBlockState().setValue(HoseAnchorBlock.FACING, firstPoint.facing);
                    level.setBlock(firstPoint.anchorPos, state, 3);
                }
                if (point.needsPlacement) {
                    BlockState state = RocketBlocks.HOSE_ANCHOR.get().defaultBlockState().setValue(HoseAnchorBlock.FACING, point.facing);
                    level.setBlock(point.anchorPos, state, 3);
                }

                if (attachHose(level, firstPoint.anchorPos, point.anchorPos)) {
                    if (player != null) {
                        player.displayClientMessage(Component.literal("Hose connected successfully!"), true);
                    }
                } else {
                    if (player != null) {
                        player.displayClientMessage(Component.literal("Failed to connect hose (too far or invalid!)."), true);
                    }
                }
            }

            clearFirstPos(heldStack);
            if (player != null && !player.hasInfiniteMaterials()) {
                heldStack.shrink(1);
            }
            return InteractionResult.SUCCESS;
        }
    }

    private boolean attachHose(Level level, BlockPos posA, BlockPos posB) {
        BlockEntity beA = level.getBlockEntity(posA);
        BlockEntity beB = level.getBlockEntity(posB);

        if (beA instanceof SmartBlockEntity sbeA && beB instanceof SmartBlockEntity sbeB) {
            RopeStrandHolderBehavior holderA = sbeA.getBehaviour(RopeStrandHolderBehavior.TYPE);
            RopeStrandHolderBehavior holderB = sbeB.getBehaviour(RopeStrandHolderBehavior.TYPE);

            if (holderA != null && holderB != null) {
                if (holderA.createRope(holderB, true)) {
                    level.playSound(null, posA, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
                    level.playSound(null, posB, SoundEvents.WOOL_PLACE, SoundSource.BLOCKS, 0.5F, 1F);
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, java.util.List<Component> tooltip, net.minecraft.world.item.TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        dev.devce.rocketnautics.content.RocketTooltipHelper.appendTooltip(stack, this, tooltip, flag);
    }
}
