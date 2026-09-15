package dev.devce.rocketnautics.content.physics;

import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.devce.rocketnautics.RocketConfig;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class SubLevelFuelMassTracker {
    public record TankFuel(double mass, Vector3d centerPos, double width, double height) {}

    private static final Map<UUID, Set<FluidTankBlockEntity>> TANKS_BY_SUBLEVEL = new ConcurrentHashMap<>();

    private SubLevelFuelMassTracker() {}

    public static void registerTank(ServerSubLevel subLevel, FluidTankBlockEntity tank) {
        if (subLevel == null || tank == null || tank.isRemoved()) return;
        if (tank instanceof CreativeFluidTankBlockEntity) return;

        FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
        if (controller instanceof CreativeFluidTankBlockEntity) return;

        TANKS_BY_SUBLEVEL.computeIfAbsent(subLevel.getUniqueId(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                .add(controller);
    }

    public static void unregisterTank(ServerSubLevel subLevel, FluidTankBlockEntity tank) {
        if (subLevel == null || tank == null) return;
        Set<FluidTankBlockEntity> set = TANKS_BY_SUBLEVEL.get(subLevel.getUniqueId());
        if (set != null) {
            FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            set.remove(controller);
            set.remove(tank);
        }
    }

    public static void clearSubLevel(UUID subLevelId) {
        TANKS_BY_SUBLEVEL.remove(subLevelId);
    }

    public static List<TankFuel> getFuelData(ServerSubLevel subLevel) {
        if (subLevel == null || !RocketConfig.SERVER.fuelMassEnabled.get() || RocketConfig.SERVER.massMultiplier.get() <= 0.0) {
            return Collections.emptyList();
        }

        Set<FluidTankBlockEntity> tanks = TANKS_BY_SUBLEVEL.get(subLevel.getUniqueId());
        if (tanks == null || tanks.isEmpty()) {
            scanAndPopulateTanks(subLevel);
            tanks = TANKS_BY_SUBLEVEL.get(subLevel.getUniqueId());
        }

        if (tanks == null || tanks.isEmpty()) {
            return Collections.emptyList();
        }

        List<TankFuel> result = new ArrayList<>(tanks.size());
        Iterator<FluidTankBlockEntity> it = tanks.iterator();

        while (it.hasNext()) {
            FluidTankBlockEntity tank = it.next();
            if (tank == null || tank.isRemoved()) {
                it.remove();
                continue;
            }
            if (tank instanceof CreativeFluidTankBlockEntity) {
                it.remove();
                continue;
            }

            net.neoforged.neoforge.fluids.FluidStack fluid = net.neoforged.neoforge.fluids.FluidStack.EMPTY;
            try {
                var storage = tank.getTank(0);
                if (storage != null) {
                    fluid = storage.getFluid();
                }
            } catch (Throwable ignored) {}

            if (fluid.isEmpty() || fluid.getAmount() <= 0) {
                continue;
            }

            double bucketMass = FluidMassHelper.getMassPerBucket(fluid);
            if (bucketMass <= 0.0) {
                continue;
            }

            double fuelMass = (fluid.getAmount() / 1000.0) * bucketMass;
            if (fuelMass <= 0.0) {
                continue;
            }

            BlockPos pos = tank.getBlockPos();
            double width = Math.max(1, tank.getWidth());
            double height = Math.max(1, tank.getHeight());
            Vector3d center = new Vector3d(
                    pos.getX() + width * 0.5,
                    pos.getY() + height * 0.5,
                    pos.getZ() + width * 0.5
            );

            result.add(new TankFuel(fuelMass, center, width, height));
        }

        return result;
    }

    public static double getTotalFuelMass(ServerSubLevel subLevel) {
        List<TankFuel> fuels = getFuelData(subLevel);
        double total = 0.0;
        for (TankFuel f : fuels) {
            total += f.mass();
        }
        return total;
    }

    private static void scanAndPopulateTanks(ServerSubLevel subLevel) {
        ServerLevelPlot plot = subLevel.getPlot();
        if (plot == null) return;
        ServerLevel level = subLevel.getLevel();
        if (level == null) return;

        Set<FluidTankBlockEntity> set = Collections.newSetFromMap(new ConcurrentHashMap<>());
        for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            if (chunk == null) continue;
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FluidTankBlockEntity tankBE && !(be instanceof CreativeFluidTankBlockEntity)) {
                    FluidTankBlockEntity controller = tankBE.getControllerBE();
                    FluidTankBlockEntity target = controller != null ? controller : tankBE;
                    if (!(target instanceof CreativeFluidTankBlockEntity)) {
                        set.add(target);
                    }
                }
            }
        }
        if (!set.isEmpty()) {
            TANKS_BY_SUBLEVEL.put(subLevel.getUniqueId(), set);
        }
    }
}
