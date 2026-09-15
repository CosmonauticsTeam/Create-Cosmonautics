package dev.devce.rocketnautics.content.blocks.sputnik_link;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class SputnikLinkBlock extends DirectionalBlock implements IBE<SputnikLinkBlockEntity>, IWrenchable {
    public static final MapCodec<SputnikLinkBlock> CODEC = simpleCodec(SputnikLinkBlock::new);

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty RECEIVER = BooleanProperty.create("receiver");

    private static final VoxelShape SHAPE_UP = Block.box(3, 0, 2, 13, 2, 14);
    private static final VoxelShape SHAPE_DOWN = Block.box(3, 14, 2, 13, 16, 14);
    private static final VoxelShape SHAPE_NORTH = Block.box(3, 2, 14, 13, 14, 16);
    private static final VoxelShape SHAPE_SOUTH = Block.box(3, 2, 0, 13, 14, 2);
    private static final VoxelShape SHAPE_WEST = Block.box(14, 2, 3, 16, 14, 13);
    private static final VoxelShape SHAPE_EAST = Block.box(0, 2, 3, 2, 14, 13);

    public SputnikLinkBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(RECEIVER, true));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, RECEIVER);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return this.defaultBlockState()
                .setValue(FACING, context.getClickedFace())
                .setValue(RECEIVER, true);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
            default -> SHAPE_UP;
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide() && level.getServer() != null) {
            withBlockEntityDo(level, pos, be -> {
                if (be.getLinkId() <= 0) {
                    be.setLinkId(SputnikLinkManager.allocateId(level.getServer()));
                }
            });
        }
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (com.simibubi.create.AllItems.WRENCH.isIn(stack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        toggleMode(state, level, pos, player);
        return ItemInteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hitResult) {
        toggleMode(state, level, pos, player);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private void toggleMode(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide()) {
            BlockState newState = state.cycle(RECEIVER);
            level.setBlock(pos, newState, 3);
            level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 0.4f, newState.getValue(RECEIVER) ? 0.7f : 0.5f);

            withBlockEntityDo(level, pos, be -> {
                boolean isReceiver = newState.getValue(RECEIVER);
                SputnikLinkManager.updateMode(be.getLinkId(), isReceiver);

                Component modeText = isReceiver
                        ? Component.translatable("rocketnautics.goggles.mode.receiver").withStyle(ChatFormatting.GREEN, ChatFormatting.BOLD)
                        : Component.translatable("rocketnautics.goggles.mode.transmitter").withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD);

                player.displayClientMessage(
                        Component.literal("Sputnik Link #").append(String.valueOf(be.getLinkId())).append(": ").append(modeText),
                        true
                );
            });

            level.updateNeighborsAt(pos, this);
        }
    }

    @Override
    public boolean isSignalSource(BlockState state) {
        return state.getValue(RECEIVER);
    }

    @Override
    public int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!state.getValue(RECEIVER)) return 0;
        if (level.getBlockEntity(pos) instanceof SputnikLinkBlockEntity be) {
            return be.getEmittedSignal();
        }
        return 0;
    }

    @Override
    public int getDirectSignal(BlockState state, BlockGetter level, BlockPos pos, Direction side) {
        if (!state.getValue(RECEIVER)) return 0;
        if (state.getValue(FACING).getOpposite() == side) {
            if (level.getBlockEntity(pos) instanceof SputnikLinkBlockEntity be) {
                return be.getEmittedSignal();
            }
        }
        return 0;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock, BlockPos neighborPos, boolean movedByPiston) {
        super.neighborChanged(state, level, pos, neighborBlock, neighborPos, movedByPiston);
        if (!level.isClientSide() && !state.getValue(RECEIVER)) {
            withBlockEntityDo(level, pos, be -> {
                int power = level.getBestNeighborSignal(pos);
                if (power != be.getReceivedSignal()) {
                    SputnikLinkManager.setReceivedSignal(be.getLinkId(), power);
                }
            });
        }
    }

    @Override
    public Class<SputnikLinkBlockEntity> getBlockEntityClass() {
        return SputnikLinkBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends SputnikLinkBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.SPUTNIK_LINK.get();
    }
}
