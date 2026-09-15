package dev.devce.rocketnautics.content.orbit.universe.builder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

import java.util.Optional;

public record SerializableStarProperties(Optional<Float> radius, Optional<Float> intensity, Optional<Float> densityFalloff, Optional<Vector3f> color) {
    public static final Codec<SerializableStarProperties> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.FLOAT.optionalFieldOf("radius").forGetter(a -> a.radius),
            Codec.FLOAT.optionalFieldOf("intensity").forGetter(a -> a.intensity),
            Codec.FLOAT.optionalFieldOf("densityFalloff").forGetter(a -> a.densityFalloff),
            ExtraCodecs.VECTOR3F.optionalFieldOf("color").forGetter(a -> a.color)
    ).apply(instance, SerializableStarProperties::new));

    public static Optional<SerializableStarProperties> of(Optional<Float> radius, Optional<Float> intensity, Optional<Float> densityFalloff, Optional<Vector3f> color) {
        return Optional.of(new SerializableStarProperties(radius, intensity, densityFalloff, color));
    }
}