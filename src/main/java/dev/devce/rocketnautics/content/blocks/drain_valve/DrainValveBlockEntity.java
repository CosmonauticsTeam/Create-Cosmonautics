package dev.devce.rocketnautics.content.blocks.drain_valve;

import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import dev.devce.rocketnautics.content.fluids.ITankPressure;
import dev.devce.rocketnautics.registry.RocketParticles;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.lang.ref.WeakReference;
import java.util.List;
import java.util.Locale;

public class DrainValveBlockEntity extends SmartBlockEntity implements IHaveGoggleInformation {
    public WeakReference<FluidTankBlockEntity> targetTank = new WeakReference<>(null);
    private int soundCooldown = 0;

    public DrainValveBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
    }

    public boolean isPowered() {
        return getBlockState().hasProperty(DrainValveBlock.POWERED) && getBlockState().getValue(DrainValveBlock.POWERED);
    }

    public FluidTankBlockEntity getTank() {
        FluidTankBlockEntity tank = targetTank.get();
        if (tank == null || tank.isRemoved()) {
            targetTank = new WeakReference<>(null);
            if (!getBlockState().hasProperty(DrainValveBlock.FACING))
                return null;
            Direction attachedDir = getBlockState().getValue(DrainValveBlock.FACING).getOpposite();
            if (level == null)
                return null;
            BlockEntity be = level.getBlockEntity(worldPosition.relative(attachedDir));
            if (be instanceof FluidTankBlockEntity tankBe) {
                targetTank = new WeakReference<>(tank = tankBe);
            }
        }
        if (tank == null)
            return null;
        return tank.getControllerBE();
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null)
            return;

        boolean powered = isPowered();
        FluidTankBlockEntity tank = getTank();
        boolean isCreative = tank instanceof CreativeFluidTankBlockEntity;

        if (powered && tank != null && !isCreative) {
            if (tank instanceof ITankPressure pressureTank) {
                if (!level.isClientSide) {
                    pressureTank.ventPressure(0.04f);
                    pressureTank.setVenting(true);
                    FluidTankBlockEntity controller = tank.getControllerBE();
                    if (controller != null) {
                        controller.getTank(0).drain(20, IFluidHandler.FluidAction.EXECUTE);
                    }
                } else {
                    spawnSteamParticles();
                    tickAudio();
                }
            }
        } else if (!level.isClientSide && tank instanceof ITankPressure pressureTank) {
            pressureTank.setVenting(false);
        }

        if (soundCooldown > 0)
            soundCooldown--;
    }

    private void tickAudio() {
        if (soundCooldown <= 0 && level != null) {
            AllSoundEvents.STEAM.playAt(level, worldPosition, 0.45f, 1.25f, false);
            soundCooldown = 15;
        }
    }

    private void spawnSteamParticles() {
        if (level == null || !getBlockState().hasProperty(DrainValveBlock.FACING))
            return;
        Direction facing = getBlockState().getValue(DrainValveBlock.FACING);
        RandomSource random = level.getRandom();
        Vec3 center = Vec3.atCenterOf(worldPosition).add(
                facing.getStepX() * 0.4,
                facing.getStepY() * 0.4,
                facing.getStepZ() * 0.4
        );

        int count = 2 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            double px = center.x + (random.nextDouble() - 0.5) * 0.06;
            double py = center.y + (random.nextDouble() - 0.5) * 0.06;
            double pz = center.z + (random.nextDouble() - 0.5) * 0.06;

            double vx, vy, vz;
            if (facing.getAxis().isHorizontal()) {
                double forwardSpeed = 0.09 + random.nextDouble() * 0.05;
                vx = facing.getStepX() * forwardSpeed + (random.nextDouble() - 0.5) * 0.03;
                vz = facing.getStepZ() * forwardSpeed + (random.nextDouble() - 0.5) * 0.03;
                vy = -0.03 - random.nextDouble() * 0.04;
            } else if (facing == Direction.DOWN) {
                vx = (random.nextDouble() - 0.5) * 0.04;
                vz = (random.nextDouble() - 0.5) * 0.04;
                vy = -0.10 - random.nextDouble() * 0.05;
            } else {
                vx = (random.nextDouble() - 0.5) * 0.05;
                vz = (random.nextDouble() - 0.5) * 0.05;
                vy = 0.06 + random.nextDouble() * 0.04;
            }

            level.addParticle(RocketParticles.VENT_STEAM.get(), px, py, pz, vx, vy, vz);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ")
                .append(Component.translatable("rocketnautics.goggles.drain_valve.title").withStyle(ChatFormatting.GOLD)));
        boolean powered = isPowered();
        tooltip.add(Component.literal("  ")
                .append(Component.translatable("rocketnautics.goggles.drain_valve.status").withStyle(ChatFormatting.GRAY))
                .append(": ")
                .append(powered
                        ? Component.translatable("rocketnautics.goggles.drain_valve.venting").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("rocketnautics.goggles.drain_valve.closed").withStyle(ChatFormatting.DARK_GRAY)));

        FluidTankBlockEntity tank = getTank();
        if (tank instanceof CreativeFluidTankBlockEntity) {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.tank.pressure").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal("Immune (Creative Tank)").withStyle(ChatFormatting.GREEN)));
        } else if (tank instanceof ITankPressure pressureTank) {
            float pressure = pressureTank.getPressure();
            float max = pressureTank.getMaxPressure();
            ChatFormatting color = pressure >= max ? ChatFormatting.RED
                    : (pressure > max * 0.7f ? ChatFormatting.YELLOW : ChatFormatting.AQUA);
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.tank.pressure").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal(String.format(Locale.ROOT, "%.2f / %.1f bar", pressure, max)).withStyle(color)));
        } else {
            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.drain_valve.no_tank").withStyle(ChatFormatting.RED)));
        }
        return true;
    }
}
