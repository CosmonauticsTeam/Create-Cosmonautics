package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import net.minecraft.commands.SharedSuggestionProvider;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3dc;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.stream.Collectors;

public final class OrbitCommand {
    private static final SimpleCommandExceptionType NO_DEEP_SPACE = new SimpleCommandExceptionType(Component.literal("Deep space dimension is not loaded!"));
    private static final DynamicCommandExceptionType PLANET_NOT_FOUND = new DynamicCommandExceptionType(
        name -> Component.literal("Celestial body not found: " + name)
    );

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("rn")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("orbit")
                .then(Commands.argument("planet", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        DeepSpaceData.getInstance(context.getSource().getServer()).getUniverse().getPlanets().stream()
                            .map(p -> p.frame().getName())
                            .collect(Collectors.toList()),
                        builder
                    ))
                    .executes(context -> teleportToOrbit(context.getSource(), 
                        StringArgumentType.getString(context, "planet"), null, null, null))
                    .then(Commands.argument("altitude", DoubleArgumentType.doubleArg(100.0))
                        .executes(context -> teleportToOrbit(context.getSource(), 
                            StringArgumentType.getString(context, "planet"), 
                            DoubleArgumentType.getDouble(context, "altitude"), null, null))
                        .then(Commands.argument("speed", DoubleArgumentType.doubleArg(0.0))
                            .executes(context -> teleportToOrbit(context.getSource(), 
                                StringArgumentType.getString(context, "planet"), 
                                DoubleArgumentType.getDouble(context, "altitude"), 
                                DoubleArgumentType.getDouble(context, "speed"), null))
                            .then(Commands.argument("angle", DoubleArgumentType.doubleArg(0.0, 360.0))
                                .executes(context -> teleportToOrbit(context.getSource(), 
                                    StringArgumentType.getString(context, "planet"), 
                                    DoubleArgumentType.getDouble(context, "altitude"), 
                                    DoubleArgumentType.getDouble(context, "speed"), 
                                    DoubleArgumentType.getDouble(context, "angle")))
                            )
                        )
                    )
                )
            )
        );
    }

    private static int teleportToOrbit(CommandSourceStack source, String planetName, Double customAltitude, Double customSpeed, Double customAngle) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel deepSpace = source.getServer().getLevel(RocketDimensions.DEEP_SPACE);
        if (deepSpace == null) {
            throw NO_DEEP_SPACE.create();
        }

        DeepSpaceData manager = DeepSpaceData.getInstance(source.getServer());
        UniverseDefinition universe = manager.getUniverse();

        // Find the gravity source corresponding to the planet name
        PointGravitySource gravitySource = null;
        for (PointGravitySource src : universe.getGravitySources()) {
            if (src.frame().getName().equalsIgnoreCase(planetName)) {
                gravitySource = src;
                break;
            }
        }

        if (gravitySource == null) {
            throw PLANET_NOT_FOUND.create(planetName);
        }

        // Find the planet metadata to get its physical radius
        CubePlanet planet = null;
        for (CubePlanet p : universe.getPlanets()) {
            if (p.frame().getName().equalsIgnoreCase(planetName)) {
                planet = p;
                break;
            }
        }

        double radius = planet != null ? planet.radius() : 100000.0;
        
        // Calculate orbit parameters
        double altitude;
        if (customAltitude != null) {
            altitude = customAltitude;
        } else {
            // Default to 1.5 times the radius, or use the planet's transition height if available
            if (planet != null && planet.linkedDimension() != null) {
                altitude = radius + planet.linkedDimension().transitionHeight() * 1.2;
            } else {
                altitude = radius * 1.5;
            }
        }

        double speed;
        if (customSpeed != null) {
            speed = customSpeed;
        } else {
            // Circular orbit velocity: v = sqrt(mu / r)
            speed = Math.sqrt(gravitySource.mu() / altitude);
        }

        double angle = customAngle != null ? customAngle : 0.0;
        double angleRad = Math.toRadians(angle);

        // Compute position and velocity in orbit plane
        Vector3D position = new Vector3D(altitude * Math.cos(angleRad), altitude * Math.sin(angleRad), 0.0);
        Vector3D velocity = new Vector3D(-speed * Math.sin(angleRad), speed * Math.cos(angleRad), 0.0);

        // Claim a 2x2 chunk region (size 2 -> powerSize 0 -> 2x2 chunks)
        DeepSpaceInstance instance = manager.claimNewInstance(2);
        
        TimeStampedPVCoordinates coords = new TimeStampedPVCoordinates(DeepSpaceHelper.EPOCH, position, velocity);
        instance.getPosition().init(universe, gravitySource.orekitFrame(), coords);

        // Teleport the player to the center of the instance
        Vector3dc center = instance.getCenter();
        player.teleportTo(deepSpace, center.x(), center.y(), center.z(), player.getYRot(), player.getXRot());

        // Spawn a 3x3 platform of glass at player's feet so they don't fall instantly
        BlockPos platformCenter = new BlockPos((int)center.x(), (int)center.y() - 1, (int)center.z());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deepSpace.setBlockAndUpdate(platformCenter.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            }
        }

        source.sendSuccess(() -> Component.literal(
            String.format("Teleported to the orbit of '%s' (Altitude: %.1f m, Speed: %.1f m/s, Phase: %.1f°)", 
                planetName, altitude, speed, angle)
        ), true);

        return 1;
    }
}
