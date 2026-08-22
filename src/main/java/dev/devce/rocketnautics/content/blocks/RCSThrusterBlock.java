package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.AllBlocks;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class RCSThrusterBlock extends AbstractRocketThrusterBlock<RCSThrusterBlockEntity> {
    public static final com.mojang.serialization.MapCodec<RCSThrusterBlock> CODEC = simpleCodec(RCSThrusterBlock::new);
    public static final EnumProperty<Casing> CASING = EnumProperty.create("casing", Casing.class);

    public RCSThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP).setValue(CASING, Casing.NONE));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, CASING);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
            InteractionHand hand, BlockHitResult hitResult) {
        Casing casing = stack.is(AllBlocks.BRASS_CASING.get().asItem()) ? Casing.BRASS
                : stack.is(AllBlocks.COPPER_CASING.get().asItem()) ? Casing.COPPER
                        : stack.is(AllBlocks.RAILWAY_CASING.get().asItem()) ? Casing.RAILWAY : Casing.NONE;
        if (casing == Casing.NONE || state.getValue(CASING) != Casing.NONE) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }

        if (!level.isClientSide) {
            level.setBlock(pos, state.setValue(CASING, casing), Block.UPDATE_ALL);
            if (!player.isCreative()) {
                stack.shrink(1);
            }
            level.playSound(null, pos, SoundEvents.COPPER_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }


    protected static final VoxelShape UP_SHAPE = Block.box(6, 0, 6, 10, 12, 10);
    protected static final VoxelShape DOWN_SHAPE = Block.box(6, 4, 6, 10, 16, 10);
    protected static final VoxelShape NORTH_SHAPE = Block.box(6, 6, 4, 10, 10, 16);
    protected static final VoxelShape SOUTH_SHAPE = Block.box(6, 6, 0, 10, 10, 12);
    protected static final VoxelShape EAST_SHAPE = Block.box(0, 6, 6, 12, 10, 10);
    protected static final VoxelShape WEST_SHAPE = Block.box(4, 6, 6, 16, 10, 10);
    protected static final VoxelShape ENCASED_SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        if (state.getValue(CASING) != Casing.NONE) {
            return ENCASED_SHAPE;
        }
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

    public enum Casing implements StringRepresentable {
        NONE("none"), BRASS("brass"), COPPER("copper"), RAILWAY("railway");

        private final String serializedName;

        Casing(String serializedName) {
            this.serializedName = serializedName;
        }

        @Override
        public String getSerializedName() {
            return serializedName;
        }
    }
}
