package dev.devce.rocketnautics.content.blocks.wire;

import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import org.jetbrains.annotations.Nullable;

import java.util.EnumMap;
import java.util.Map;

public class CopperWireBlock extends Block implements IBE<CopperWireBlockEntity>, SimpleWaterloggedBlock {

    public static final BooleanProperty NORTH = PipeBlock.NORTH;
    public static final BooleanProperty EAST = PipeBlock.EAST;
    public static final BooleanProperty SOUTH = PipeBlock.SOUTH;
    public static final BooleanProperty WEST = PipeBlock.WEST;
    public static final BooleanProperty UP = PipeBlock.UP;
    public static final BooleanProperty DOWN = PipeBlock.DOWN;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;

    public static final Map<Direction, BooleanProperty> PROPERTY_BY_DIRECTION = PipeBlock.PROPERTY_BY_DIRECTION;

    private static final VoxelShape CORE_SHAPE = Block.box(6, 6, 6, 10, 10, 10);
    private static final Map<Direction, VoxelShape> SIDE_SHAPES = new EnumMap<>(Direction.class);

    static {
        SIDE_SHAPES.put(Direction.UP, Block.box(6, 10, 6, 10, 16, 10));
        SIDE_SHAPES.put(Direction.DOWN, Block.box(6, 0, 6, 10, 6, 10));
        SIDE_SHAPES.put(Direction.NORTH, Block.box(6, 6, 0, 10, 10, 6));
        SIDE_SHAPES.put(Direction.SOUTH, Block.box(6, 6, 10, 10, 10, 16));
        SIDE_SHAPES.put(Direction.WEST, Block.box(0, 6, 6, 6, 10, 10));
        SIDE_SHAPES.put(Direction.EAST, Block.box(10, 6, 6, 16, 10, 10));
    }

    private final VoxelShape[] shapeCache;

    public CopperWireBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false)
                .setValue(WATERLOGGED, false));

        this.shapeCache = makeShapes();
    }

    private VoxelShape[] makeShapes() {
        VoxelShape[] shapes = new VoxelShape[64];
        for (int i = 0; i < 64; i++) {
            VoxelShape shape = CORE_SHAPE;
            if ((i & (1 << Direction.DOWN.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.DOWN));
            if ((i & (1 << Direction.UP.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.UP));
            if ((i & (1 << Direction.NORTH.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.NORTH));
            if ((i & (1 << Direction.SOUTH.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.SOUTH));
            if ((i & (1 << Direction.WEST.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.WEST));
            if ((i & (1 << Direction.EAST.get3DDataValue())) != 0) shape = Shapes.or(shape, SIDE_SHAPES.get(Direction.EAST));
            shapes[i] = shape;
        }
        return shapes;
    }

    private int getIndex(BlockState state) {
        int index = 0;
        if (state.getValue(DOWN)) index |= (1 << Direction.DOWN.get3DDataValue());
        if (state.getValue(UP)) index |= (1 << Direction.UP.get3DDataValue());
        if (state.getValue(NORTH)) index |= (1 << Direction.NORTH.get3DDataValue());
        if (state.getValue(SOUTH)) index |= (1 << Direction.SOUTH.get3DDataValue());
        if (state.getValue(WEST)) index |= (1 << Direction.WEST.get3DDataValue());
        if (state.getValue(EAST)) index |= (1 << Direction.EAST.get3DDataValue());
        return index;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return this.shapeCache[getIndex(state)];
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN, WATERLOGGED);
    }

    public boolean canConnectTo(LevelAccessor level, BlockPos currentPos, Direction direction) {
        BlockPos neighborPos = currentPos.relative(direction);
        BlockState neighborState = level.getBlockState(neighborPos);

        if (neighborState.getBlock() instanceof CopperWireBlock) {
            return true;
        }

        if (level instanceof Level fullLevel) {
            var energyStorage = fullLevel.getCapability(Capabilities.EnergyStorage.BLOCK, neighborPos, direction.getOpposite());
            return energyStorage != null;
        }

        return false;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        FluidState fluidState = level.getFluidState(pos);

        BlockState state = this.defaultBlockState();
        for (Direction direction : Direction.values()) {
            BooleanProperty prop = PROPERTY_BY_DIRECTION.get(direction);
            state = state.setValue(prop, canConnectTo(level, pos, direction));
        }

        return state.setValue(WATERLOGGED, fluidState.getType() == Fluids.WATER);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState, LevelAccessor level, BlockPos currentPos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(currentPos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }

        BooleanProperty prop = PROPERTY_BY_DIRECTION.get(direction);
        return state.setValue(prop, canConnectTo(level, currentPos, direction));
    }

    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    public Class<CopperWireBlockEntity> getBlockEntityClass() {
        return CopperWireBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CopperWireBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.COPPER_WIRE.get();
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> blockEntityType) {
        return (lvl, pos, st, be) -> {
            if (be instanceof CopperWireBlockEntity wireBe) {
                wireBe.tick();
            }
        };
    }
}
