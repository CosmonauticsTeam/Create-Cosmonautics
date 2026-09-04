package dev.devce.rocketnautics.content.blocks;

import java.util.function.Supplier;

import com.simibubi.create.content.decoration.encasing.EncasedBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class EncasedRCSThrusterBlock extends RCSThrusterBlock implements EncasedBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 16, 16);

    private final Supplier<Block> casing;

    public EncasedRCSThrusterBlock(Properties properties, Supplier<Block> casing) {
        super(properties);
        this.casing = casing;
    }

    @Override
    public VoxelShape getShape(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos, CollisionContext context) {
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
}
