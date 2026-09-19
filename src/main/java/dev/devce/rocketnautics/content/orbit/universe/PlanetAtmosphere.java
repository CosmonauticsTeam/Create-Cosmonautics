package dev.devce.rocketnautics.content.orbit.universe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

public record PlanetAtmosphere(float radius, float intensity, float densityFalloff, float scatteringStrength, Vector3f wavelengths) {
    public static final StreamCodec<FriendlyByteBuf, PlanetAtmosphere> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeFloat(val.radius);
                buf.writeFloat(val.intensity);
                buf.writeFloat(val.densityFalloff);
                buf.writeFloat(val.scatteringStrength);
                buf.writeVector3f(val.wavelengths);
            },
            buf -> {
                float radius = buf.readFloat();
                float intensity = buf.readFloat();
                float densityFalloff = buf.readFloat();
                float scatteringStrength = buf.readFloat();
                Vector3f wavelengths = buf.readVector3f();
                return new PlanetAtmosphere(radius, intensity, densityFalloff, scatteringStrength, wavelengths);
            }
    );
}
