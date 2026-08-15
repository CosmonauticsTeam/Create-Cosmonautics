package dev.devce.rocketnautics.content.blocks.ion;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Ion Engine Block.
 * Smart-places on any clicked surface and delivers electrical plasma thrust powered by FE and controlled by redstone.
 */
public class IonEngineBlock extends DirectionalBlock implements EntityBlock {

    public static final com.mojang.serialization.MapCodec<IonEngineBlock> CODEC = simpleCodec(IonEngineBlock::new);

    protected static final VoxelShape SHAPE_UP = Block.box(0, 0, 0, 16, 4, 16);
    protected static final VoxelShape SHAPE_DOWN = Block.box(0, 12, 0, 16, 16, 16);
    protected static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 12, 16, 16, 16);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 4);
    protected static final VoxelShape SHAPE_WEST = Block.box(12, 0, 0, 16, 16, 16);
    protected static final VoxelShape SHAPE_EAST = Block.box(0, 0, 0, 4, 16, 16);

    public IonEngineBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Bottom adheres to the clicked surface so the engine exhaust points outward
        return this.defaultBlockState().setValue(FACING, context.getClickedFace());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        Direction facing = state.getValue(FACING);
        BlockPos exhaustPos = pos.relative(facing);
        // Ensure the exhaust face is unobstructed
        return !level.getBlockState(exhaustPos).isSolidRender(level, exhaustPos);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new IonEngineBlockEntity(RocketBlockEntities.ION_ENGINE.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (lvl, pos, st, be) -> {
            if (be instanceof IonEngineBlockEntity ionBe) {
                ionBe.tick();
            }
        };
    }
}
