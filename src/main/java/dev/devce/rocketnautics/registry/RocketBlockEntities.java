package dev.devce.rocketnautics.registry;

import com.simibubi.create.api.behaviour.display.DisplaySource;
import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.content.kinetics.base.ShaftRenderer;
import com.simibubi.create.content.kinetics.base.SingleAxisRotatingVisual;
import com.tterrag.registrate.util.entry.BlockEntityEntry;
import com.tterrag.registrate.util.entry.RegistryEntry;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.client.render.HologramTableRenderer;
import dev.devce.rocketnautics.client.render.VectorThrusterRenderer;
import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.CreativeThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.EngineNozzleBlockEntity;
import dev.devce.rocketnautics.content.blocks.EnginePipesBlockEntity;
import dev.devce.rocketnautics.content.blocks.HologramTableBlockEntity;
import dev.devce.rocketnautics.content.blocks.RCSThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.content.blocks.SputnikDisplaySource;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlockEntity;
import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.hose.HoseAnchorBlockEntity;
import dev.devce.rocketnautics.content.blocks.hose.client.HoseAnchorRenderer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

public class RocketBlockEntities {
        private static final RocketRegistrate REGISTRATE = RocketNautics.getRegistrate();

        public static final BlockEntityEntry<RocketThrusterBlockEntity> ROCKET_THRUSTER = REGISTRATE
                        .blockEntity("rocket_thruster", RocketThrusterBlockEntity::new).validBlocks(RocketBlocks.ROCKET_THRUSTER)
                        .register();

        public static final BlockEntityEntry<CreativeThrusterBlockEntity> CREATIVE_THRUSTER = REGISTRATE
                        .blockEntity("creative_thruster", CreativeThrusterBlockEntity::new).validBlocks(RocketBlocks.CREATIVE_THRUSTER)
                        .register();

        public static final BlockEntityEntry<BoosterThrusterBlockEntity> BOOSTER_THRUSTER = REGISTRATE
                        .blockEntity("booster_thruster", BoosterThrusterBlockEntity::new).validBlocks(RocketBlocks.BOOSTER_THRUSTER)
                        .register();

        public static final BlockEntityEntry<VectorThrusterBlockEntity> VECTOR_THRUSTER = REGISTRATE
                        .blockEntity("vector_thruster", VectorThrusterBlockEntity::new).validBlocks(RocketBlocks.VECTOR_THRUSTER)
                        .renderer(() -> VectorThrusterRenderer::new).register();

        public static final BlockEntityEntry<RCSThrusterBlockEntity> RCS_THRUSTER = REGISTRATE
                        .blockEntity("rcs_thruster", RCSThrusterBlockEntity::new)
                        .validBlocks(RocketBlocks.RCS_THRUSTER, RocketBlocks.BRASS_ENCASED_RCS_THRUSTER,
                                        RocketBlocks.COPPER_ENCASED_RCS_THRUSTER, RocketBlocks.RAILWAY_ENCASED_RCS_THRUSTER)
                        .register();

        public static final BlockEntityEntry<KineticBlockEntity> SEPARATOR_SHAFT = REGISTRATE
                        .blockEntity("separator_shaft", KineticBlockEntity::new).visual(() -> SingleAxisRotatingVisual::shaft, false)
                        .validBlocks(RocketBlocks.SEPARATOR_SHAFT).renderer(() -> ShaftRenderer::new).register();

        public static final RegistryEntry<DisplaySource, SputnikDisplaySource> SPUTNIK_DISPLAY_SOURCE = REGISTRATE
                        .displaySource("sputnik", SputnikDisplaySource::new).register();

        public static final BlockEntityEntry<SputnikBlockEntity> SPUTNIK = REGISTRATE.blockEntity("sputnik", SputnikBlockEntity::new)
                        .validBlocks(RocketBlocks.SPUTNIK).onRegisterAfter(com.simibubi.create.api.registry.CreateRegistries.DISPLAY_SOURCE,
                                        type -> DisplaySource.BY_BLOCK_ENTITY.add(type, SPUTNIK_DISPLAY_SOURCE.get()))
                        .register();

        public static final BlockEntityEntry<HologramTableBlockEntity> HOLOGRAM_TABLE = REGISTRATE
                        .blockEntity("hologram_table", HologramTableBlockEntity::new).validBlocks(RocketBlocks.HOLOGRAM_TABLE)
                        .renderer(() -> HologramTableRenderer::new).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity> MFD = REGISTRATE
                        .blockEntity("mfd", dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity::new).validBlocks(RocketBlocks.MFD)
                        .renderer(() -> dev.devce.rocketnautics.client.render.MFDRenderer::new).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity> GYRODYNE = REGISTRATE
                        .blockEntity("gyrodyne", dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity::new)
                        .validBlocks(RocketBlocks.GYRODYNE).renderer(() -> dev.devce.rocketnautics.client.render.GyrodyneRenderer::new)
                        .register();

        public static final BlockEntityEntry<HoseAnchorBlockEntity> HOSE_ANCHOR = REGISTRATE
                        .blockEntity("hose_anchor", HoseAnchorBlockEntity::new).validBlocks(RocketBlocks.HOSE_ANCHOR)
                        .renderer(() -> HoseAnchorRenderer::new).register();

        public static final BlockEntityEntry<ThrusterMountBlockEntity> THRUSTER_MOUNT = REGISTRATE
                        .blockEntity("thruster_mount", ThrusterMountBlockEntity::new).validBlocks(RocketBlocks.THRUSTER_MOUNT)
                        .renderer(() -> dev.devce.rocketnautics.client.render.ThrusterMountRenderer::new).register();

        public static final BlockEntityEntry<EnginePipesBlockEntity> ENGINE_PIPES = REGISTRATE
                        .blockEntity("engine_pipes", EnginePipesBlockEntity::new).validBlocks(RocketBlocks.ENGINE_PIPES)
                        .renderer(() -> dev.devce.rocketnautics.client.render.EnginePipesRenderer::new).register();

        public static final BlockEntityEntry<EngineNozzleBlockEntity> ENGINE_NOZZLE = REGISTRATE
                        .blockEntity("engine_nozzle", EngineNozzleBlockEntity::new).validBlocks(RocketBlocks.ENGINE_NOZZLE)
                        .renderer(() -> dev.devce.rocketnautics.client.render.EngineNozzleRenderer::new).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.solar.SolarPanelBlockEntity> SOLAR_PANEL = REGISTRATE
                        .blockEntity("solar_panel", dev.devce.rocketnautics.content.blocks.solar.SolarPanelBlockEntity::new)
                        .validBlocks(RocketBlocks.SOLAR_PANEL).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.ion.IonEngineBlockEntity> ION_ENGINE = REGISTRATE
                        .blockEntity("ion_engine", dev.devce.rocketnautics.content.blocks.ion.IonEngineBlockEntity::new)
                        .validBlocks(RocketBlocks.ION_ENGINE).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.wire.CopperWireBlockEntity> COPPER_WIRE = REGISTRATE
                        .blockEntity("copper_wire", dev.devce.rocketnautics.content.blocks.wire.CopperWireBlockEntity::new)
                        .validBlocks(RocketBlocks.COPPER_WIRE).register();

        public static final BlockEntityEntry<dev.devce.rocketnautics.content.blocks.energy_tank.EnergyTankBlockEntity> ENERGY_TANK = REGISTRATE
                        .blockEntity("energy_tank", dev.devce.rocketnautics.content.blocks.energy_tank.EnergyTankBlockEntity::new)
                        .validBlocks(RocketBlocks.ENERGY_TANK).register();

        public static void register(IEventBus eventBus) {
                eventBus.addListener(RocketBlockEntities::registerCapabilities);
        }

        private static void registerCapabilities(RegisterCapabilitiesEvent event) {
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SOLAR_PANEL.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ION_ENGINE.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, COPPER_WIRE.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, GYRODYNE.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, SPUTNIK.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, ENERGY_TANK.get(), (be, side) -> be.getEnergyStorage(side));
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, ROCKET_THRUSTER.get(), (be, side) -> be.fuelTank);
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, VECTOR_THRUSTER.get(), (be, side) -> be.fuelTank);
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, HOSE_ANCHOR.get(), (be, side) -> be.fuelTank);
                event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, THRUSTER_MOUNT.get(), (be, side) -> {
                        if (side == null)
                                return be.combinedFluidHandler;
                        net.minecraft.world.level.block.state.BlockState state = be.getBlockState();
                        if (state.getBlock() instanceof dev.devce.rocketnautics.content.blocks.ThrusterMountBlock) {
                                net.minecraft.core.Direction facing = state
                                                .getValue(dev.devce.rocketnautics.content.blocks.ThrusterMountBlock.FACING);
                                if (side == be.getInput1(facing)) {
                                        return be.tank1;
                                }
                                if (side == be.getInput2(facing)) {
                                        return be.tank2;
                                }
                        }
                        return null;
                });

                if (net.neoforged.fml.ModList.get().isLoaded("computercraft")) {
                        dev.devce.rocketnautics.compat.computercraft.ComputerCraftCompat.registerCapabilities(event);
                }
        }
}
