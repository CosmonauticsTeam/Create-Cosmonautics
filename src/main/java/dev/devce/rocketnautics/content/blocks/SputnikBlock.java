package dev.devce.rocketnautics.content.blocks;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.content.sputnik.network.SputnikOpenRequestPayload;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class SputnikBlock extends BaseEntityBlock implements IBE<SputnikBlockEntity>, IWrenchable {
    public static final MapCodec<SputnikBlock> CODEC = simpleCodec(SputnikBlock::new);
    public static final BooleanProperty LEGS = BooleanProperty.create("legs");

    public SputnikBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(LEGS, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LEGS);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState().setValue(LEGS, false);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        tooltip.add(Component.literal("The brain of your spacecraft.").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.literal("Runs a node graph every tick — read sensors,").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("control thrusters, send radio packets and more.").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.literal("Right-click to open the node editor.").withStyle(ChatFormatting.AQUA));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public Class<SputnikBlockEntity> getBlockEntityClass() {
        return SputnikBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SputnikBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.SPUTNIK.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return createTickerHelper(type, RocketBlockEntities.SPUTNIK.get(), SputnikBlockEntity::tick);
    }

    @Override
    public InteractionResult onWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        if (!level.isClientSide()) {
            BlockState newState = state.cycle(LEGS);
            level.setBlock(pos, newState, 3);
            IWrenchable.playRotateSound(level, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean isWrench(ItemStack stack) {
        if (stack.isEmpty()) return false;
        return com.simibubi.create.AllItems.WRENCH.isIn(stack)
                || stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("c", "tools/wrench")))
                || stack.is(net.minecraft.tags.ItemTags.create(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("forge", "tools/wrench")));
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (isWrench(stack)) {
            if (player.isShiftKeyDown()) {
                return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            }
            if (!level.isClientSide()) {
                BlockState newState = state.cycle(LEGS);
                level.setBlock(pos, newState, 3);
                IWrenchable.playRotateSound(level, pos);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide());
        }
        if (level.isClientSide) {
            PacketDistributor.sendToServer(new SputnikOpenRequestPayload(pos));
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        if (isWrench(player.getItemInHand(InteractionHand.MAIN_HAND)) || isWrench(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            PacketDistributor.sendToServer(new SputnikOpenRequestPayload(pos));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide() && level.getBlockEntity(pos) instanceof SputnikBlockEntity sputnik) {
                dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.onSputnikRemoved(sputnik.getSputnikId());
                dev.devce.rocketnautics.content.sputnik.comms.SputnikCommsManager.onSputnikRemoved(sputnik.getSputnikId());
            }
            super.onRemove(state, level, pos, newState, isMoving);
        }
    }
}
