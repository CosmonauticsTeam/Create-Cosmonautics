package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.api.orbit.ColorFlags;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.universe.builder.BiomeSamplingTextureBuilder;
import dev.devce.rocketnautics.content.orbit.universe.builder.PlanetDefinitionBuilder;
import dev.devce.rocketnautics.content.orbit.universe.builder.UniverseDefinitionBuilder;
import dev.devce.rocketnautics.registry.RocketTags;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.Tags;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

import java.util.EnumSet;

public final class StandardUniverseProvider {
    private static final double solRadius = 300_000_000D;
    private static final double overworldRadius = 3_000_000D; // 1 / 10th of the dimension radius
    private static final int overworldOrbitalYearInOverworldDays = 72 * 7; // one real-life week. Balance between a shorter time and having a large sphere of influence.
    private static final int overworldDaynightCycleLengthSeconds = 1200;
    private static final int lunarMonthInOverworldDays = 8 * 3; // 8 real-life hours
    private static final double overworldDistance = solRadius * 40 / 3; // roughly based on the angular size of the sun in the overworld
    // orbit duration in seconds = 2pi * sqrt(r^3 / mu)
    // mu = r^3 * (2pi / orbit duration in seconds)^2
    // compute solar mu based on this
    private static final double comp = overworldDistance * Math.PI / (overworldOrbitalYearInOverworldDays * overworldDaynightCycleLengthSeconds);
    private static final double solMu = 4 * overworldDistance * comp * comp;

    private StandardUniverseProvider() {}

    public static UniverseDefinitionBuilder createSolarSystem() {
        return createSunOverworldMoon()
                .cubePlanet(mars())
                .cubePlanet(gasGiant())
                .cubePlanet(iceWorld());
    }

    public static UniverseDefinitionBuilder createSunOverworldMoon() {
        return UniverseDefinition.builder()
                .cubePlanet(sol())
                .cubePlanet(overworld())
                .cubePlanet(moon());
    }

    public static PlanetDefinitionBuilder sol() {
        return new PlanetDefinitionBuilder("root", "sol")
                .setMu(solMu)
                .setStar(true)
                .setTextureFile(RocketNautics.path("textures/planet/sol.png"))
                .setRadius(solRadius)
                .setRotationPeriod(Vector3D.PLUS_J, overworldDaynightCycleLengthSeconds * 32d)
                .setFixedPosition(Vector3D.ZERO)
                .setPriority(0);
    }

    public static PlanetDefinitionBuilder overworld() {
        return new PlanetDefinitionBuilder("sol", "overworld")
                .setAccelerationAtSurface(11)
                .setClouds(true)
                .setParentIsShadowLightSource()
                .setTextureDefinition(new BiomeSamplingTextureBuilder()
                        .addMatchingTag(BiomeTags.IS_OCEAN, BiomeTags.IS_DEEP_OCEAN)
                        .addFlag(ColorFlags.OCEAN)
                        .buildEntryToColor(15, 45 ,135) // Curated deep royal blue
                        .addMatchingTag(BiomeTags.IS_RIVER)
                        .buildEntryToColor(25, 95, 215) // Vibrant blue
                        .addMatchingTag(BiomeTags.IS_BEACH)
                        .buildEntryToColor(225, 205, 155) // Warm sand
                        .addMatchingTag(Tags.Biomes.IS_DESERT)
                        .buildEntryToColor(215, 195, 115) // Golden sand
                        .addMatchingTag(Tags.Biomes.IS_PLAINS)
                        .buildEntryToColor(45, 145, 55) // Emerald green
                        .addMatchingTag(BiomeTags.IS_FOREST)
                        .buildEntryToColor(25, 105, 35) // Lush dark forest green
                        .addMatchingTag(BiomeTags.IS_JUNGLE)
                        .setPriority(1)
                        .buildEntryToColor(15, 85, 25) // Deep jungle teal-green
                        .addMatchingTag(BiomeTags.IS_TAIGA)
                        .buildEntryToColor(30, 75, 55) // Cool pine green
                        .addMatchingTag(BiomeTags.IS_SAVANNA)
                        .buildEntryToColor(160, 140, 70)
                        .addMatchingTag(Tags.Biomes.IS_SNOWY)
                        .setPriority(10)
                        .buildEntryToColor(240, 240, 245) // Pristine snow white
                        .addMatchingTag(BiomeTags.IS_BADLANDS)
                        .setPriority(10)
                        .buildEntryToColor(195, 90, 40) // Terracotta orange
                        .addMatchingTag(Tags.Biomes.IS_SWAMP)
                        .buildEntryToColor(50, 70, 40)
                        .addMatchingTag(Tags.Biomes.IS_WINDSWEPT)
                        .buildEntryToColor(80, 100, 80)
                        .addMatchingTag(Tags.Biomes.IS_MUSHROOM)
                        .buildEntryToColor(100, 90, 100)
                        .addMatchingTag(BiomeTags.IS_MOUNTAIN, Tags.Biomes.IS_STONY_SHORES)
                        .buildEntryToColor(135, 135, 135)
                        .build(30, 120, 40))
                .setLinkedDimension(Level.OVERWORLD)
                .setRenderUniverseInDimension(true)
                .setDimensionTransferHeight(20_000)
                .addEntityDragPoint(4_000, 1, 0)
                .addEntityDragPoint(7_000, 0, -0.0006)
                .setAtmosphereFlagsBelow(5_000, AtmosphereFlags.empty())
                .setAtmosphereFlagsBelow(21_000, EnumSet.of(AtmosphereFlags.DROWNING, AtmosphereFlags.LOW_DENSITY))
                .setRadius(overworldRadius)
                .setCircularOrbit(overworldOrbitalYearInOverworldDays * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                .setRotationPeriod(Vector3D.MINUS_J, overworldDaynightCycleLengthSeconds)
                .setPriority(0);
    }

    public static PlanetDefinitionBuilder moon() {
        return new PlanetDefinitionBuilder("overworld", "moon")
                .setShadowLightSource("sol")
                .setAccelerationAtSurface(2)
                .setTextureDefinition(new BiomeSamplingTextureBuilder()
                        .addMatchingTag(RocketTags.BiomeTags.LUNAR_CHASM.tag)
                        .setPriority(10)
                        .buildEntryToColor(60, 62, 65)
                        .addMatchingTag(RocketTags.BiomeTags.LUNAR_HIGHLANDS.tag)
                        .buildEntryToColor(165, 168, 172)
                        .addMatchingTag(RocketTags.BiomeTags.LUNAR_MARIA.tag)
                        .addFlag(ColorFlags.OCEAN)
                        .buildEntryToColor(95, 98, 102)
                        .build(140, 142, 145))
                .setLinkedDimension(RocketDimensions.MOON)
                .setRenderUniverseInDimension(true)
                .setDimensionDayTimeController("sol")
                .setApplyGravityCorrectionToEntities(true)
                .setDimensionTransferHeight(20000)
                .addEntityDragPoint(4_000, 1, 0)
                .addEntityDragPoint(7_000, 0, -0.0006)
                .setAtmosphereFlagsBelow(21_000, EnumSet.of(AtmosphereFlags.DROWNING, AtmosphereFlags.LOW_DENSITY))
                .setCircularOrbit(lunarMonthInOverworldDays * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                .setRadius(overworldRadius / 4)
                .setTidalLocked()
                .setPriority(0);
    }

    public static PlanetDefinitionBuilder mars() {
        return new PlanetDefinitionBuilder("sol", "mars")
                .setAccelerationAtSurface(3.7)
                .setParentIsShadowLightSource()
                .setRadius(overworldRadius * 0.53) // Mars is smaller than Earth
                .setCircularOrbit((int) (overworldOrbitalYearInOverworldDays * 1.88) * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                .setRotationPeriod(Vector3D.MINUS_J, 1230) // ~24.6 hours
                .setTextureFile(RocketNautics.path("textures/planet/mars.png"))
                .setPriority(0);
    }

    public static PlanetDefinitionBuilder gasGiant() {
        return new PlanetDefinitionBuilder("sol", "gas_giant")
                .setAccelerationAtSurface(24.8)
                .setParentIsShadowLightSource()
                .setRadius(overworldRadius * 4.2) // Jupiter is massive!
                .setCircularOrbit(overworldOrbitalYearInOverworldDays * 4 * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                .setRotationPeriod(Vector3D.MINUS_J, 500) // Spins very quickly
                .setTextureFile(RocketNautics.path("textures/planet/gas_giant.png"))
                .setPriority(0);
    }

    public static PlanetDefinitionBuilder iceWorld() {
        return new PlanetDefinitionBuilder("sol", "ice_world")
                .setAccelerationAtSurface(11.0)
                .setParentIsShadowLightSource()
                .setRadius(overworldRadius * 1.8) // Neptune-like
                .setCircularOrbit(overworldOrbitalYearInOverworldDays * 8 * overworldDaynightCycleLengthSeconds, Vector3D.PLUS_J)
                .setRotationPeriod(Vector3D.MINUS_J, 800)
                .setTextureFile(RocketNautics.path("textures/planet/ice_planet.png"))
                .setPriority(0);
    }
}
