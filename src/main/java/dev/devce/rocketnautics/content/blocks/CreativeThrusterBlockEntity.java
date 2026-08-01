package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class CreativeThrusterBlockEntity extends AbstractThrusterBlockEntity {

    public ScrollValueBehaviour minThrust;
    public ScrollValueBehaviour maxThrust;
    public ThrustBehaviour thrust;

    public boolean currentlyBurning = false;
    public float fuelThrottle = 0.0f;

    @Override
    public ScrollValueBehaviour getThrustPower() {
        return maxThrust;
    }

    @Override
    public int getWarmupTime() {
        return 0;
    }

    public CreativeThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        minThrust = new ScrollValueBehaviour(
                Component.translatable("gui.rocketnautics.min_thrust"),
                this,
                new CenteredSideValueBoxTransform(
                        (state, direction) -> direction != state.getValue(CreativeThrusterBlock.FACING)));
        minThrust.between(0, 200);
        minThrust.withFormatter(v -> (v * 50) + " N");
        minThrust.setValue(0);

        maxThrust = new ScrollValueBehaviour(
                Component.translatable("gui.rocketnautics.max_thrust"),
                this,
                new CenteredSideValueBoxTransform(
                        (state, direction) -> direction != state.getValue(CreativeThrusterBlock.FACING)));
        maxThrust.between(0, 200);
        maxThrust.withFormatter(v -> (v * 50) + " N");
        maxThrust.setValue(200);

        thrust = new ThrustBehaviour(this)
                .withType(ThrustBehaviour.EngineType.ROCKET)
                .withOffset(new Vec3(0.5, 0.5, 0.5));

        behaviours.add(minThrust);
        behaviours.add(maxThrust);
        behaviours.add(thrust);
    }

    public int getCurrentPower() {
        if (!isActive())
            return 0;

        int min = minThrust.getValue();
        int max = maxThrust.getValue();
        if (min > max)
            min = max;

        return (int) (min + (max - min) * fuelThrottle);
    }

    @Override
    public boolean isActive() {
        return currentlyBurning;
    }

    protected void updateActiveState() {
        if (level == null || level.isClientSide)
            return;

        boolean wasBurning = this.currentlyBurning;
        float oldThrottle = this.fuelThrottle;

        int signal = level.getBestNeighborSignal(worldPosition);
        this.fuelThrottle = signal / 15.0f;
        this.currentlyBurning = signal > 0;

        if (wasBurning != currentlyBurning || Math.abs(oldThrottle - fuelThrottle) > 0.01f) {
            sendData();
            setChanged();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        if (!level.isClientSide) {
            updateActiveState();
        }

        Direction facing = getThrustDirection();
        thrust.withOffset(new Vec3(0.5, 0.5, 0.5).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5));
        thrust.update(
                getCurrentPower() * 50.0f,
                fuelThrottle,
                new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                isActive()
        );
    }

    @Override
    public void sable$physicsTick(dev.ryanhcode.sable.sublevel.ServerSubLevel serverSubLevel, dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle handle, double deltaTime) {
        thrust.applyPhysicsForce(handle, deltaTime);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putBoolean("Burning", currentlyBurning);
        tag.putFloat("FuelThrottle", fuelThrottle);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        currentlyBurning = tag.getBoolean("Burning");
        fuelThrottle = tag.getFloat("FuelThrottle");
    }

    public Direction getThrustDirection() {
        BlockState state = getBlockState();
        if (state.getBlock() instanceof CreativeThrusterBlock) {
            return state.getValue(CreativeThrusterBlock.FACING);
        }
        return Direction.UP;
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(
                Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId())
                        .withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Engine ID: ")
                .append(Component.literal(String.valueOf(getPeripheralId())).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.status")).append(": ")
                .append(isActive()
                        ? Component.translatable("rocketnautics.goggles.active")
                                .withStyle(net.minecraft.ChatFormatting.GREEN)
                        : Component.translatable("rocketnautics.goggles.inactive")
                                .withStyle(net.minecraft.ChatFormatting.RED)));

        int power = getCurrentPower();
        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.thrust")).append(": ")
                .append(Component.literal(power * 50 + " N").withStyle(net.minecraft.ChatFormatting.GOLD)));

        return true;
    }

    @Override
    public float getFlow() {
        return fuelThrottle;
    }

    @Override
    public void setActive(boolean active) {
        this.currentlyBurning = active;
        notifyUpdate();
    }

    @Override
    public void setThrottle(float throttle) {
        this.fuelThrottle = Math.max(0.0f, Math.min(1.0f, throttle));
        setChanged();
        sendData();
    }

    @Override
    public void setGimbal(double pitch, double yaw) {
        // No gimbal
    }

    @Override
    public String getPeripheralType() {
        return "thruster";
    }
}
