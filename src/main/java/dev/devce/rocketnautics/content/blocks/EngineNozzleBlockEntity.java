package dev.devce.rocketnautics.content.blocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class EngineNozzleBlockEntity extends BlockEntity {
    /** Server-authoritative heat value, 0.0–2.5 */
    public float heat = 0.0f;
    /** Client-only smoothed heat used by the renderer for interpolation */
    public float smoothedHeat = 0.0f;

    private int syncCooldown = 0;

    public EngineNozzleBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, EngineNozzleBlockEntity be) {
        Direction facing = state.getValue(EngineNozzleBlock.FACING);
        // Mount is 2 blocks behind the nozzle (opposite of exhaust direction)
        BlockPos mountPos = pos.relative(facing.getOpposite(), 2);

        boolean isThrusting = false;
        float heatModifier = 1.0f;
        int nozzleType = state.getValue(EngineNozzleBlock.NOZZLE_TYPE);

        if (level.getBlockEntity(mountPos) instanceof ThrusterMountBlockEntity mount) {
            isThrusting = mount.isThrusting;
            heatModifier = mount.getHeatModifier();
            float maxLimit = 200 * mount.getThrustModifier();
            float throttle = maxLimit > 0 ? (mount.thrustLimit.getValue() / maxLimit) : 0f;
            heatModifier *= throttle;
        }

        if (isThrusting) {
            be.heat = Math.min(2.5f, be.heat + 0.006f * heatModifier);
        } else {
            be.heat = Math.max(0.0f, be.heat - 0.008f);
        }

        if (!level.isClientSide) {
            // Copper nozzles overheat and explode
            if (nozzleType == 1 && be.heat > 1.0f) {
                if (level.random.nextFloat() < (be.heat - 1.0f) * 0.05f) {
                    level.destroyBlock(pos, true);
                    level.playSound(null, pos, net.minecraft.sounds.SoundEvents.FIRE_EXTINGUISH,
                            net.minecraft.sounds.SoundSource.BLOCKS, 1.0f, 1.0f);
                }
            }

            // Sync heat to clients every 5 ticks so the renderer has up-to-date data
            be.syncCooldown--;
            if (be.syncCooldown <= 0) {
                be.syncCooldown = 5;
                level.sendBlockUpdated(pos, state, state, 2);
            }
        } else {
            // Smooth the heat value on the client for butter-smooth rendering
            be.smoothedHeat = be.smoothedHeat + (be.heat - be.smoothedHeat) * 0.15f;
            dev.devce.rocketnautics.client.render.ExhaustClientRenderer.tickClientThruster(be);
        }
    }

    // ── Sync: send heat value to clients via block update packet ──────────────

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putFloat("Heat", heat);
        return tag;
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt, HolderLookup.Provider lookup) {
        if (pkt.getTag() != null && pkt.getTag().contains("Heat")) {
            heat = pkt.getTag().getFloat("Heat");
        }
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putFloat("Heat", heat);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        heat = tag.getFloat("Heat");
        smoothedHeat = heat; // Avoid jump on load
    }

    public net.minecraft.world.phys.AABB getRenderBoundingBox() {
        // Inflate the bounding box by 10 blocks in all directions to prevent frustum culling
        // from clipping the long volumetric exhaust plume when the engine block leaves the screen.
        return new net.minecraft.world.phys.AABB(this.worldPosition).inflate(10.0);
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && level.isClientSide) {
            dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(level, getBlockPos());
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && level.isClientSide) {
            dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(level, getBlockPos());
        }
    }
}
