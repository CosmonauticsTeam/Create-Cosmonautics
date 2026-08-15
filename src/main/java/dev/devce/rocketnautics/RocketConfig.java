package dev.devce.rocketnautics;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration class for RocketNautics.
 * Uses NeoForge's ModConfigSpec to define and register server and client-side settings.
 */
public class RocketConfig {
    public static final ModConfigSpec SERVER_SPEC;
    public static final Server SERVER;

    public static final ModConfigSpec CLIENT_SPEC;
    public static final Client CLIENT;

    static {
        final Pair<Server, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Server::new);
        SERVER_SPEC = specPair.getRight();
        SERVER = specPair.getLeft();

        final Pair<Client, ModConfigSpec> clientPair = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT_SPEC = clientPair.getRight();
        CLIENT = clientPair.getLeft();
    }

    /**
     * Server-side configuration settings.
     * These settings are synced from the server to all connected clients.
     */
    public static class Server {
        public final ModConfigSpec.IntValue maxFuelConsumption;
        public final ModConfigSpec.DoubleValue jetpackThrust;
        public final ModConfigSpec.IntValue jetpackThrustConsumption;
        public final ModConfigSpec.DoubleValue legThrusterThrustFactor;
        public final ModConfigSpec.IntValue legThrusterBaseConsumption;
        public final ModConfigSpec.IntValue ignitionFlow;
        public final ModConfigSpec.IntValue steamMinFlow;
        public final ModConfigSpec.IntValue entitySpeedLimit;
        public final ModConfigSpec.BooleanValue enableEngineDebugLogging;
        public final ModConfigSpec.BooleanValue brokenBarrier;
        public final ModConfigSpec.DoubleValue sonicBoomSpeedThreshold;
        public final ModConfigSpec.DoubleValue magneticStabilizerStrength;
        public final ModConfigSpec.DoubleValue gyrodyneStrength;

        public final ModConfigSpec.EnumValue<PlanetShape> planetShape;

        public Server(ModConfigSpec.Builder builder) {
            builder.push("Thrusters");
            maxFuelConsumption = builder
                    .comment("Maximum fuel consumption in mB/tick")
                    .defineInRange("maxFuelConsumption", 40, 1, 1000);
            ignitionFlow = builder
                    .comment("Flow threshold for full ignition (mB/tick)")
                    .defineInRange("ignitionFlow", 5, 1, 100);
            steamMinFlow = builder
                    .comment("Flow threshold for pre-ignition steam phase (mB/tick)")
                    .defineInRange("steamMinFlow", 2, 1, 100);
            entitySpeedLimit = builder
                    .comment("Maximum speed in m/s entities can reach in space before drag kicks in")
                    .defineInRange("entitySpeedLimit", 80, 0, 320);
            enableEngineDebugLogging = builder
                    .comment("Enable debug logging for engine fuel and thrust (can cause spam)")
                    .define("enableEngineDebugLogging", false);
            brokenBarrier = builder
                    .comment("Allow engine thrust to exceed standard limits (up to 5000N)")
                    .define("brokenBarrier", false);
            sonicBoomSpeedThreshold = builder
                    .comment("Speed in blocks/second (m/s) at which a ship breaks the sound barrier and triggers a sonic boom")
                    .defineInRange("sonicBoomSpeedThreshold", 166.0, 1.0, 500.0);
            builder.pop();

            builder.push("Jetpack");
            jetpackThrust = builder
                    .comment("Acceleration of the jetpack")
                    .defineInRange("jetpackThrust", 0.1, 0.05, 0.5);
            jetpackThrustConsumption = builder
                    .comment("Maximum consumption per tick of jetpack fuel")
                    .defineInRange("jetpackThrustConsumption", 10, 1, 100);
            legThrusterThrustFactor = builder
                    .comment("Fraction of the jetpack's acceleration")
                    .defineInRange("legThrusterThrustFactor", 0.8d, 0d, 1.0d);
            legThrusterBaseConsumption = builder
                    .comment("Base consumption per tick of pressurized air while leg thrusters are active")
                    .defineInRange("legThrusterBaseConsumption", 2, 1, 100);
            builder.pop();
            magneticStabilizerStrength = builder
                    .comment("Strength of the Magnetic Stabilizer in dampening angular momentum")
                    .defineInRange("magneticStabilizerStrength", 50d, 1, 1000000);
            gyrodyneStrength = builder
                    .comment("Torque strength of the Gyrodyne (Reaction Wheel) in stabilizing and reorienting ships")
                    .defineInRange("gyrodyneStrength", 100d, 1d, 1000000d);
            
            builder.push("Space");
            planetShape = builder
                    .comment("Selects the shape of planets in the sky:")
                    .comment(" - CUBE: Classic Minecraft cube-shaped planets.")
                    .comment(" - SPHERE: Smooth sphere-shaped planets.")
                    .defineEnum("planetShape", PlanetShape.CUBE);
            builder.pop();
        }
    }



    public enum PlanetShape {
        CUBE,
        SPHERE
    }

    public enum SkyRenderingSystem {
        LEGACY,
        MODERN
    }

    public enum SkyboxExposure {
        LOW,
        HIGH
    }

    /**
     * Client-side configuration settings.
     * These settings are local to each player's client.
     */
    public static class Client {
        public final ModConfigSpec.BooleanValue hasShownWorldBorderWarning;
        public final ModConfigSpec.DoubleValue shakeIntensity;
        public final ModConfigSpec.DoubleValue shakeRadius;
        public final ModConfigSpec.BooleanValue enableDynamicRenderDistance;
        public final ModConfigSpec.BooleanValue showDebugOverlay;
        public final ModConfigSpec.IntValue planetRenderMaximumScale;
        public final ModConfigSpec.DoubleValue orbitPredictionAngularThreshold;
        public final ModConfigSpec.IntValue orbitPredictionSteps;
        public final ModConfigSpec.BooleanValue enableCustomSky;
        public final ModConfigSpec.BooleanValue enablePlumeMerging;
        public final ModConfigSpec.DoubleValue plumeMergeRadius;
        public final ModConfigSpec.EnumValue<SkyRenderingSystem> skyRenderingSystem;
        public final ModConfigSpec.EnumValue<SkyboxExposure> skyboxExposure;
        public final ModConfigSpec.BooleanValue enableSpaceLighting;
        public final ModConfigSpec.BooleanValue enableSpaceShadowMaps;

        public Client(ModConfigSpec.Builder builder) {
            hasShownWorldBorderWarning = builder
                    .comment("Has the world border warning been shown to the player?")
                    .define("hasShownWorldBorderWarning", false);

            builder.push("Visuals");
            shakeIntensity = builder
                    .comment("Intensity multiplier for camera shake near engines")
                    .defineInRange("shakeIntensity", 0.5, 0.0, 5.0);
            shakeRadius = builder
                    .comment("Radius in blocks where camera shake is felt")
                    .defineInRange("shakeRadius", 8.0, 1.0, 64.0);
            enableDynamicRenderDistance = builder
                    .comment("Enable automatic render distance adjustment based on altitude")
                    .define("enableDynamicRenderDistance", true);
            enableCustomSky = builder
                    .comment("Enable custom sky rendering (nebula, stars, planet beneath, custom sun/moon etc). Disable to return to standard Minecraft sky.")
                    .define("enableCustomSky", false);
            enablePlumeMerging = builder
                    .comment("Enable merging of adjacent thruster plumes into a single larger plume cluster")
                    .define("enablePlumeMerging", true);
            plumeMergeRadius = builder
                    .comment("Distance threshold in blocks within which adjacent engine plumes are clustered and merged")
                    .defineInRange("plumeMergeRadius", 4.0, 1.0, 16.0);
            showDebugOverlay = builder
                    .comment("Show the Cosmonautics debug overlay (Alt/Speed/etc)")
                    .define("showDebugOverlay", false);
            planetRenderMaximumScale = builder
                    .comment("The maximum texture scale for the planet render.")
                    .comment("Recommended scale for the render to maintain visual structure is 15 or 16.")
                    .defineInRange("planetRenderMaximumScale", 100, SkyDataHandler.MIN_POWER_SIZE, 100);
            orbitPredictionAngularThreshold = builder
                    .comment("Controls the rotation in degrees between each step in the hologram table's orbit prediction.")
                    .comment("A smaller number will increase visual fidelity and accuracy, but reduce prediction length and increase the cost of rendering the orbits of planets.")
                    .defineInRange("orbitPredictionAngularThreshold", 4d, 0.5d, 20d);
            orbitPredictionSteps = builder
                    .comment("The number of steps to compute in the hologram table's orbit prediction.")
                    .comment("Set to zero to disable the prediction entirely. Too many steps can cause lag!")
                    .defineInRange("orbitPredictionSteps", 1000, 0, 10000);
            skyRenderingSystem = builder
                    .comment("Sky Rendering System:")
                    .comment(" - LEGACY: The legacy rendering system is provided as is, and will not receive future support.")
                    .comment(" - MODERN: In the modern rendering system we work only with deep space.")
                    .defineEnum("skyRenderingSystem", SkyRenderingSystem.MODERN);
            skyboxExposure = builder
                    .comment("Deep Space skybox exposure level:")
                    .comment(" - LOW: Atmospheric dark space background (Default).")
                    .comment(" - HIGH: Brighter, vivid space nebula.")
                    .defineEnum("skyboxExposure", SkyboxExposure.LOW);
            enableSpaceLighting = builder
                    .comment("Enable realistic space directional lighting and PBR shading for ships and structures in Deep Space.")
                    .define("enableSpaceLighting", true);
            enableSpaceShadowMaps = builder
                    .comment("Enable real-time directional shadow mapping in Deep Space.")
                    .define("enableSpaceShadowMaps", true);
            builder.pop();
        }
    }
}
