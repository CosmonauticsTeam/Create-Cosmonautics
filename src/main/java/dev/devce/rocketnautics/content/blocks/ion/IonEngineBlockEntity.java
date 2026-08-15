package dev.devce.rocketnautics.content.blocks.ion;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.devce.rocketnautics.content.blocks.AbstractThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.ThrustBehaviour;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * BlockEntity for the Ion Engine.
 * High-efficiency electrical propulsion powered by Forge Energy and throttled by redstone.
 */
public class IonEngineBlockEntity extends AbstractThrusterBlockEntity {

    public static final int MAX_ENERGY_USAGE = 160; // 160 FE/t at 100% throttle (15 redstone)
    public static final double MAX_VACUUM_THRUST = 180.0; // 180 N at 100% throttle in vacuum

    public ThrustBehaviour thrust;
    public boolean currentlyBurning = false;
    private boolean computerActive = false;
    private float computerThrottle = 0.0f;
    private boolean energyShortage = false;
    private boolean obstructed = false;
    private int currentEnergyUsage = 0;
    private double currentThrustCalculated = 0.0;

    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(10000, 500);

    public IonEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        thrust = new ThrustBehaviour(this)
                .withType(ThrustBehaviour.EngineType.ION)
                .withOffset(new Vec3(0.5, 0.5, 0.5));
        behaviours.add(thrust);
    }

    @Override
    public String getPeripheralType() {
        return "ion_thruster";
    }

    @Override
    public ScrollValueBehaviour getThrustPower() {
        return null; // Pure redstone/computer control, no scroll display
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        Direction facing = getThrustDirection();
        BlockPos exhaustPos = worldPosition.relative(facing);
        this.obstructed = level.getBlockState(exhaustPos).isSolidRender(level, exhaustPos);

        float throttle = getEffectiveThrottle();
        boolean shouldBurn = throttle > 0.0f && !obstructed;

        if (!level.isClientSide) {
            if (shouldBurn) {
                int requiredFE = Math.max(1, (int) Math.round(MAX_ENERGY_USAGE * throttle));
                if (energyStorage.getEnergyStored() >= requiredFE) {
                    energyStorage.extractEnergyInternal(requiredFE);
                    this.energyShortage = false;
                    this.currentlyBurning = true;
                    this.currentEnergyUsage = requiredFE;
                    this.currentThrustCalculated = calculateIonThrust(throttle);
                } else {
                    this.energyShortage = true;
                    this.currentlyBurning = false;
                    this.currentEnergyUsage = 0;
                    this.currentThrustCalculated = 0.0;
                }
            } else {
                this.energyShortage = false;
                this.currentlyBurning = false;
                this.currentEnergyUsage = 0;
                this.currentThrustCalculated = 0.0;
            }

            if (level.getGameTime() % 4 == 0) {
                sendData();
            }
        }

        // 4px flat block: active emitter surface is 4px (0.25 blocks) from mounting block face
        Vec3 faceExit = new Vec3(0.5, 0.5, 0.5).add(
            facing.getStepX() * (-0.25),
            facing.getStepY() * (-0.25),
            facing.getStepZ() * (-0.25)
        );
        thrust.withOffset(faceExit);
        thrust.update(
                (float) currentThrustCalculated,
                currentlyBurning ? throttle : 0.0f,
                new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                currentlyBurning
        );
    }

    @Override
    public void sable$physicsTick(ServerSubLevel serverSubLevel, RigidBodyHandle handle, double deltaTime) {
        if (currentlyBurning && !obstructed) {
            thrust.applyPhysicsForce(handle, deltaTime);
        }
    }

    @Override
    public int getWarmupTime() {
        return 0;
    }

    @Override
    public boolean isActive() {
        return currentlyBurning;
    }

    public float getEffectiveThrottle() {
        if (level == null) return 0.0f;
        if (computerActive) return Math.min(1.0f, Math.max(0.0f, computerThrottle));
        int signal = level.getBestNeighborSignal(worldPosition);
        return signal > 0 ? (signal / 15.0f) : 0.0f;
    }

    @Override
    public void setActive(boolean active) {
        this.computerActive = active;
        notifyUpdate();
    }

    @Override
    public void setThrottle(float throttle) {
        this.computerThrottle = throttle;
        notifyUpdate();
    }

    @Override
    public void setGimbal(double pitch, double yaw) {
        // Ion Engine has fixed thrust alignment
    }

    @Override
    public float getFlow() {
        return isActive() ? getEffectiveThrottle() : 0.0f;
    }

    private double calculateIonThrust(float throttle) {
        if (level == null) return 0.0;

        double maxThrust = MAX_VACUUM_THRUST;
        double y = 0;
        if (level.isClientSide) {
            dev.ryanhcode.sable.sublevel.ClientSubLevel clientSubLevel = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(this);
            if (clientSubLevel != null) {
                y = clientSubLevel.logicalPose().position().y;
            } else {
                y = worldPosition.getY();
            }
        } else {
            dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
            if (ship != null) {
                y = ship.logicalPose().position().y;
            } else {
                y = worldPosition.getY();
            }
        }

        // Atmosphere thrust dropoff (cannot lift heavy craft from planetary ground)
        if (y < 5000) {
            if (y <= 2000) {
                maxThrust = 15.0;
            } else {
                double factor = (y - 2000.0) / 3000.0;
                maxThrust = 15.0 + ((MAX_VACUUM_THRUST - 15.0) * factor);
            }
        }

        return throttle * maxThrust;
    }

    public Direction getThrustDirection() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof IonEngineBlock) {
            return state.getValue(IonEngineBlock.FACING);
        }
        return Direction.UP;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Engine ID: ")
                .append(Component.literal(String.valueOf(getPeripheralId())).withStyle(ChatFormatting.GOLD)));

        if (obstructed) {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Obstructed (Exhaust Blocked)").withStyle(ChatFormatting.RED)));
        } else if (currentlyBurning) {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Thrusting").withStyle(ChatFormatting.GREEN)));
        } else if (energyShortage) {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Insufficient FE Energy").withStyle(ChatFormatting.RED)));
        } else {
            tooltip.add(Component.literal("  Status: ")
                    .append(Component.literal("Inactive (No Redstone)").withStyle(ChatFormatting.DARK_GRAY)));
        }

        int signal = level != null ? level.getBestNeighborSignal(worldPosition) : 0;
        int pct = Math.round(getEffectiveThrottle() * 100.0f);
        tooltip.add(Component.literal("  Signal: ")
                .append(Component.literal(signal + "/15 (" + pct + "%)").withStyle(signal > 0 ? ChatFormatting.AQUA : ChatFormatting.GRAY)));

        tooltip.add(Component.literal("  Thrust: ")
                .append(Component.literal(String.format("%.1f N", currentThrustCalculated)).withStyle(ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Consumption: ")
                .append(Component.literal(currentEnergyUsage + " FE/t").withStyle(ChatFormatting.AQUA)));

        tooltip.add(Component.literal("  Stored FE: ")
                .append(Component.literal(energyStorage.getEnergyStored() + " / " + energyStorage.getMaxEnergyStored() + " FE").withStyle(ChatFormatting.WHITE)));

        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Burning", currentlyBurning);
        tag.putBoolean("ComputerActive", computerActive);
        tag.putFloat("ComputerThrottle", computerThrottle);
        tag.putBoolean("EnergyShortage", energyShortage);
        tag.putBoolean("Obstructed", obstructed);
        tag.putInt("Energy", energyStorage.getEnergyStored());
        tag.putInt("EnergyUsage", currentEnergyUsage);
        tag.putDouble("ThrustVal", currentThrustCalculated);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        this.currentlyBurning = tag.getBoolean("Burning");
        this.computerActive = tag.getBoolean("ComputerActive");
        this.computerThrottle = tag.getFloat("ComputerThrottle");
        this.energyShortage = tag.getBoolean("EnergyShortage");
        this.obstructed = tag.getBoolean("Obstructed");
        energyStorage.setEnergy(tag.getInt("Energy"));
        this.currentEnergyUsage = tag.getInt("EnergyUsage");
        this.currentThrustCalculated = tag.getDouble("ThrustVal");
    }

    public static class CustomEnergyStorage extends EnergyStorage {
        public CustomEnergyStorage(int capacity, int maxReceive) {
            super(capacity, maxReceive, 0);
        }

        public void extractEnergyInternal(int amount) {
            this.energy = Math.max(0, this.energy - amount);
        }

        public void setEnergy(int energy) {
            this.energy = energy;
        }
    }
}
