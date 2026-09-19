package dev.devce.rocketnautics.content.physics;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.registry.RocketTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidType;

public final class FluidMassHelper {
    private FluidMassHelper() {}

    private static TagKey<Fluid> cTag(String path) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", path));
    }

    private static final TagKey<Fluid> TAG_NAPALM = cTag("napalm");
    private static final TagKey<Fluid> TAG_HEAVY_OIL = cTag("heavy_oil");
    private static final TagKey<Fluid> TAG_CRUDE_OIL = cTag("crude_oil");
    private static final TagKey<Fluid> TAG_OIL = cTag("oil");
    private static final TagKey<Fluid> TAG_BITUMEN = cTag("bitumen");
    private static final TagKey<Fluid> TAG_DIESEL = cTag("diesel");
    private static final TagKey<Fluid> TAG_DIESEL_SULFUR = cTag("diesel_sulfur");
    private static final TagKey<Fluid> TAG_BIODIESEL = cTag("biodiesel");
    private static final TagKey<Fluid> TAG_BIOFUEL = cTag("biofuel");
    private static final TagKey<Fluid> TAG_FUEL = cTag("fuel");
    private static final TagKey<Fluid> TAG_FUELS = cTag("fuels");

    private static final TagKey<Fluid> TAG_KEROSENE = cTag("kerosene");
    private static final TagKey<Fluid> TAG_GASOLINE = cTag("gasoline");
    private static final TagKey<Fluid> TAG_NAPHTHA = cTag("naphtha");
    private static final TagKey<Fluid> TAG_ETHANOL = cTag("ethanol");

    private static final TagKey<Fluid> TAG_LPG = cTag("lpg");
    private static final TagKey<Fluid> TAG_PETROLEUM_GAS = cTag("petroleum_gas");
    private static final TagKey<Fluid> TAG_HYDROGEN = cTag("hydrogen");
    private static final TagKey<Fluid> TAG_AIR = cTag("air");
    private static final TagKey<Fluid> TAG_OXYGEN = cTag("oxygen");
    private static final TagKey<Fluid> TAG_LAVA_COMMON = cTag("lava");

    public static double getMassPerBucket(FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0.0;
        }

        if (!RocketConfig.SERVER.fuelMassEnabled.get()) {
            return 0.0;
        }

        double multiplier = RocketConfig.SERVER.massMultiplier.get();
        if (multiplier <= 0.0) {
            return 0.0;
        }

        double baseScale = RocketConfig.SERVER.fuelMassPerBucket.get() * multiplier;
        if (baseScale <= 0.0) {
            return 0.0;
        }

        if (stack.is(Fluids.LAVA) || stack.is(FluidTags.LAVA) || stack.is(TAG_LAVA_COMMON)) {
            return RocketConfig.SERVER.lavaMassPerBucket.get() * multiplier;
        }

        if (stack.is(TAG_NAPALM) || stack.is(TAG_HEAVY_OIL) || stack.is(TAG_CRUDE_OIL) || stack.is(TAG_OIL)
                || stack.is(TAG_BITUMEN) || stack.is(TAG_DIESEL) || stack.is(TAG_DIESEL_SULFUR)
                || stack.is(TAG_BIODIESEL) || stack.is(TAG_BIOFUEL) || stack.is(TAG_FUEL) || stack.is(TAG_FUELS)) {
            return RocketConfig.SERVER.heavyFuelMassPerBucket.get() * multiplier;
        }

        if (stack.is(TAG_KEROSENE) || stack.is(TAG_GASOLINE) || stack.is(TAG_NAPHTHA) || stack.is(TAG_ETHANOL)
                || stack.is(RocketTags.FluidTags.ROCKET_FUEL.tag)) {
            return RocketConfig.SERVER.rocketFuelMassPerBucket.get() * multiplier;
        }

        if (stack.is(TAG_LPG) || stack.is(TAG_PETROLEUM_GAS)) {
            return 0.5 * baseScale;
        }

        if (stack.is(TAG_HYDROGEN)) {
            return 0.1 * baseScale;
        }

        if (stack.is(TAG_AIR) || stack.is(TAG_OXYGEN)) {
            return 0.05 * baseScale;
        }

        if (stack.is(Fluids.WATER) || stack.is(FluidTags.WATER)) {
            return baseScale;
        }

        try {
            FluidType type = stack.getFluidType();
            if (type != null) {
                int density = type.getDensity(stack);
                if (density > 0) {
                    return (density / 1000.0) * baseScale;
                }
            }
        } catch (Throwable ignored) {}

        return baseScale;
    }
}
