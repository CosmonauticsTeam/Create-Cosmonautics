package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

import javax.annotation.Nullable;

public class MagneticStabilizerBlock extends Block implements IBE<MagneticStabilizerBlockEntity> {
    public static final BooleanProperty POWERED = BlockStateProperties.POWERED;

    public MagneticStabilizerBlock(Properties p_49795_) {
        super(p_49795_);
        registerDefaultState(defaultBlockState().setValue(POWERED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder.add(POWERED));
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext p_55659_) {
        return this.defaultBlockState().setValue(POWERED, p_55659_.getLevel().hasNeighborSignal(p_55659_.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState p_55666_, Level p_55667_, BlockPos p_55668_, Block p_55669_, BlockPos p_55670_, boolean p_55671_) {
        if (!p_55667_.isClientSide) {
            boolean flag = p_55666_.getValue(POWERED);
            if (flag != p_55667_.hasNeighborSignal(p_55668_)) {
                p_55667_.setBlock(p_55668_, p_55666_.cycle(POWERED), 2);
            }
        }
    }

    @Override
    public Class<MagneticStabilizerBlockEntity> getBlockEntityClass() {
        return MagneticStabilizerBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends MagneticStabilizerBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.MAGNETIC_STABILIZER.get();
    }
}
