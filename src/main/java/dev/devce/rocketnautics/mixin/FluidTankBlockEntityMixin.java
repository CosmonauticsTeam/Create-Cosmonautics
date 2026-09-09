package dev.devce.rocketnautics.mixin;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.content.fluids.ITankPressure;
import dev.devce.rocketnautics.content.physics.SubLevelFuelMassTracker;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.devce.rocketnautics.registry.RocketTags;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Mixin(value = FluidTankBlockEntity.class, remap = false)
public abstract class FluidTankBlockEntityMixin extends BlockEntity implements ITankPressure {

    @Shadow
    public abstract boolean isController();

    @Shadow
    public abstract FluidTankBlockEntity getControllerBE();

    @Shadow
    public abstract int getTotalTankSize();

    @Shadow
    public abstract net.neoforged.neoforge.fluids.IFluidTank getTank(int tank);

    @Shadow
    public abstract void sendData();

    @Unique
    private float rocketnautics$pressure = 1.0f;

    @Unique
    private boolean rocketnautics$venting = false;

    @Unique
    private int rocketnautics$criticalOverpressureTicks = 0;

    @Unique
    private float rocketnautics$lastSyncedPressure = 1.0f;

    @Unique
    private int rocketnautics$lastFluidAmount = -1;

    public FluidTankBlockEntityMixin(BlockEntityType<?> type, net.minecraft.core.BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Unique
    private boolean rocketnautics$isCreative() {
        if ((Object) this instanceof CreativeFluidTankBlockEntity) {
            return true;
        }
        FluidTankBlockEntity controller = getControllerBE();
        return controller instanceof CreativeFluidTankBlockEntity;
    }

    @Override
    public float getPressure() {
        if (rocketnautics$isCreative()) {
            return 1.0f;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p)
                return p.getPressure();
        }
        return rocketnautics$pressure;
    }

    @Override
    public void setPressure(float pressure) {
        if (rocketnautics$isCreative()) {
            return;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p) {
                p.setPressure(pressure);
                return;
            }
        }
        this.rocketnautics$pressure = pressure;
    }

    @Override
    public float getMaxPressure() {
        if (rocketnautics$isCreative()) {
            return Float.MAX_VALUE;
        }
        return RocketConfig.SERVER.maxTankPressure.get().floatValue();
    }

    @Override
    public boolean isVenting() {
        if (rocketnautics$isCreative()) {
            return false;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p)
                return p.isVenting();
        }
        return rocketnautics$venting;
    }

    @Override
    public void setVenting(boolean venting) {
        if (rocketnautics$isCreative()) {
            return;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p) {
                p.setVenting(venting);
                return;
            }
        }
        this.rocketnautics$venting = venting;
    }

    @Override
    public void ventPressure(float amount) {
        if (rocketnautics$isCreative()) {
            return;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p) {
                p.ventPressure(amount);
                return;
            }
        }
        this.rocketnautics$pressure = Math.max(1.0f, this.rocketnautics$pressure - amount);
    }

    @Override
    public void addPressure(float amount) {
        if (rocketnautics$isCreative()) {
            return;
        }
        if (!isController()) {
            FluidTankBlockEntity controller = getControllerBE();
            if (controller instanceof ITankPressure p) {
                p.addPressure(amount);
                return;
            }
        }
        this.rocketnautics$pressure = Math.min(10.0f, this.rocketnautics$pressure + amount);
    }

    @Inject(method = "write", at = @At("HEAD"))
    private void rocketnautics$writePressure(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (isController() && !rocketnautics$isCreative()) {
            compound.putFloat("RocketnauticsPressure", rocketnautics$pressure);
            compound.putBoolean("RocketnauticsVenting", rocketnautics$venting);
            compound.putInt("RocketnauticsLastFluid", rocketnautics$lastFluidAmount);
        }
    }

    @Inject(method = "read", at = @At("TAIL"))
    private void rocketnautics$readPressure(CompoundTag compound, HolderLookup.Provider registries, boolean clientPacket, CallbackInfo ci) {
        if (rocketnautics$isCreative()) {
            return;
        }
        if (compound.contains("RocketnauticsPressure")) {
            rocketnautics$pressure = compound.getFloat("RocketnauticsPressure");
        }
        if (compound.contains("RocketnauticsVenting")) {
            rocketnautics$venting = compound.getBoolean("RocketnauticsVenting");
        }
        if (compound.contains("RocketnauticsLastFluid")) {
            rocketnautics$lastFluidAmount = compound.getInt("RocketnauticsLastFluid");
        }
    }

    @Inject(method = "removeController", at = @At("HEAD"))
    private void rocketnautics$onRemoveController(boolean keepFluids, CallbackInfo ci) {
        if (level != null) {
            try {
                Object obj = Sable.HELPER.getContaining(level, worldPosition);
                if (obj instanceof ServerSubLevel ssl) {
                    SubLevelFuelMassTracker.unregisterTank(ssl, (FluidTankBlockEntity) (Object) this);
                }
            } catch (Throwable ignored) {}
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void rocketnautics$tickPressure(CallbackInfo ci) {
        if (level == null || level.isClientSide || !isController() || rocketnautics$isCreative())
            return;

        SubLevel subLevel = null;
        Object lvlObj = level;
        if (lvlObj instanceof SubLevel sl) {
            subLevel = sl;
        } else {
            try {
                Object obj = Sable.HELPER.getContaining(level, worldPosition);
                if (obj instanceof SubLevel sl) {
                    subLevel = sl;
                }
            } catch (Throwable ignored) {
            }
        }

        if (subLevel instanceof ServerSubLevel ssl) {
            SubLevelFuelMassTracker.registerTank(ssl, (FluidTankBlockEntity) (Object) this);
        }

        if (!RocketConfig.SERVER.tankPressureEnabled.get())
            return;

        FluidStack fluid = getTank(0).getFluid();
        int currentFluidAmount = getTank(0).getFluidAmount();
        int capacity = getTank(0).getCapacity();
        float maxPressure = getMaxPressure();
        float targetPressureDelta = Math.max(1.0f, maxPressure - 1.0f);

        // Fueling dynamics: pressure rises proportionally to the amount of fuel filled
        if (rocketnautics$lastFluidAmount == -1) {
            rocketnautics$lastFluidAmount = currentFluidAmount;
            if (capacity > 0 && currentFluidAmount > 0 && rocketnautics$pressure <= 1.0f) {
                float fillRatio = (float) currentFluidAmount / capacity;
                rocketnautics$pressure = 1.0f + fillRatio * targetPressureDelta;
            }
        } else {
            if (currentFluidAmount > rocketnautics$lastFluidAmount && capacity > 0) {
                int added = currentFluidAmount - rocketnautics$lastFluidAmount;
                float addedRatio = (float) added / capacity;
                rocketnautics$pressure = Math.min(maxPressure * 1.5f, rocketnautics$pressure + addedRatio * targetPressureDelta);
            }
            rocketnautics$lastFluidAmount = currentFluidAmount;
        }

        double altitude = worldPosition.getY();
        if (subLevel != null) {
            altitude = subLevel.logicalPose().position().y();
        }

        double explosionAlt = RocketConfig.SERVER.tankExplosionAltitude.get();

        if (altitude > explosionAlt && rocketnautics$pressure >= maxPressure) {
            rocketnautics$criticalOverpressureTicks++;
            if (rocketnautics$criticalOverpressureTicks > 40) {
                triggerCatastrophicExplosion();
                return;
            }
        } else {
            rocketnautics$criticalOverpressureTicks = 0;
        }

        // Periodic sync to client
        if (Math.abs(rocketnautics$pressure - rocketnautics$lastSyncedPressure) > 0.05f || (level.getGameTime() % 40 == 0)) {
            rocketnautics$lastSyncedPressure = rocketnautics$pressure;
            sendData();
        }
    }

    @Inject(method = "addToGoggleTooltip", at = @At("RETURN"), cancellable = true)
    private void rocketnautics$addPressureToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking, CallbackInfoReturnable<Boolean> cir) {
        if (rocketnautics$isCreative()) {
            return;
        }

        FluidTankBlockEntity controllerBE = getControllerBE();
        if (controllerBE == null)
            return;

        if (controllerBE instanceof ITankPressure pressureTank) {
            float pressure = pressureTank.getPressure();
            float max = pressureTank.getMaxPressure();
            ChatFormatting color = pressure >= max ? ChatFormatting.RED
                    : (pressure > max * 0.7f ? ChatFormatting.YELLOW : ChatFormatting.AQUA);

            tooltip.add(Component.literal("  ")
                    .append(Component.translatable("rocketnautics.goggles.tank.pressure").withStyle(ChatFormatting.GRAY))
                    .append(": ")
                    .append(Component.literal(String.format(Locale.ROOT, "%.2f / %.1f bar", pressure, max)).withStyle(color)));

            if (pressureTank.isVenting()) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("rocketnautics.goggles.tank.venting").withStyle(ChatFormatting.GREEN)));
            } else if (pressure >= max) {
                tooltip.add(Component.literal("  ")
                        .append(Component.translatable("rocketnautics.goggles.tank.overpressure_warning").withStyle(ChatFormatting.RED)));
            }
            cir.setReturnValue(true);
        }
    }

    @Override
    public void triggerCatastrophicExplosion() {
        if (rocketnautics$isCreative()) {
            return;
        }

        FluidTankBlockEntity controller = getControllerBE();
        if (controller != null && controller != (Object) this) {
            if (controller instanceof ITankPressure p) {
                p.triggerCatastrophicExplosion();
                return;
            }
        }

        int totalSize = getTotalTankSize();
        float explosionPower = Math.min(30.0f, 4.0f + (totalSize * 0.5f));

        int searchRadius = Math.max(2, (int) Math.ceil(Math.cbrt(totalSize)) + 1);
        List<BlockPos> tanksToDestroy = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(
                worldPosition.offset(-searchRadius, -searchRadius, -searchRadius),
                worldPosition.offset(searchRadius, searchRadius, searchRadius))) {
            BlockState s = level.getBlockState(p);
            if (s.getBlock() instanceof FluidTankBlock) {
                BlockEntity be = level.getBlockEntity(p);
                if (be instanceof CreativeFluidTankBlockEntity) {
                    continue;
                }
                tanksToDestroy.add(p.immutable());
            }
        }
        for (BlockPos p : tanksToDestroy) {
            level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
        }

        SubLevel subLevel = null;
        Object lvlObj = level;
        if (lvlObj instanceof SubLevel sl) {
            subLevel = sl;
        } else {
            try {
                Object obj = Sable.HELPER.getContaining(level, worldPosition);
                if (obj instanceof SubLevel sl) {
                    subLevel = sl;
                }
            } catch (Throwable ignored) {
            }
        }

        if (subLevel instanceof ServerSubLevel ssl) {
            dev.devce.rocketnautics.content.physics.SubLevelExplosionHandler.explodeSubLevel(ssl, explosionPower);
            return;
        }

        if (level instanceof ServerLevel sl) {
            dev.devce.rocketnautics.content.physics.SubLevelExplosionHandler.sendExplosionParticle(sl,
                    worldPosition.getX() + 0.5,
                    worldPosition.getY() + 0.5,
                    worldPosition.getZ() + 0.5,
                    explosionPower);
        }

        AABB plotDamageArea = new AABB(worldPosition).inflate(explosionPower * 1.5);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, plotDamageArea)) {
            e.hurt(level.damageSources().explosion(null, null), explosionPower * 3.5f);
        }
    }
}
