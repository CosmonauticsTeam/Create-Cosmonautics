package dev.devce.rocketnautics.content.blocks;

import java.util.function.Supplier;

import com.simibubi.create.api.schematic.requirement.SpecialBlockItemRequirement;
import com.simibubi.create.content.decoration.encasing.EncasedBlock;
import com.simibubi.create.content.schematics.requirement.ItemRequirement;

import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EncasedRCSThrusterBlock extends RCSThrusterBlock implements EncasedBlock, SpecialBlockItemRequirement {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    private final Supplier<Block> casing;

    public EncasedRCSThrusterBlock(Properties properties, Supplier<Block> casing) {
        super(properties);
        this.casing = casing;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    public Block getCasing() {
        return casing.get();
    }

    @Override
    public void handleEncasing(BlockState state, Level level, BlockPos pos, ItemStack heldItem, Player player, InteractionHand hand,
            BlockHitResult ray) {
        BlockState encasedState = defaultBlockState().setValue(FACING, state.getValue(FACING));
        switchBlockPreservingData(level, pos, encasedState);

        if (!player.isCreative()) {
            heldItem.shrink(1);
        }
    }

    @Override
    public InteractionResult onSneakWrenched(BlockState state, UseOnContext context) {
        Level level = context.getLevel();
        if (!level.isClientSide) {
            BlockPos pos = context.getClickedPos();
            level.levelEvent(2001, pos, Block.getId(state));
            switchBlockPreservingData(level, pos, RocketBlocks.RCS_THRUSTER.getDefaultState().setValue(FACING, state.getValue(FACING)));
            Block.popResource(level, pos, new ItemStack(getCasing()));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public ItemRequirement getRequiredItems(BlockState state, BlockEntity blockEntity) {
        return ItemRequirement.of(RocketBlocks.RCS_THRUSTER.getDefaultState(), blockEntity)
                .union(new ItemRequirement(ItemRequirement.ItemUseType.CONSUME, getCasing().asItem()));
    }

    @Override
    public ItemStack getCloneItemStack(BlockState state, HitResult target, LevelReader level, BlockPos pos, Player player) {
        if (target instanceof BlockHitResult hit && hit.getDirection() == state.getValue(FACING)) {
            return new ItemStack(RocketBlocks.RCS_THRUSTER.get());
        }
        return new ItemStack(getCasing().asItem());
    }
}
