package dev.devce.rocketnautics.content.blocks.drain_valve;

import com.mojang.serialization.MapCodec;
import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

public class DrainValveBlock extends DirectionalBlock implements IBE<DrainValveBlockEntity>, IWrenchable {
    public static final MapCodec<DrainValveBlock> CODEC = simpleCodec(DrainValveBlock::new);
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    protected static final VoxelShape UP_SHAPE = Block.box(3, 0, 3, 13, 11, 13);
    protected static final VoxelShape DOWN_SHAPE = Block.box(3, 5, 3, 13, 16, 13);
    protected static final VoxelShape NORTH_SHAPE = Block.box(3, 3, 5, 13, 13, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(3, 3, 0, 13, 13, 11);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 3, 3, 11, 13, 13);
    protected static final VoxelShape WEST_SHAPE = Block.box(5, 3, 3, 16, 13, 13);

    public DrainValveBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.UP)
                .setValue(POWERED, false));
    }

    @Override
    protected MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

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
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction attachedDir = state.getValue(FACING).getOpposite();
        BlockPos attachedPos = pos.relative(attachedDir);
        BlockState attachedState = level.getBlockState(attachedPos);
        return !attachedState.isAir();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getClickedFace();
        boolean powered = context.getLevel().hasNeighborSignal(context.getClickedPos());
        return defaultBlockState().setValue(FACING, facing).setValue(POWERED, powered);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide)
            return;
        boolean hasSignal = level.hasNeighborSignal(pos);
        if (state.getValue(POWERED) != hasSignal) {
            level.setBlock(pos, state.setValue(POWERED, hasSignal), 2);
        }
    }

    @Override
    public Class<DrainValveBlockEntity> getBlockEntityClass() {
        return DrainValveBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends DrainValveBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.DRAIN_VALVE.get();
    }
}
