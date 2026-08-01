package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketTags;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.NotNull;
import net.minecraft.world.phys.Vec3;

import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform;
import java.util.List;

public class ThrusterMountBlockEntity extends SmartBlockEntity
        implements IHaveGoggleInformation, BlockEntitySubLevelActor, IThruster {

    public boolean hasPipes = false;
    public int pipeType = 0; // 0 = Closed, 1 = Open, 2 = Full-flow, 3 = Expander
    public int nozzleType = 0; // 0 = None, 1 = Copper, 2 = Titanium
    public int inputs = 1;

    public final FluidTank tank1 = new FluidTank(1000,
            stack -> this.isRocketFuel(stack) || this.isOxidizer(stack));
    public final FluidTank tank2 = new FluidTank(1000,
            stack -> this.isRocketFuel(stack) || this.isOxidizer(stack));

    public boolean isThrusting = false;
    public boolean lastThrusting = false;
    public int thrustTicks = 0;
    public ThrustBehaviour thrust;

    public final IFluidHandler combinedFluidHandler = new IFluidHandler() {
        @Override
        public int getTanks() {
            return 2;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return tank == 0 ? tank1.getFluid() : tank2.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank == 0 ? tank1.isFluidValid(stack) : tank2.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int filled = tank1.fill(resource, action);
            if (filled > 0)
                return filled;
            return tank2.fill(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            FluidStack drained = tank1.drain(resource, action);
            if (!drained.isEmpty())
                return drained;
            return tank2.drain(resource, action);
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            FluidStack drained = tank1.drain(maxDrain, action);
            if (!drained.isEmpty())
                return drained;
            return tank2.drain(maxDrain, action);
        }
    };

    public Direction getInput1(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y)
            return Direction.EAST;
        return facing.getClockWise();
    }

    public Direction getInput2(Direction facing) {
        if (facing.getAxis() == Direction.Axis.Y)
            return Direction.WEST;
        return facing.getCounterClockWise();
    }

    public ScrollValueBehaviour thrustLimit;

    public ThrusterMountBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(
            List<com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour> behaviours) {
        thrust = new ThrustBehaviour(this)
                .withType(ThrustBehaviour.EngineType.ROCKET);

        thrustLimit = new ScrollValueBehaviour(
                Component.translatable("gui.rocketnautics.max_thrust"),
                this,
                new ThrusterMountValueBox());
        thrustLimit.between(0, 200);
        thrustLimit.withFormatter(v -> (v * 50) + " N");
        thrustLimit.setValue(200);

        behaviours.add(thrust);
        behaviours.add(thrustLimit);
    }

    @Override
    public com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour getThrustPower() {
        return thrustLimit;
    }

    @Override
    public boolean isActive() {
        return isThrusting;
    }

    @Override
    public void setActive(boolean active) {
    }

    @Override
    public void setThrottle(float throttle) {
        if (thrustLimit != null) {
            int targetMax = (int) (200 * getThrustModifier());
            thrustLimit.setValue((int) (throttle * targetMax));
        }
    }

    @Override
    public void setGimbal(double pitch, double yaw) {
    }

    @Override
    public float getFlow() {
        return isThrusting ? 1.0f : 0.0f;
    }

    @Override
    public String getPeripheralType() {
        return "modular_thruster";
    }

    private int peripheralId = -1;

    @Override
    public int getPeripheralId() {
        return peripheralId;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            if (peripheralId == -1) {
                peripheralId = dev.devce.rocketnautics.api.peripherals.EngineIdManager.getNextPeripheralId(level);
                notifyUpdate();
            }
            dev.devce.rocketnautics.api.peripherals.PeripheralRegistry.register(level, this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null && !level.isClientSide) {
            dev.devce.rocketnautics.api.peripherals.PeripheralRegistry.unregister(level, this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            dev.devce.rocketnautics.api.peripherals.PeripheralRegistry.unregister(level, this);
        }
    }

    private java.util.UUID uniqueId = java.util.UUID.randomUUID();

    @Override
    public java.util.UUID getUniqueId() {
        return uniqueId;
    }

    public float getThrustModifier() {
        float nozzleMod = nozzleType == 2 ? 1.3f : 1.0f;
        return switch (pipeType) {
            case 2 -> 2.0f * nozzleMod; // Full-flow
            case 3 -> 0.8f * nozzleMod; // Expander
            case 0 -> 1.0f * nozzleMod; // Closed
            default -> 1.6f * nozzleMod; // Open
        };
    }

    public float getEfficiencyModifier() {
        float nozzleMod = nozzleType == 2 ? 0.9f : 1.0f;
        return switch (pipeType) {
            case 2 -> 0.8f * nozzleMod; // Full-flow: Good
            case 3 -> 0.6f * nozzleMod; // Expander: Amazing
            case 0 -> 1.0f * nozzleMod; // Closed: Standard
            default -> 1.5f * nozzleMod; // Open: Poor
        };
    }

    public float getHeatModifier() {
        float nozzleMod = nozzleType == 2 ? 0.7f : 1.0f;
        return switch (pipeType) {
            case 2 -> 2.5f * nozzleMod; // Full-flow: Extreme
            case 1 -> 0.8f * nozzleMod; // Open: Low
            case 3 -> 0.5f * nozzleMod; // Expander: Very Low
            default -> 1.0f * nozzleMod; // Closed: Standard
        };
    }

    private boolean isRocketFuel(FluidStack stack) {
        return !stack.isEmpty() && stack.is(RocketTags.FluidTags.ROCKET_FUEL.tag);
    }
    private boolean isOxidizer(FluidStack stack) {
        return !stack.isEmpty() && stack.is(RocketTags.FluidTags.OXIDIZER.tag);
    }

    public static void tick(Level level, BlockPos pos, BlockState state, ThrusterMountBlockEntity be) {
        if (level == null)
            return;

        Direction facing = state.getValue(ThrusterMountBlock.FACING);
        Direction exhaustDir = facing.getOpposite();
        BlockPos pipePos = pos.relative(exhaustDir);
        BlockPos nozzlePos = pos.relative(exhaustDir, 2);

        boolean hasPipesInWorld = level.getBlockState(pipePos).is(RocketBlocks.ENGINE_PIPES.get());
        boolean hasNozzleInWorld = level.getBlockState(nozzlePos).is(RocketBlocks.ENGINE_NOZZLE.get());

        be.hasPipes = hasPipesInWorld;
        if (hasNozzleInWorld) {
            BlockState nozzleState = level.getBlockState(nozzlePos);
            be.nozzleType = nozzleState.getValue(EngineNozzleBlock.NOZZLE_TYPE);
        } else {
            be.nozzleType = 0;
        }

        if (hasPipesInWorld) {
            be.pipeType = level.getBlockState(pipePos).getValue(EnginePipesBlock.PIPE_TYPE);
        }

        if (!level.isClientSide) {
            Direction[] horizontalDirs = new Direction[] { Direction.NORTH, Direction.SOUTH, Direction.EAST,
                    Direction.WEST };
            for (Direction d : horizontalDirs) {
                if (d.getAxis() == facing.getAxis())
                    continue;
                BlockPos neighborPos = pos.relative(d);
                if (level.getBlockEntity(neighborPos) instanceof ThrusterMountBlockEntity neighbor) {
                    if (neighbor.getBlockState().getValue(ThrusterMountBlock.FACING) == facing) {
                        FluidStack ourFluid1 = be.tank1.getFluid();
                        if (!ourFluid1.isEmpty()) {
                            FluidTank targetTank = null;
                            if (neighbor.tank1.isEmpty()
                                    || neighbor.tank1.getFluid().getFluid().isSame(ourFluid1.getFluid()))
                                targetTank = neighbor.tank1;
                            else if (neighbor.tank2.isEmpty()
                                    || neighbor.tank2.getFluid().getFluid().isSame(ourFluid1.getFluid()))
                                targetTank = neighbor.tank2;

                            if (targetTank != null) {
                                int total = be.tank1.getFluidAmount() + targetTank.getFluidAmount();
                                int avg = total / 2;
                                if (be.tank1.getFluidAmount() > avg + 1) {
                                    int toMove = be.tank1.getFluidAmount() - avg;
                                    FluidStack drained = be.tank1.drain(toMove, IFluidHandler.FluidAction.EXECUTE);
                                    targetTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                                }
                            }
                        }
                        FluidStack ourFluid2 = be.tank2.getFluid();
                        if (!ourFluid2.isEmpty()) {
                            FluidTank targetTank = null;
                            if (neighbor.tank1.isEmpty()
                                    || neighbor.tank1.getFluid().getFluid().isSame(ourFluid2.getFluid()))
                                targetTank = neighbor.tank1;
                            else if (neighbor.tank2.isEmpty()
                                    || neighbor.tank2.getFluid().getFluid().isSame(ourFluid2.getFluid()))
                                targetTank = neighbor.tank2;

                            if (targetTank != null) {
                                int total = be.tank2.getFluidAmount() + targetTank.getFluidAmount();
                                int avg = total / 2;
                                if (be.tank2.getFluidAmount() > avg + 1) {
                                    int toMove = be.tank2.getFluidAmount() - avg;
                                    FluidStack drained = be.tank2.drain(toMove, IFluidHandler.FluidAction.EXECUTE);
                                    targetTank.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                                }
                            }
                        }
                    }
                }
            }

            boolean hasFuel = be.tank1.getFluid().is(RocketTags.FluidTags.ROCKET_FUEL.tag)
                    || be.tank2.getFluid().is(RocketTags.FluidTags.ROCKET_FUEL.tag);
            boolean hasOxidizer = be.tank1.getFluid().is(RocketTags.FluidTags.OXIDIZER.tag)
                    || be.tank2.getFluid().is(RocketTags.FluidTags.OXIDIZER.tag);

            int targetMax = (int) (200 * be.getThrustModifier());
            if (be.thrustLimit != null) {
                if (be.thrustLimit.getValue() > targetMax || (level.getGameTime() % 20 == 0)) {
                    be.thrustLimit.between(0, targetMax);
                }
            }

            boolean canThrust = be.hasPipes && be.nozzleType > 0 && be.thrustLimit != null
                    && be.thrustLimit.getValue() > 0 && ( hasFuel && hasOxidizer);

            if (canThrust) {
                float baseConsumption = 200.0f;
                float maxLimit = 200 * be.getThrustModifier();
                float throttle = maxLimit > 0 ? (be.thrustLimit.getValue() / maxLimit) : 0f;
                int consumption = (int) Math.ceil(baseConsumption * be.getEfficiencyModifier() * throttle);
                if (consumption < 1)
                    consumption = 1;

                if (be.tank1.getFluidAmount() >= consumption && be.tank2.getFluidAmount() >= consumption) {
                    be.tank1.drain(consumption, IFluidHandler.FluidAction.EXECUTE);
                    be.tank2.drain(consumption, IFluidHandler.FluidAction.EXECUTE);
                    be.isThrusting = true;
                    be.thrustTicks++;
                } else {
                    be.isThrusting = false;
                }
            } else {
                be.isThrusting = false;
                be.thrustTicks = 0;
            }

            if (be.lastThrusting != be.isThrusting) {
                be.lastThrusting = be.isThrusting;
                be.notifyUpdate();
                be.setChanged();
            }
        }

        be.thrust.withOffset(new Vec3(0.5, 0.5, 0.5).add(
                exhaustDir.getStepX() * 2.5,
                exhaustDir.getStepY() * 2.5,
                exhaustDir.getStepZ() * 2.5));

        float force = 0.0f;
        if (be.isThrusting && be.thrustLimit != null) {
            force = be.thrustLimit.getValue() * 50.0f; // Each step is 50 N
        }

        be.thrust.update(
                force,
                be.isThrusting ? 1.0f : 0.0f,
                new Vec3(exhaustDir.getStepX(), exhaustDir.getStepY(), exhaustDir.getStepZ()),
                be.isThrusting && be.nozzleType > 0);

        be.tick();
    }

    @Override
    public void sable$physicsTick(ServerSubLevel serverSubLevel, RigidBodyHandle handle, double deltaTime) {
        thrust.applyPhysicsForce(handle, deltaTime);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("UniqueId", uniqueId);
        tag.putBoolean("HasPipes", hasPipes);
        tag.putInt("PipeType", pipeType);
        tag.putInt("NozzleType", nozzleType);
        tag.putBoolean("IsThrusting", isThrusting);
        tag.put("Tank1", tank1.writeToNBT(registries, new CompoundTag()));
        tag.put("Tank2", tank2.writeToNBT(registries, new CompoundTag()));
        tag.putInt("PeripheralId", peripheralId);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("UniqueId")) {
            uniqueId = tag.getUUID("UniqueId");
        }
        hasPipes = tag.getBoolean("HasPipes");
        pipeType = tag.getInt("PipeType");
        nozzleType = tag.getInt("NozzleType");
        isThrusting = tag.getBoolean("IsThrusting");
        if (tag.contains("Tank1"))
            tank1.readFromNBT(registries, tag.getCompound("Tank1"));
        if (tag.contains("Tank2"))
            tank2.readFromNBT(registries, tag.getCompound("Tank2"));
        if (tag.contains("PeripheralId")) {
            peripheralId = tag.getInt("PeripheralId");
        } else {
            peripheralId = -1;
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(
                Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId())
                        .withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Engine ID: ")
                .append(Component.literal(String.valueOf(getPeripheralId())).withStyle(net.minecraft.ChatFormatting.GOLD)));

        if (nozzleType > 0) {
            Component nozzleName = nozzleType == 1 ? Component.translatable("item.rocketnautics.copper_nozzle")
                    : Component.translatable("item.rocketnautics.titanium_nozzle");
            tooltip.add(Component.literal("  Nozzle: ")
                    .append(nozzleName.copy().withStyle(net.minecraft.ChatFormatting.GREEN)));
        }

        if (hasPipes) {
            Component cycle = switch (pipeType) {
                case 0 -> Component.translatable("gui.rocketnautics.cycle.closed");
                case 1 -> Component.translatable("gui.rocketnautics.cycle.open");
                case 2 -> Component.translatable("gui.rocketnautics.cycle.fullflow");
                default -> Component.translatable("gui.rocketnautics.cycle.expander");
            };
            tooltip.add(
                    Component.literal("  Cycle: ").append(cycle.copy().withStyle(net.minecraft.ChatFormatting.AQUA)));
        }

        if (isThrusting) {
            float totalThrust = thrustLimit != null ? (thrustLimit.getValue() * 0.05f) : (10.0f * getThrustModifier());
            tooltip.add(Component.literal("  Thrust: ").append(Component.literal(String.format("%.1f kN", totalThrust))
                    .withStyle(net.minecraft.ChatFormatting.YELLOW)));
            tooltip.add(Component.literal("  Efficiency: ")
                    .append(Component.literal(String.format("%.1f%%", 100.0f / getEfficiencyModifier()))
                            .withStyle(net.minecraft.ChatFormatting.AQUA)));
        }

        if (nozzleType > 0 && level != null) {
            Direction facing = getBlockState().getValue(ThrusterMountBlock.FACING);
            net.minecraft.world.level.block.entity.BlockEntity nozzleBe = level
                    .getBlockEntity(worldPosition.relative(facing.getOpposite(), 2));
            if (nozzleBe instanceof EngineNozzleBlockEntity nozzle) {
                if (nozzle.heat > 0.5f) {
                    tooltip.add(Component.literal("  Temperature: ")
                            .append(Component.literal(String.format("%.0f°C", 200 + nozzle.heat * 800))
                                    .withStyle(nozzle.heat > 1.2f ? net.minecraft.ChatFormatting.RED
                                            : net.minecraft.ChatFormatting.GOLD)));
                }
            }
        }

        // Tank 1 info
        if (!tank1.getFluid().isEmpty()) {
            tooltip.add(Component.literal("  Tank 1: ")
                    .append(Component
                            .literal(tank1.getFluid().getHoverName().getString() + " (" + tank1.getFluidAmount()
                                    + "/1000 mB)")
                            .withStyle(net.minecraft.ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Tank 1: ")
                    .append(Component.literal("Empty").withStyle(net.minecraft.ChatFormatting.GRAY)));
        }

        // Tank 2 info
        if (!tank2.getFluid().isEmpty()) {
            tooltip.add(Component.literal("  Tank 2: ")
                    .append(Component
                            .literal(tank2.getFluid().getHoverName().getString() + " (" + tank2.getFluidAmount()
                                    + "/1000 mB)")
                            .withStyle(net.minecraft.ChatFormatting.AQUA)));
        } else {
            tooltip.add(Component.literal("  Tank 2: ")
                    .append(Component.literal("Empty").withStyle(net.minecraft.ChatFormatting.GRAY)));
        }

        return true;
    }

    public static class ThrusterMountValueBox
            extends com.simibubi.create.foundation.blockEntity.behaviour.ValueBoxTransform.Sided {
        @Override
        protected boolean isSideActive(BlockState state, Direction direction) {
            Direction facing = state.getValue(ThrusterMountBlock.FACING);
            if (direction == facing || direction == facing.getOpposite())
                return false;
            Direction in1 = facing.getAxis() == Direction.Axis.Y ? Direction.EAST : facing.getClockWise();
            Direction in2 = in1.getOpposite();
            return direction != in1 && direction != in2;
        }

        @Override
        protected Vec3 getSouthLocation() {
            return net.createmod.catnip.math.VecHelper.voxelSpace(8, 13, 16.05);
        }
    }
}
