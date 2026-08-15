package dev.devce.rocketnautics.content.blocks.solar;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.SableSubLevelLightingHandler;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

/**
 * BlockEntity for the Solar Panel.
 * Generates Forge Energy based on sunlight incidence angle and supports Create's Goggles tooltip.
 */
public class SolarPanelBlockEntity extends BlockEntity implements IHaveGoggleInformation {

    public static final int MAX_SPACE_GENERATION = 160; // Max FE/t in Deep Space at optimal 90 deg angle
    public static final int MAX_OVERWORLD_GENERATION = 60; // Max FE/t in Overworld during clear midday

    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(10000, 200);
    private int currentGeneration = 0;
    private float efficiency = 0.0f;

    public SolarPanelBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void tick() {
        if (level == null) return;

        if (!level.isClientSide) {
            int generated = computeGeneration();
            if (generated > 0) {
                energyStorage.receiveEnergyInternal(generated);
            }
            this.currentGeneration = generated;

            pushEnergy();

            if (level.getGameTime() % 10 == 0) {
                setChanged();
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
            }
        }
    }

    private int computeGeneration() {
        if (level == null) return 0;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof SolarPanelBlock)) return 0;
        Direction facing = state.getValue(SolarPanelBlock.FACING);

        // Check if facing top is obstructed by a solid block
        BlockPos frontPos = worldPosition.relative(facing);
        if (level.getBlockState(frontPos).isSolidRender(level, frontPos)) {
            this.efficiency = 0.0f;
            return 0;
        }

        if (DeepSpaceHelper.isDeepSpace(level)) {
            // Deep Space generation: depends purely on the angle between the panel normal and the star/sun
            Vec3 panelNormal = getPanelWorldNormal(facing);
            Vec3 sunDir = getSunDirection();
            double dot = panelNormal.dot(sunDir);

            if (dot > 0.0) {
                this.efficiency = (float) Math.min(1.0, dot);
                return (int) Math.round(MAX_SPACE_GENERATION * efficiency);
            } else {
                this.efficiency = 0.0f;
                return 0;
            }
        } else {
            // Overworld / planetary atmosphere generation
            if (!level.canSeeSky(frontPos)) {
                this.efficiency = 0.0f;
                return 0;
            }

            float sunAngle = level.getSunAngle(1.0f);
            double sunFactor = Math.max(0.0, Math.cos(sunAngle));
            if (level.isRaining()) sunFactor *= 0.35;
            if (level.isThundering()) sunFactor *= 0.15;

            double orientFactor = (facing == Direction.UP) ? 1.0 : (facing == Direction.DOWN ? 0.0 : 0.6);
            double totalEfficiency = sunFactor * orientFactor;

            this.efficiency = (float) Math.min(1.0, totalEfficiency);
            return (int) Math.round(MAX_OVERWORLD_GENERATION * efficiency);
        }
    }

    private Vec3 getPanelWorldNormal(Direction facing) {
        if (level != null) {
            SubLevel subLevel = Sable.HELPER.getContaining(level, worldPosition);
            if (subLevel != null) {
                Quaterniond rot = subLevel.logicalPose().orientation();
                Vector3d localNorm = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                Vector3d worldNorm = rot.transform(localNorm, new Vector3d());
                return new Vec3(worldNorm.x, worldNorm.y, worldNorm.z);
            }
        }
        return new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ());
    }

    private Vec3 getSunDirection() {
        if (level != null && level.isClientSide) {
            return new Vec3(
                SableSubLevelLightingHandler.getSunX(),
                SableSubLevelLightingHandler.getSunY(),
                SableSubLevelLightingHandler.getSunZ()
            ).normalize();
        }

        if (DeepSpaceHandler.hasReceivedPosition() && DeepSpaceHandler.getUniverse() != null) {
            try {
                org.orekit.time.AbsoluteDate renderDate = DeepSpaceHandler.getRenderDate(0.0f);
                Vector3D shipPos = DeepSpaceHandler.getReceivedPosition().getPosition(renderDate);
                org.orekit.frames.Frame shipFrame = DeepSpaceHandler.getReceivedPosition().getFrame();

                return DeepSpaceHandler.getUniverse().getPlanets().stream()
                    .filter(p -> p.extras() != null && p.extras().star())
                    .map(sol -> {
                        try {
                            Vector3D dir = sol.posInMyFrame(renderDate, shipPos, shipFrame).negate();
                            return new Vec3(dir.getX(), dir.getY(), dir.getZ()).normalize();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(v -> v != null)
                    .findFirst()
                    .orElse(new Vec3(0.577, 0.707, 0.408).normalize());
            } catch (Exception ignored) {}
        }
        return new Vec3(0.577, 0.707, 0.408).normalize();
    }

    private void pushEnergy() {
        if (energyStorage.getEnergyStored() <= 0 || level == null) return;
        Direction facing = getBlockState().getValue(SolarPanelBlock.FACING);

        // Push energy to neighbor blocks on all sides except the front active surface
        for (Direction d : Direction.values()) {
            if (d == facing) continue;
            BlockPos targetPos = worldPosition.relative(d);
            IEnergyStorage target = level.getCapability(Capabilities.EnergyStorage.BLOCK, targetPos, d.getOpposite());
            if (target != null && target.canReceive()) {
                int toExtract = Math.min(energyStorage.getEnergyStored(), 100);
                int simulated = target.receiveEnergy(toExtract, true);
                if (simulated > 0) {
                    int drained = energyStorage.extractEnergy(simulated, false);
                    target.receiveEnergy(drained, false);
                    if (energyStorage.getEnergyStored() <= 0) break;
                }
            }
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.GOLD)));

        if (currentGeneration > 0) {
            tooltip.add(Component.literal("  Status: ")
                .append(Component.literal("Generating").withStyle(ChatFormatting.GREEN)));
            int pct = Math.round(efficiency * 100.0f);
            ChatFormatting effColor = pct >= 75 ? ChatFormatting.GREEN : (pct >= 35 ? ChatFormatting.YELLOW : ChatFormatting.GOLD);
            tooltip.add(Component.literal("  Efficiency: ")
                .append(Component.literal(pct + "%").withStyle(effColor)));
            tooltip.add(Component.literal("  Output: ")
                .append(Component.literal(currentGeneration + " FE/t").withStyle(ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Status: ")
                .append(Component.literal("Inactive (No Sunlight)").withStyle(ChatFormatting.RED)));
            tooltip.add(Component.literal("  Efficiency: ")
                .append(Component.literal("0%").withStyle(ChatFormatting.GRAY)));
        }

        tooltip.add(Component.literal("  Stored: ")
            .append(Component.literal(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored() + " FE").withStyle(ChatFormatting.WHITE)));

        return true;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("Generation", currentGeneration);
        tag.putFloat("Efficiency", efficiency);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energyStorage.setEnergy(tag.getInt("Energy"));
        this.currentGeneration = tag.getInt("Generation");
        this.efficiency = tag.getFloat("Efficiency");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public static class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int capacity, int maxTransfer) {
            super(capacity, 0, maxTransfer);
        }

        public int receiveEnergyInternal(int maxReceive) {
            int energyReceived = Math.min(capacity - energy, maxReceive);
            energy += energyReceived;
            return energyReceived;
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }
    }
}
