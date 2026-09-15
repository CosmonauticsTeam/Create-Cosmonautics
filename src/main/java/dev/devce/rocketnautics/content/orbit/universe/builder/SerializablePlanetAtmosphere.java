package dev.devce.rocketnautics.content.orbit.universe.builder;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import org.joml.Vector3f;

import java.util.Optional;

public record SerializablePlanetAtmosphere(Optional<Boolean> enabled, Optional<Float> radius, Optional<Float> intensity, Optional<Float> densityFalloff, Optional<Float> scatteringStrength, Optional<Vector3f> wavelengths) {
    public static final Codec<SerializablePlanetAtmosphere> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("enabled").forGetter(a -> a.enabled),
            Codec.FLOAT.optionalFieldOf("radius").forGetter(a -> a.radius),
            Codec.FLOAT.optionalFieldOf("intensity").forGetter(a -> a.intensity),
            Codec.FLOAT.optionalFieldOf("densityFalloff").forGetter(a -> a.densityFalloff),
            Codec.FLOAT.optionalFieldOf("scatteringStrength").forGetter(a -> a.scatteringStrength),
            ExtraCodecs.VECTOR3F.optionalFieldOf("wavelengths").forGetter(a -> a.wavelengths)
    ).apply(instance, SerializablePlanetAtmosphere::new));

    public static Optional<SerializablePlanetAtmosphere> of(Optional<Boolean> enabled, Optional<Float> radius, Optional<Float> intensity, Optional<Float> densityFalloff, Optional<Float> scatteringStrength, Optional<Vector3f> wavelengths) {
        return Optional.of(new SerializablePlanetAtmosphere(enabled, radius, intensity, densityFalloff, scatteringStrength, wavelengths));
    }
}