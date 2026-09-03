package dev.devce.rocketnautics.content.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDPrograms;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.MFDCartridgeItem;
import dev.devce.rocketnautics.content.blocks.mfd.cartridge.ui.MFDFocusScreen;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

public class MFDBlock extends DirectionalBlock implements IWrenchable, IBE<MFDBlockEntity> {
    public static final MapCodec<MFDBlock> CODEC = simpleCodec(MFDBlock::new);

    public MFDBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getNearestLookingDirection().getOpposite();
        if (context.getPlayer() != null && context.getPlayer().isShiftKeyDown()) {
            facing = facing.getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (stack.is(AllItems.WRENCH.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }

        if (stack.getItem() instanceof MFDCartridgeItem) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof MFDBlockEntity mfd) {
                String id = MFDCartridgeItem.getCartridgeId(stack);
                if (id.isEmpty()) {
                    id = "pong";
                    MFDCartridgeItem.setCartridgeId(stack, id);
                }
                if (!level.isClientSide) {
                    if (mfd.hasCartridge()) {
                        ItemStack prev = mfd.ejectCartridge();
                        if (!player.getInventory().add(prev)) {
                            player.drop(prev, false);
                        }
                    }
                    mfd.insertCartridge(stack.copy());
                    if (!player.isCreative()) {
                        stack.shrink(1);
                        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
                            sp.inventoryMenu.sendAllDataToRemote();
                        }
                    }
                    player.displayClientMessage(Component.literal("§b[MFD] Inserted Cartridge: §e" + id), true);
                }
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }

        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof MFDBlockEntity mfd) {
            if (player.isCrouching()) {
                if (!level.isClientSide) {
                    if (mfd.hasCartridge()) {
                        ItemStack ejected = mfd.ejectCartridge();
                        if (!player.getInventory().add(ejected)) {
                            player.drop(ejected, false);
                        }
                        player.displayClientMessage(Component.literal("§b[MFD] Ejected Cartridge"), true);
                    } else {
                        mfd.cycleProgram(-1);
                        var current = MFDPrograms.get(mfd.getProgramIndex());
                        if (current != null) {
                            player.displayClientMessage(Component.literal("§b[MFD] Mode: §e" + current.getName()), true);
                        }
                    }
                }
                return InteractionResult.sidedSuccess(level.isClientSide);
            }

            if (level.isClientSide) {
                openFocusScreen(pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private void openFocusScreen(BlockPos pos) {
        dev.devce.rocketnautics.content.blocks.mfd.MFDInputManager.setFocusedPos(pos);
        Minecraft.getInstance().setScreen(new MFDFocusScreen(pos));
    }

    @Override
    public Class<MFDBlockEntity> getBlockEntityClass() {
        return MFDBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MFDBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.MFD.get();
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }
}
