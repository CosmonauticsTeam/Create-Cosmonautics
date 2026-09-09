package dev.devce.rocketnautics.content.particles;

import dev.devce.rocketnautics.client.CameraShakeHandler;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.devce.rocketnautics.registry.RocketSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.NoRenderParticle;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

public class TankExplosionCloudParticle extends NoRenderParticle {

    private final float power;
    private final List<TrailSubparticle> trails = new LinkedList<>();

    public TankExplosionCloudParticle(ClientLevel level, double x, double y, double z, float power) {
        super(level, x, y, z);
        this.power = Mth.clamp(power <= 10.0f ? power * 0.6f : 6.0f + (power - 10.0f) * 0.08f, 4.0f, 8.5f);
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.lifetime = 10;

        // 1. Play CBC artillery shell explosion sound
        float volume = Math.min(16.0f, Math.max(4.0f, this.power * 1.5f));
        float pitch = 0.8f + level.random.nextFloat() * 0.35f;
        SimpleSoundInstance sound = new SimpleSoundInstance(
                RocketSounds.TANK_EXPLOSION.get(),
                SoundSource.BLOCKS,
                volume,
                pitch,
                RandomSource.create(level.random.nextLong()),
                x, y, z
        );
        Minecraft.getInstance().getSoundManager().play(sound);

        // 3. Screen shake (CBC Blast Wave effect)
        if (Minecraft.getInstance().player != null) {
            double dist = Math.sqrt(Minecraft.getInstance().player.distanceToSqr(x, y, z));
            double shakeRadius = Math.max(48.0, this.power * 8.0);
            if (dist < shakeRadius) {
                float f = 1.0f - (float) (dist / shakeRadius);
                CameraShakeHandler.addShake(f * Math.min(3.0f, this.power * 0.25f));
            }
        }

        // 4. Generate CBC-style trailing debris / sparks
        double secondaryVelScale = this.power * 0.35;
        int secondaryCount = 14 + this.random.nextInt(6);
        double gravity = -0.12;
        for (int i = 0; i < secondaryCount; ++i) {
            double rx = this.random.nextDouble() - this.random.nextDouble();
            double ry = this.random.nextDouble() - this.random.nextDouble();
            double rz = this.random.nextDouble() - this.random.nextDouble();
            double dx = this.random.nextDouble() - this.random.nextDouble();
            double dy = this.random.nextDouble() - this.random.nextDouble() + 0.35;
            double dz = this.random.nextDouble() - this.random.nextDouble();
            int subLifetime = 5;
            this.trails.add(new TrailSubparticle(
                    new Vec3(rx, ry, rz).scale(2.5),
                    new Vec3(dx, dy, dz).scale(secondaryVelScale),
                    0.85,
                    gravity,
                    subLifetime
            ));
        }
    }

    @Override
    public void tick() {
        int PLUME_AGE = 5;
        if (this.age < PLUME_AGE) {
            float primaryScale = this.power * 2.0f;
            int plumes = 20;
            double velScale = this.power * 0.25 * (1.0 - (double) this.age / (double) PLUME_AGE);
            double displacementScale = this.power * 0.35;
            SpriteSet sprites = ExplosionSmokeParticle.getSprites();

            for (int i = 0; i <= plumes; ++i) {
                double rx = this.x + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double ry = this.y + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale + 0.5;
                double rz = this.z + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double dx = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dy = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dz = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                int lifetime = 140 + this.random.nextInt(60);

                if (sprites != null) {
                    ExplosionSmokeParticle p = new ExplosionSmokeParticle(this.level, rx, ry, rz, dx, dy, dz, primaryScale, lifetime, sprites);
                    Minecraft.getInstance().particleEngine.add(p);
                } else {
                    this.level.addParticle(RocketParticles.EXPLOSION_SMOKE.get(), rx, ry, rz, dx, dy, dz);
                }
            }
        }

        float secondaryScale = this.power * 0.75f;
        SpriteSet sprites = ExplosionSmokeParticle.getSprites();

        for (Iterator<TrailSubparticle> iter = this.trails.iterator(); iter.hasNext(); ) {
            TrailSubparticle trail = iter.next();
            if (this.age > trail.lifetime) {
                iter.remove();
                continue;
            }
            Vec3 origin = trail.calculateDisplacement(this.age);
            Vec3 next = trail.calculateDisplacement(this.age + 1);
            double velScale = 0.125;
            double displacementScale = 0.1;
            for (int i = 0; i <= 2; ++i) {
                Vec3 pos = origin.lerp(next, (double) i / 2.0).add(this.x, this.y, this.z);
                double rx = pos.x + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double ry = pos.y + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double rz = pos.z + (this.random.nextDouble() - this.random.nextDouble()) * displacementScale;
                double dx = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dy = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                double dz = (this.random.nextDouble() - this.random.nextDouble()) * velScale;
                int lifetime = 30 + this.random.nextInt(15);

                if (sprites != null) {
                    ExplosionSmokeParticle p = new ExplosionSmokeParticle(this.level, rx, ry, rz, dx, dy, dz, secondaryScale, lifetime, sprites);
                    Minecraft.getInstance().particleEngine.add(p);
                } else {
                    this.level.addParticle(RocketParticles.EXPLOSION_SMOKE.get(), rx, ry, rz, dx, dy, dz);
                }
            }
        }

        super.tick();
    }

    private record TrailSubparticle(Vec3 displacement, Vec3 vel, double drag, double gravity, int lifetime) {
        public Vec3 calculateDisplacement(int ticks) {
            if (ticks <= 0) return this.displacement;
            if (this.drag == 1) return this.displacement.add(this.vel.scale(ticks));
            double geo = (1 - Math.pow(this.drag, ticks)) / (1 - this.drag);
            return this.displacement.add(this.vel.scale(geo)).add(0, this.gravity * ticks, 0);
        }
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {
        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            float power = (float) dx;
            return new TankExplosionCloudParticle(level, x, y, z, power);
        }
    }
}
