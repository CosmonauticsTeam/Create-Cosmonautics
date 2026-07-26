package dev.devce.rocketnautics.content.blocks;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class VectorThrusterBlock extends AbstractRocketThrusterBlock<VectorThrusterBlockEntity> {
    public static final com.mojang.serialization.MapCodec<VectorThrusterBlock> CODEC = simpleCodec(VectorThrusterBlock::new);

    public VectorThrusterBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends net.minecraft.world.level.block.DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public void neighborChanged(BlockState state, Level level, BlockPos pos, Block block, BlockPos fromPos, boolean isMoving) {
        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof VectorThrusterBlockEntity vectorBE) {
                vectorBE.updateGimbalAngles();
            }
        }
    }

    @Override
    public Class<VectorThrusterBlockEntity> getBlockEntityClass() {
        return VectorThrusterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends VectorThrusterBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.VECTOR_THRUSTER.get();
    }
}
