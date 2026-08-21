package dev.devce.rocketnautics.content.blocks;

import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.fluids.FluidStack;

/** Fuel properties shared by the direct and modular rocket engines. */
public record RocketFuelProperties(float ispMultiplier, float consumptionMultiplier) {
    public static RocketFuelProperties forFuel(FluidStack fuel) {
        String fluidId = BuiltInRegistries.FLUID.getKey(fuel.getFluid()).toString().toLowerCase();

        if (fluidId.contains("kerosene")) {
            return new RocketFuelProperties(1.4f, 0.5f);
        }
        if (fluidId.contains("diesel") || fluidId.contains("fuel_oil") || fluidId.contains("lpg")) {
            return new RocketFuelProperties(1.2f, 0.7f);
        }
        if (fluidId.contains("gasoline") || fluidId.contains("petrol")) {
            return new RocketFuelProperties(1.1f, 0.8f);
        }
        return new RocketFuelProperties(1.0f, 1.0f);
    }
}
