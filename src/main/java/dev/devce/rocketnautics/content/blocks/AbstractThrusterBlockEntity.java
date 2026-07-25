package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import dev.devce.rocketnautics.api.peripherals.PeripheralRegistry;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Base abstract class for all thruster block entities.
 * Consolidates common thruster variables, implements necessary capability interfaces,
 * and handles registration to the position-independent PeripheralRegistry.
 */
public abstract class AbstractThrusterBlockEntity extends SmartBlockEntity 
        implements BlockEntitySubLevelActor, IThruster, com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation {

    public int ignitionTicks = 0;
    protected Object soundInstance;
    private java.util.UUID uniqueId = java.util.UUID.randomUUID();

    public AbstractThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public java.util.UUID getUniqueId() {
        return uniqueId;
    }

    /**
     * Get the warm up/ignition ticks required for this engine.
     */
    public abstract int getWarmupTime();

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            PeripheralRegistry.register(level, this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null) {
            if (!level.isClientSide) {
                PeripheralRegistry.unregister(level, this);
            } else {
                dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(getBlockPos());
            }
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null) {
            if (!level.isClientSide) {
                PeripheralRegistry.unregister(level, this);
            } else {
                dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(getBlockPos());
            }
        }
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("UniqueId", uniqueId);
        tag.putInt("IgnitionTicks", ignitionTicks);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("UniqueId")) {
            uniqueId = tag.getUUID("UniqueId");
        }
        ignitionTicks = tag.getInt("IgnitionTicks");
    }

    @Override
    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        // Inflate the bounding box by 10 blocks in all directions to prevent frustum culling
        // from clipping the long volumetric exhaust plume when the engine block leaves the screen.
        return new net.minecraft.world.phys.AABB(this.worldPosition).inflate(10.0);
    }
}
