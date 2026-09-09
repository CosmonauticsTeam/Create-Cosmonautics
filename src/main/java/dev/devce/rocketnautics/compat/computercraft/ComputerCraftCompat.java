package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.ComputerCraftAPI;
import dev.devce.rocketnautics.compat.computercraft.generic.ThrusterMethods;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import dev.devce.rocketnautics.registry.RocketBlockEntities;

public class ComputerCraftCompat {
    public static void init() {
        ComputerCraftAPI.registerGenericSource(new ThrusterMethods());
        ComputerCraftAPI.registerGenericSource(new dev.devce.rocketnautics.compat.computercraft.generic.GyrodyneMethods());
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(
                PeripheralCapability.get(),
                RocketBlockEntities.SPUTNIK.get(),
                (be, side) -> new SputnikPeripheral(be)
        );
        event.registerBlockEntity(
                PeripheralCapability.get(),
                RocketBlockEntities.GYRODYNE.get(),
                (be, side) -> new GyrodynePeripheral(be)
        );
        event.registerBlockEntity(
                PeripheralCapability.get(),
                RocketBlockEntities.MFD.get(),
                (be, side) -> new MFDPeripheral(be)
        );
    }
}
