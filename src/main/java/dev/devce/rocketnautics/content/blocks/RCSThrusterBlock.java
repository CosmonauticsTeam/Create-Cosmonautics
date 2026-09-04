package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.content.decoration.encasing.EncasableBlock;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RCSThrusterBlock extends AbstractRocketThrusterBlock<RCSThrusterBlockEntity> implements EncasableBlock {
    public static final com.mojang.serialization.MapCodec<RCSThrusterBlock> CODEC = simpleCodec(RCSThrusterBlock::new);

    public RCSThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        if (player.isShiftKeyDown() || !player.mayBuild()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return tryEncase(state, level, pos, stack, player, hand, hitResult);
    }

    public static void switchBlockPreservingData(Level level, BlockPos pos, BlockState targetState) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        CompoundTag blockEntityData = blockEntity == null ? null : blockEntity.saveWithFullMetadata(level.registryAccess());
        level.setBlock(pos, targetState, Block.UPDATE_ALL);
        if (blockEntityData == null) {
            return;
        }

        BlockEntity replacement = BlockEntity.loadStatic(pos, targetState, blockEntityData, level.registryAccess());
        if (replacement != null) {
            level.setBlockEntity(replacement);
        }
    }


    protected static final VoxelShape UP_SHAPE = Block.box(6, 0, 6, 10, 12, 10);
    protected static final VoxelShape DOWN_SHAPE = Block.box(6, 4, 6, 10, 16, 10);
    protected static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 4, 10, 10, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 0, 10, 10, 12);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 6, 6, 12, 10, 10);
    protected static final VoxelShape WEST_SHAPE = Block.box(4, 6, 6, 16, 10, 10);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
        case UP -> UP_SHAPE;
        case DOWN -> DOWN_SHAPE;
        case NORTH -> NORTH_SHAPE;
        case SOUTH -> SOUTH_SHAPE;
        case EAST -> EAST_SHAPE;
        case WEST -> WEST_SHAPE;
        };
    }

    @Override
    public Class<RCSThrusterBlockEntity> getBlockEntityClass() {
        return RCSThrusterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends RCSThrusterBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.RCS_THRUSTER.get();
    }

}
