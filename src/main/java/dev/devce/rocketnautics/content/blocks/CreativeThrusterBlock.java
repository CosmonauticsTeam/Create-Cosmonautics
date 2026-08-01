package dev.devce.rocketnautics.content.blocks;

import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.DirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class CreativeThrusterBlock extends AbstractRocketThrusterBlock<CreativeThrusterBlockEntity> {
    public static final com.mojang.serialization.MapCodec<CreativeThrusterBlock> CODEC = simpleCodec(CreativeThrusterBlock::new);

    public CreativeThrusterBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.UP));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends DirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public Class<CreativeThrusterBlockEntity> getBlockEntityClass() {
        return CreativeThrusterBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends CreativeThrusterBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.CREATIVE_THRUSTER.get();
    }
}
