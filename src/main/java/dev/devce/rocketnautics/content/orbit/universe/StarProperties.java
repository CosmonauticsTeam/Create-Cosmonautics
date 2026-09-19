package dev.devce.rocketnautics.content.orbit.universe;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.joml.Vector3f;

public record StarProperties(float radius, float intensity, float densityFalloff, Vector3f color) {
    public static final StreamCodec<FriendlyByteBuf, StarProperties> CODEC = StreamCodec.of(
            (buf, val) -> {
                buf.writeFloat(val.radius);
                buf.writeFloat(val.intensity);
                buf.writeFloat(val.densityFalloff);
                buf.writeVector3f(val.color);
            },
            buf -> {
                float radius = buf.readFloat();
                float intensity = buf.readFloat();
                float densityFalloff = buf.readFloat();
                Vector3f color = buf.readVector3f();
                return new StarProperties(radius, intensity, densityFalloff, color);
            }
    );
}