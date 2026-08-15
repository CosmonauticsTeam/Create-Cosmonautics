package dev.devce.rocketnautics.content.blocks.solar;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
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
 * Solar Panel Block.
 * Smart-places on any clicked surface and generates Forge Energy (FE) based on angle to the Sun.
 */
public class SolarPanelBlock extends DirectionalBlock implements EntityBlock {

    public static final com.mojang.serialization.MapCodec<SolarPanelBlock> CODEC = simpleCodec(SolarPanelBlock::new);

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    protected static final VoxelShape SHAPE_UP = Block.box(0, 0, 0, 16, 4, 16);
    protected static final VoxelShape SHAPE_DOWN = Block.box(0, 12, 0, 16, 16, 16);
    protected static final VoxelShape SHAPE_NORTH = Block.box(0, 0, 12, 16, 16, 16);
    protected static final VoxelShape SHAPE_SOUTH = Block.box(0, 0, 0, 16, 16, 4);
    protected static final VoxelShape SHAPE_WEST = Block.box(12, 0, 0, 16, 16, 16);
    protected static final VoxelShape SHAPE_EAST = Block.box(0, 0, 0, 4, 16, 16);

    public SolarPanelBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        // Smart placement: bottom attaches to the clicked block face so panel faces outward
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
        return new SolarPanelBlockEntity(RocketBlockEntities.SOLAR_PANEL.get(), pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (lvl, pos, st, be) -> {
            if (be instanceof SolarPanelBlockEntity solarBe) {
                solarBe.tick();
            }
        };
    }
}
