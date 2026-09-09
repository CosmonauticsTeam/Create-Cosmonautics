package dev.devce.rocketnautics.content.blocks.energy_tank;

import com.simibubi.create.content.equipment.wrench.IWrenchable;
import com.simibubi.create.foundation.block.IBE;
import dev.devce.rocketnautics.registry.RocketBlockEntities;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

public class EnergyTankBlock extends Block implements IWrenchable, IBE<EnergyTankBlockEntity> {

    public EnergyTankBlock(Properties properties) {
        super(properties);
    }

    @Override
    public Class<EnergyTankBlockEntity> getBlockEntityClass() {
        return EnergyTankBlockEntity.class;
    }

    @Override
    public BlockEntityType<? extends EnergyTankBlockEntity> getBlockEntityType() {
        return RocketBlockEntities.ENERGY_TANK.get();
    }
}
