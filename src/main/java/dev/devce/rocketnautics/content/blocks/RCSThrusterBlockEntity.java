package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;

import java.util.List;

public class RCSThrusterBlockEntity extends AbstractThrusterBlockEntity {
    public ThrustBehaviour thrust;
    public boolean currentlyBurning = false;
    private boolean computerActive = false;

    public RCSThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        thrust = new ThrustBehaviour(this)
                .withType(ThrustBehaviour.EngineType.RCS)
                .withOffset(new Vec3(0.5, 0.5, 0.5));
        behaviours.add(thrust);
    }

    @Override
    public ScrollValueBehaviour getThrustPower() {
        return null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        boolean active = isActive();
        if (!level.isClientSide) {
            if (active != currentlyBurning) {
                currentlyBurning = active;
                sendData();
            }
        }

        Direction facing = getThrustDirection();
        thrust.withOffset(new Vec3(0.5, 0.5, 0.5).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5));
        thrust.update(
                (float) calculateRcsThrust(),
                active ? 1.0f : 0.0f,
                new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                active
        );
    }

    @Override
    public void sable$physicsTick(ServerSubLevel serverSubLevel, RigidBodyHandle handle, double deltaTime) {
        thrust.applyPhysicsForce(handle, deltaTime);
    }

    @Override
    public int getWarmupTime() {
        return 0;
    }

    @Override
    public boolean isActive() {
        if (level == null) return false;
        if (level.isClientSide) return currentlyBurning;
        return level.hasNeighborSignal(worldPosition) || computerActive;
    }

    private float computerThrottle = 0.0f;

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
        // RCS has no gimbal
    }

    @Override
    public float getFlow() {
        return isActive() ? 1.0f : 0.0f;
    }

    private double calculateRcsThrust() {
        if (level == null) return 0;

        double maxThrust = 105.0;
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

        if (y < 5000) {
            if (y <= 2000) {
                maxThrust = 12.0;
            } else {
                double factor = (y - 2000.0) / 3000.0;
                maxThrust = 12.0 + (93.0 * factor);
            }
        }
        
        if (computerActive) {
            return computerThrottle * maxThrust;
        }
        
        return Math.min(7 * level.getBestNeighborSignal(worldPosition), maxThrust);
    }

    public Direction getThrustDirection() {
        return getBlockState().getValue(RocketThrusterBlock.FACING);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Burning", currentlyBurning);
        tag.putBoolean("ComputerActive", computerActive);
        tag.putFloat("ComputerThrottle", computerThrottle);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        currentlyBurning = tag.getBoolean("Burning");
        computerActive = tag.getBoolean("ComputerActive");
        computerThrottle = tag.getFloat("ComputerThrottle");
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Engine ID: ")
                .append(Component.literal(String.valueOf(getPeripheralId())).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.status")).append(": ")
                .append(isActive() ? Component.translatable("rocketnautics.goggles.active").withStyle(net.minecraft.ChatFormatting.GREEN) :
                        Component.translatable("rocketnautics.goggles.inactive").withStyle(net.minecraft.ChatFormatting.RED)));

        double thrustForce = calculateRcsThrust();
        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.thrust")).append(": ")
                .append(Component.literal(String.format("%.1f N", thrustForce)).withStyle(net.minecraft.ChatFormatting.GOLD)));

        return true;
    }

    @Override
    public String getPeripheralType() {
        return "rcs";
    }
}
