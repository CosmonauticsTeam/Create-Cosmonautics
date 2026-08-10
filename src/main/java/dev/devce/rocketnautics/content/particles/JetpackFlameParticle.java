package dev.devce.rocketnautics.content.particles;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.devce.rocketnautics.registry.RocketParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

public class JetpackFlameParticle extends TextureSheetParticle {
    protected JetpackFlameParticle(ClientLevel level, double x, double y, double z, double vx, double vy, double vz, Vector3f color, float scale, int lifetime, SpriteSet sprites) {
        super(level, x, y, z, vx, vy, vz);

        this.xd = vx;
        this.yd = vy;
        this.zd = vz;

        float divergence = 0.05F;
        this.rCol = Math.clamp(color.x() + (this.random.nextFloat() * 2.0F - 1.0F) * divergence, 0.0F, 1.0F);
        this.gCol = Math.clamp(color.y() + (this.random.nextFloat() * 2.0F - 1.0F) * divergence, 0.0F, 1.0F);
        this.bCol = Math.clamp(color.z() + (this.random.nextFloat() * 2.0F - 1.0F) * divergence, 0.0F, 1.0F);

        this.quadSize = scale * 0.1f;

        this.lifetime = lifetime;

        this.friction = 1.0f;
        this.gravity = 0.0f;

        this.setSprite(sprites.get(this.random));
    }

    @Override
    protected int getLightColor(float partialTick) {
        return 0xF000F0;
    }

    @Override
    public float getQuadSize(float partialTick) {
        float progress = ((float) this.age + partialTick) / (float) this.lifetime;
        return this.quadSize * (1.0F - progress);
    }

    @Override
    public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    public static class JetpackFlameProvider implements ParticleProvider<JetpackFlameParticleOptions> {
        private final SpriteSet sprites;

        public JetpackFlameProvider(SpriteSet sprites) { this.sprites = sprites; }

        @Override
        public Particle createParticle(JetpackFlameParticleOptions options, ClientLevel level, double x, double y, double z, double vx, double vy, double vz) {
            return new JetpackFlameParticle(
                    level,
                    x, y, z,
                    vx, vy, vz,
                    options.color,
                    options.scale,
                    options.lifetime,
                    sprites
            );
        }
    }

    public record JetpackFlameParticleOptions(
        Vector3f color,
        float scale,
        int lifetime
    ) implements ParticleOptions {
        @Override
        public ParticleType<?> getType() {
            return RocketParticles.JETPACK_FLAME.get();
        }
    }

    public static class JetpackFlameParticleType extends ParticleType<JetpackFlameParticleOptions> {
        public JetpackFlameParticleType() { super(false); }

        @Override
        public MapCodec<JetpackFlameParticleOptions> codec() {
            return RecordCodecBuilder.mapCodec(instance -> instance.group(
                    ExtraCodecs.VECTOR3F.fieldOf("color").forGetter(JetpackFlameParticleOptions::color),
                    Codec.FLOAT.fieldOf("scale").forGetter(JetpackFlameParticleOptions::scale),
                    Codec.INT.fieldOf("lifetime").forGetter(JetpackFlameParticleOptions::lifetime)
            ).apply(instance, JetpackFlameParticleOptions::new));
        }

        @Override
        public StreamCodec<? super RegistryFriendlyByteBuf, JetpackFlameParticleOptions> streamCodec() {
            return StreamCodec.composite(
                    ByteBufCodecs.VECTOR3F, JetpackFlameParticleOptions::color,
                    ByteBufCodecs.FLOAT, JetpackFlameParticleOptions::scale,
                    ByteBufCodecs.INT, JetpackFlameParticleOptions::lifetime,
                    JetpackFlameParticleOptions::new
            );
        }
    }
}
