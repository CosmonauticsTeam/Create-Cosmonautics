package dev.devce.rocketnautics.registry;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.particles.JetpackFlameParticle;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class RocketParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES = DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, RocketNautics.MODID);

    public static final Supplier<SimpleParticleType> PLASMA = PARTICLES.register("plasma",
            () -> new SimpleParticleType(true));

    public static final Supplier<SimpleParticleType> PLUME = PARTICLES.register("plume",
            () -> new SimpleParticleType(true));

    public static final Supplier<SimpleParticleType> JET_SMOKE = PARTICLES.register("jet_smoke",
            () -> new SimpleParticleType(true));

    public static final Supplier<SimpleParticleType> BLUE_FLAME = PARTICLES.register("blue_flame",
            () -> new SimpleParticleType(true));

    public static final Supplier<SimpleParticleType> RCS_GAS = PARTICLES.register("rcs_gas",
            () -> new SimpleParticleType(true));

    public static final Supplier<JetpackFlameParticle.JetpackFlameParticleType> JETPACK_FLAME = PARTICLES.register("jetpack_flame",
            JetpackFlameParticle.JetpackFlameParticleType::new);

    public static void register(IEventBus eventBus) {
        PARTICLES.register(eventBus);
    }
}
