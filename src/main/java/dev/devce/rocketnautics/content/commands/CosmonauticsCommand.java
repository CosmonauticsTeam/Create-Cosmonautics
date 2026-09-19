package dev.devce.rocketnautics.content.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.simibubi.create.content.fluids.tank.CreativeFluidTankBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.blocks.drain_valve.DrainValveBlockEntity;
import dev.devce.rocketnautics.content.fluids.ITankPressure;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseLoader;
import dev.devce.rocketnautics.content.physics.AsteroidSpawner;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.devce.rocketnautics.content.physics.SubLevelExplosionHandler;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.devce.rocketnautics.server.telemetry.TelemetryServer;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.plot.PlotChunkHolder;
import dev.ryanhcode.sable.sublevel.plot.ServerLevelPlot;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLPaths;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Unified, clean, and intuitive command tree for Cosmonautics under /cosmo (and /rn alias).
 */
public final class CosmonauticsCommand {
    private static final SimpleCommandExceptionType NO_DEEP_SPACE = new SimpleCommandExceptionType(
            Component.literal("§c[Cosmonautics] Deep space dimension is not loaded!"));
    private static final SimpleCommandExceptionType NOT_IN_INSTANCE = new SimpleCommandExceptionType(
            Component.literal("§c[Cosmonautics] You must be located inside a Deep Space instance!"));
    private static final DynamicCommandExceptionType PLANET_NOT_FOUND = new DynamicCommandExceptionType(
            name -> Component.literal("§c[Cosmonautics] Celestial body not found: §e" + name));

    // Ship copy/paste clipboard and storage
    private record ClipboardEntry(CompoundTag tag, ChunkPos sourcePlotPos) {}
    private static final Map<UUID, ClipboardEntry> clipboard = new HashMap<>();
    private static final Path STORAGE_PATH = FMLPaths.CONFIGDIR.get().resolve("rocketnautics_ships");

    static {
        try {
            Files.createDirectories(STORAGE_PATH);
        } catch (IOException e) {
            Sable.LOGGER.error("Failed to create ship storage directory", e);
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> cosmo = buildTree("cosmo");
        LiteralArgumentBuilder<CommandSourceStack> rn = buildTree("rn");

        dispatcher.register(cosmo);
        dispatcher.register(rn);

        dispatcher.register(Commands.literal("cartridge")
            .then(Commands.argument("id", StringArgumentType.string())
                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                    dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager.listCartridges(), builder))
                .executes(ctx -> executeGiveCartridge(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
            .executes(ctx -> executeGiveCartridge(ctx.getSource(), "default")));

        dispatcher.register(Commands.literal("ship")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("explode").executes(CosmonauticsCommand::executeShipExplode)));

        dispatcher.register(Commands.literal("tank")
            .requires(source -> source.hasPermission(2))
            .then(Commands.literal("info").executes(CosmonauticsCommand::executeTankInfo))
            .then(Commands.literal("explode").executes(CosmonauticsCommand::executeTankExplode))
            .then(Commands.literal("pressure")
                .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 100.0f))
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("1.0", "3.0", "5.0", "10.0"), builder))
                    .executes(ctx -> executeTankSetPressure(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
            .then(Commands.literal("test_explosion")
                .executes(ctx -> executeTankTestExplosion(ctx.getSource(), 10.0f, 30.0f))
                .then(Commands.argument("power", FloatArgumentType.floatArg(1.0f, 50.0f))
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("5.0", "10.0", "20.0", "30.0"), builder))
                    .executes(ctx -> executeTankTestExplosion(ctx.getSource(), FloatArgumentType.getFloat(ctx, "power"), 30.0f))
                    .then(Commands.argument("distance", FloatArgumentType.floatArg(1.0f, 500.0f))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("15.0", "30.0", "50.0", "100.0"), builder))
                        .executes(ctx -> executeTankTestExplosion(ctx.getSource(), FloatArgumentType.getFloat(ctx, "power"), FloatArgumentType.getFloat(ctx, "distance")))))));
    }

    private static LiteralArgumentBuilder<CommandSourceStack> buildTree(String rootLiteral) {
        return Commands.literal(rootLiteral)
            .requires(source -> source.hasPermission(2))

            // 0. /cosmo cartridge <id>
            .then(Commands.literal("cartridge")
                .then(Commands.argument("id", StringArgumentType.string())
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                        dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager.listCartridges(), builder))
                    .executes(ctx -> executeGiveCartridge(ctx.getSource(), StringArgumentType.getString(ctx, "id"))))
                .executes(ctx -> executeGiveCartridge(ctx.getSource(), "default")))

            // 1. /cosmo orbit <planet> [altitude_above_surface] [speed] [angle]
            .then(Commands.literal("orbit")
                .then(Commands.argument("planet", StringArgumentType.string())
                    .suggests((context, builder) -> {
                        var universe = DeepSpaceData.getInstance(context.getSource().getServer()).getUniverse();
                        if (universe != null) {
                            return SharedSuggestionProvider.suggest(
                                universe.getPlanets().stream().map(p -> p.frame().getName()).toList(),
                                builder
                            );
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> executeOrbit(ctx.getSource(), StringArgumentType.getString(ctx, "planet"), null, null, null))
                    .then(Commands.argument("altitude_above_surface", DoubleArgumentType.doubleArg(10.0))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                            List.of("1000", "5000", "10000", "25000", "50000", "100000"), builder))
                        .executes(ctx -> executeOrbit(ctx.getSource(),
                            StringArgumentType.getString(ctx, "planet"),
                            DoubleArgumentType.getDouble(ctx, "altitude_above_surface"), null, null))
                        .then(Commands.argument("speed_m_s", DoubleArgumentType.doubleArg(0.0))
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("auto", "1000", "3000", "7500"), builder))
                            .executes(ctx -> executeOrbit(ctx.getSource(),
                                StringArgumentType.getString(ctx, "planet"),
                                DoubleArgumentType.getDouble(ctx, "altitude_above_surface"),
                                DoubleArgumentType.getDouble(ctx, "speed_m_s"), null))
                            .then(Commands.argument("phase_angle_deg", DoubleArgumentType.doubleArg(0.0, 360.0))
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("0", "90", "180", "270"), builder))
                                .executes(ctx -> executeOrbit(ctx.getSource(),
                                    StringArgumentType.getString(ctx, "planet"),
                                    DoubleArgumentType.getDouble(ctx, "altitude_above_surface"),
                                    DoubleArgumentType.getDouble(ctx, "speed_m_s"),
                                    DoubleArgumentType.getDouble(ctx, "phase_angle_deg")))
                            )
                        )
                    )
                )
            )

            // 2. /cosmo timescale <value | reset | get>
            .then(Commands.literal("timescale")
                .then(Commands.literal("get")
                    .executes(CosmonauticsCommand::getTimescale))
                .then(Commands.literal("reset")
                    .executes(ctx -> setTimescale(ctx.getSource(), 1)))
                .then(Commands.argument("multiplier", IntegerArgumentType.integer(1, 10000))
                    .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("1", "2", "5", "10", "50", "100", "500", "1000"), builder))
                    .executes(ctx -> setTimescale(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "multiplier"))))
            )

            // 3. /cosmo physics <gravity | calibrate | barrier | reentry>
            .then(Commands.literal("physics")
                .then(Commands.literal("gravity")
                    .then(Commands.literal("reset")
                        .executes(ctx -> {
                            GlobalSpacePhysicsHandler.resetGravityOverride();
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aGravity reset to automatic celestial calculation."), true);
                            return 1;
                        }))
                    .then(Commands.argument("value", FloatArgumentType.floatArg(-50.0f, 50.0f))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("0.0", "1.62", "3.71", "9.81", "24.79"), builder))
                        .executes(ctx -> {
                            float val = FloatArgumentType.getFloat(ctx, "value");
                            GlobalSpacePhysicsHandler.setGravityOverride(val);
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aEffective gravity set to §b" + val + " m/s²§a."), true);
                            return 1;
                        }))
                )
                .then(Commands.literal("calibrate")
                    .then(Commands.argument("multiplier", DoubleArgumentType.doubleArg(0.0, 10.0))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("0.5", "1.0", "1.5", "2.0"), builder))
                        .executes(ctx -> {
                            double mult = DoubleArgumentType.getDouble(ctx, "multiplier");
                            GlobalSpacePhysicsHandler.setCalibration(mult);
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aPhysics calibration multiplier set to §b" + mult + "§a."), true);
                            return 1;
                        }))
                )
                .then(Commands.literal("barrier")
                    .executes(ctx -> {
                        boolean current = RocketConfig.SERVER.brokenBarrier.get();
                        boolean next = !current;
                        RocketConfig.SERVER.brokenBarrier.set(next);
                        RocketConfig.SERVER_SPEC.save();
                        ctx.getSource().sendSuccess(() -> Component.literal(next
                            ? "§c[Cosmonautics] Rocket Thrust Barrier BROKEN. Max thrust limit raised to 5000N."
                            : "§a[Cosmonautics] Rocket Thrust Barrier RESTORED. Standard 1000N limit active."), true);
                        return 1;
                    })
                )
                .then(Commands.literal("reentry")
                    .executes(ctx -> {
                        GlobalSpacePhysicsHandler.reentryDebugEnabled = !GlobalSpacePhysicsHandler.reentryDebugEnabled;
                        boolean on = GlobalSpacePhysicsHandler.reentryDebugEnabled;
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aForced re-entry visual effect " + (on ? "§eENABLED" : "§7DISABLED") + "§a."), true);
                        return 1;
                    })
                )
            )

            // 4. /cosmo telemetry <status | start | stop | port>
            .then(Commands.literal("telemetry")
                .then(Commands.literal("status")
                    .executes(ctx -> {
                        boolean running = TelemetryServer.INSTANCE.isRunning();
                        int port = RocketConfig.SERVER.telemetryServerPort.get();
                        String bind = RocketConfig.SERVER.telemetryServerBind.get();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6[Cosmonautics] §eTelemetry API Server: " + (running ? "§aONLINE" : "§cOFFLINE") + "\n" +
                            "§7  • URL: §bhttp://" + bind + ":" + port + "/api/v1\n" +
                            "§7  • Dump: §bhttp://" + bind + ":" + port + "/api/v1/dump?flat=true\n" +
                            "§7  • Refresh Interval: §b" + RocketConfig.SERVER.telemetrySnapshotInterval.get() + " ticks"
                        ), false);
                        return 1;
                    })
                )
                .then(Commands.literal("start")
                    .executes(ctx -> {
                        RocketConfig.SERVER.telemetryServerEnabled.set(true);
                        RocketConfig.SERVER.telemetryServerEnabled.save();
                        TelemetryServer.INSTANCE.start(ctx.getSource().getServer());
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aTelemetry HTTP server started on port §b" + RocketConfig.SERVER.telemetryServerPort.get() + "§a."), true);
                        return 1;
                    })
                )
                .then(Commands.literal("stop")
                    .executes(ctx -> {
                        RocketConfig.SERVER.telemetryServerEnabled.set(false);
                        RocketConfig.SERVER.telemetryServerEnabled.save();
                        TelemetryServer.INSTANCE.stop();
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §eTelemetry HTTP server stopped."), true);
                        return 1;
                    })
                )
                .then(Commands.literal("port")
                    .then(Commands.argument("port_number", IntegerArgumentType.integer(1024, 65535))
                        .executes(ctx -> {
                            int p = IntegerArgumentType.getInteger(ctx, "port_number");
                            RocketConfig.SERVER.telemetryServerPort.set(p);
                            RocketConfig.SERVER.telemetryServerPort.save();
                            if (TelemetryServer.INSTANCE.isRunning()) {
                                TelemetryServer.INSTANCE.stop();
                                TelemetryServer.INSTANCE.start(ctx.getSource().getServer());
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aTelemetry server port updated to §b" + p + "§a."), true);
                            return 1;
                        }))
                )
            )

            // 5. /cosmo reload
            .then(Commands.literal("reload")
                .executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aFor datapacks/universe, use §b/reload§a."), true);
                    return 1;
                })
            )

            // 6. /cosmo asteroid <spawn | clear>
            .then(Commands.literal("asteroid")
                .then(Commands.literal("spawn")
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        AsteroidSpawner.spawnAsteroid(player, player.serverLevel());
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aSpawned near-space asteroid near §e" + player.getName().getString() + "§a."), true);
                        return 1;
                    })
                )
                .then(Commands.literal("clear")
                    .executes(ctx -> {
                        AsteroidSpawner.clearAsteroids(ctx.getSource().getLevel());
                        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aCleared all managed space asteroids."), true);
                        return 1;
                    })
                )
            )

            // 7. /cosmo jetpack [toggle]
            .then(Commands.literal("jetpack")
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    JetpackItem.toggle(player);
                    boolean active = JetpackItem.isActive(player);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aJetpack " + (active ? "§eENABLED" : "§7DISABLED") + "§a."), true);
                    return 1;
                })
            )

            // 8. /cosmo ship <copy | paste | save | load | list | delete>
            .then(Commands.literal("ship")
                .then(Commands.literal("copy").executes(CosmonauticsCommand::copyShip))
                .then(Commands.literal("paste").executes(CosmonauticsCommand::pasteShip))
                .then(Commands.literal("save")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .executes(CosmonauticsCommand::saveShip)))
                .then(Commands.literal("load")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getSavedShipNames(), builder))
                        .executes(CosmonauticsCommand::loadShip)))
                .then(Commands.literal("list").executes(CosmonauticsCommand::listShips))
                .then(Commands.literal("delete")
                    .then(Commands.argument("name", StringArgumentType.string())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(getSavedShipNames(), builder))
                        .executes(CosmonauticsCommand::deleteShip)))
                .then(Commands.literal("explode").executes(CosmonauticsCommand::executeShipExplode))
            )

            // 9. /cosmo tank <info | pressure | explode | test_explosion>
            .then(Commands.literal("tank")
                .then(Commands.literal("info").executes(CosmonauticsCommand::executeTankInfo))
                .then(Commands.literal("explode").executes(CosmonauticsCommand::executeTankExplode))
                .then(Commands.literal("pressure")
                    .then(Commands.argument("value", FloatArgumentType.floatArg(0.0f, 100.0f))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("1.0", "3.0", "5.0", "10.0"), builder))
                        .executes(ctx -> executeTankSetPressure(ctx.getSource(), FloatArgumentType.getFloat(ctx, "value")))))
                .then(Commands.literal("test_explosion")
                    .executes(ctx -> executeTankTestExplosion(ctx.getSource(), 10.0f, 30.0f))
                    .then(Commands.argument("power", FloatArgumentType.floatArg(1.0f, 50.0f))
                        .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("5.0", "10.0", "20.0", "30.0"), builder))
                        .executes(ctx -> executeTankTestExplosion(ctx.getSource(), FloatArgumentType.getFloat(ctx, "power"), 30.0f))
                        .then(Commands.argument("distance", FloatArgumentType.floatArg(1.0f, 500.0f))
                            .suggests((context, builder) -> SharedSuggestionProvider.suggest(List.of("15.0", "30.0", "50.0", "100.0"), builder))
                            .executes(ctx -> executeTankTestExplosion(ctx.getSource(), FloatArgumentType.getFloat(ctx, "power"), FloatArgumentType.getFloat(ctx, "distance")))))));
    }

    // ==========================================
    // Command Implementations
    // ==========================================

    private static int executeOrbit(CommandSourceStack source, String planetName, Double customAltitudeAboveSurface, Double customSpeed, Double customAngle) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel deepSpace = source.getServer().getLevel(RocketDimensions.DEEP_SPACE);
        if (deepSpace == null) {
            throw NO_DEEP_SPACE.create();
        }

        DeepSpaceData manager = DeepSpaceData.getInstance(source.getServer());
        UniverseDefinition universe = manager.getUniverse();

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

        CubePlanet planet = null;
        for (CubePlanet p : universe.getPlanets()) {
            if (p.frame().getName().equalsIgnoreCase(planetName)) {
                planet = p;
                break;
            }
        }

        double planetRadius = planet != null ? planet.radius() : 100000.0;
        int transitionHeight = (planet != null && planet.linkedDimension() != null) ? planet.linkedDimension().transitionHeight() : 2500;

        // Smart Altitude: if player gives 5000, they mean 5000 meters ABOVE the planet surface/atmosphere!
        double altitudeAboveSurface;
        if (customAltitudeAboveSurface != null) {
            altitudeAboveSurface = customAltitudeAboveSurface;
        } else {
            // Default safe orbital insertion altitude above surface
            altitudeAboveSurface = Math.max(transitionHeight + 10000.0, planetRadius * 0.25);
        }

        double orbitalRadius = planetRadius + altitudeAboveSurface;

        // Circular orbital speed: v = sqrt(mu / r)
        double speed;
        if (customSpeed != null && customSpeed > 0) {
            speed = customSpeed;
        } else {
            speed = Math.sqrt(gravitySource.mu() / orbitalRadius);
        }

        double angle = customAngle != null ? customAngle : 0.0;
        double angleRad = Math.toRadians(angle);

        // Orbital period in minutes: T = 2*pi * sqrt(r^3 / mu) / 60
        double periodMin = (2.0 * Math.PI * Math.sqrt(Math.pow(orbitalRadius, 3) / gravitySource.mu())) / 60.0;

        Vector3D position = new Vector3D(orbitalRadius * Math.cos(angleRad), orbitalRadius * Math.sin(angleRad), 0.0);
        Vector3D velocity = new Vector3D(-speed * Math.sin(angleRad), speed * Math.cos(angleRad), 0.0);

        DeepSpaceInstance instance = manager.claimNewInstance(2);
        TimeStampedPVCoordinates coords = new TimeStampedPVCoordinates(DeepSpaceHelper.EPOCH, position, velocity);
        instance.getPosition().init(universe, gravitySource.orekitFrame(), coords);

        Vector3dc center = instance.getCenter();
        player.teleportTo(deepSpace, center.x(), center.y(), center.z(), player.getYRot(), player.getXRot());

        // Spawn a 3x3 protective glass floor
        BlockPos platformCenter = new BlockPos((int) center.x(), (int) center.y() - 1, (int) center.z());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                deepSpace.setBlockAndUpdate(platformCenter.offset(dx, 0, dz), net.minecraft.world.level.block.Blocks.GLASS.defaultBlockState());
            }
        }

        source.sendSuccess(() -> Component.literal(
            String.format("§6[Cosmonautics] §aInserted into stable orbit of §e%s§a!\n" +
                          "§7  • Altitude above surface: §b+%,.0f m §7(Radius: §b%,.0f m§7)\n" +
                          "§7  • Orbital Speed: §b%,.1f m/s\n" +
                          "§7  • Orbital Period: §b%.1f min §7(Phase: §b%.1f°§7)",
                planetName, altitudeAboveSurface, orbitalRadius, speed, periodMin, angle)
        ), true);

        return 1;
    }

    private static int setTimescale(CommandSourceStack source, int value) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayer();
        if (player == null) return 0;
        if (!DeepSpaceHelper.isDeepSpace(player.level())) {
            throw NOT_IN_INSTANCE.create();
        }
        DeepSpaceInstance instance = DeepSpaceData.getInstance(source.getServer())
                .getInstanceForPos(player.getBlockX(), player.getBlockZ());
        if (instance == null) {
            throw NOT_IN_INSTANCE.create();
        }
        instance.getPosition().setTimescale(value);
        instance.forceClientSync();
        source.sendSuccess(() -> Component.literal("§6[Cosmonautics] §aInstance orbital timescale set to §b" + value + "x§a."), true);
        return 1;
    }

    private static int getTimescale(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) return 0;
        if (!DeepSpaceHelper.isDeepSpace(player.level())) {
            throw NOT_IN_INSTANCE.create();
        }
        DeepSpaceInstance instance = DeepSpaceData.getInstance(ctx.getSource().getServer())
                .getInstanceForPos(player.getBlockX(), player.getBlockZ());
        if (instance == null) {
            throw NOT_IN_INSTANCE.create();
        }
        int ts = instance.getPosition().getTimescale();
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aCurrent instance timescale: §b" + ts + "x§a."), false);
        return ts;
    }

    // --- Ship Management Commands ---

    private static List<String> getSavedShipNames() {
        try (Stream<Path> stream = Files.list(STORAGE_PATH)) {
            return stream.filter(p -> p.toString().endsWith(".nbt"))
                    .map(p -> p.getFileName().toString().replace(".nbt", ""))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }

    private static int copyShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();

        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
            if (subLevel != null) {
                CompoundTag tag = subLevel.getPlot().save();
                clipboard.put(player.getUUID(), new ClipboardEntry(tag, subLevel.getPlot().plotPos));
                source.sendSuccess(() -> Component.literal("§6[Cosmonautics] §aShip copied to local clipboard."), true);
                return 1;
            }
        }
        source.sendFailure(Component.literal("§c[Cosmonautics] No ship found in crosshairs!"));
        return 0;
    }

    private static int pasteShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ClipboardEntry entry = clipboard.get(player.getUUID());

        if (entry == null) {
            source.sendFailure(Component.literal("§c[Cosmonautics] Clipboard is empty! Copy a ship first via /cosmo ship copy."));
            return 0;
        }

        return spawnShip(source, player, entry);
    }

    private static int saveShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");

        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            ServerSubLevel subLevel = (ServerSubLevel) Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
            if (subLevel != null) {
                CompoundTag tag = subLevel.getPlot().save();
                CompoundTag fileTag = new CompoundTag();
                fileTag.put("Data", tag);
                fileTag.putLong("PlotPos", ChunkPos.asLong(subLevel.getPlot().plotPos.x, subLevel.getPlot().plotPos.z));

                try {
                    NbtIo.writeCompressed(fileTag, STORAGE_PATH.resolve(name + ".nbt"));
                    source.sendSuccess(() -> Component.literal("§6[Cosmonautics] §aShip saved to disk as '§e" + name + "§a'."), true);
                    return 1;
                } catch (IOException e) {
                    source.sendFailure(Component.literal("§c[Cosmonautics] Failed to save ship: " + e.getMessage()));
                    return 0;
                }
            }
        }
        source.sendFailure(Component.literal("§c[Cosmonautics] No ship found in crosshairs!"));
        return 0;
    }

    private static int loadShip(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();
        String name = StringArgumentType.getString(ctx, "name");
        Path file = STORAGE_PATH.resolve(name + ".nbt");

        if (!Files.exists(file)) {
            source.sendFailure(Component.literal("§c[Cosmonautics] Saved ship '§e" + name + "§c' does not exist!"));
            return 0;
        }

        try {
            CompoundTag fileTag = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            CompoundTag data = fileTag.getCompound("Data");
            ChunkPos plotPos = new ChunkPos(fileTag.getLong("PlotPos"));
            return spawnShip(source, player, new ClipboardEntry(data, plotPos));
        } catch (IOException e) {
            source.sendFailure(Component.literal("§c[Cosmonautics] Failed to load ship: " + e.getMessage()));
            return 0;
        }
    }

    private static int listShips(CommandContext<CommandSourceStack> ctx) {
        List<String> names = getSavedShipNames();
        if (names.isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §7No saved ships found in storage."), false);
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aSaved Ships (§e" + names.size() + "§a):\n§7  • " + String.join("\n§7  • ", names)), false);
        return names.size();
    }

    private static int deleteShip(CommandContext<CommandSourceStack> ctx) {
        String name = StringArgumentType.getString(ctx, "name");
        Path file = STORAGE_PATH.resolve(name + ".nbt");
        if (Files.exists(file)) {
            try {
                Files.delete(file);
                ctx.getSource().sendSuccess(() -> Component.literal("§6[Cosmonautics] §aDeleted ship '§e" + name + "§a'."), true);
                return 1;
            } catch (IOException e) {
                ctx.getSource().sendFailure(Component.literal("§c[Cosmonautics] Failed to delete file: " + e.getMessage()));
            }
        } else {
            ctx.getSource().sendFailure(Component.literal("§c[Cosmonautics] Ship '§e" + name + "§c' not found!"));
        }
        return 0;
    }

    private static int spawnShip(CommandSourceStack source, ServerPlayer player, ClipboardEntry entry) {
        HitResult hit = player.pick(128.0, 1.0f, false);
        Vector3d targetPos;
        if (hit.getType() == HitResult.Type.MISS) {
            net.minecraft.world.phys.Vec3 eyePos = player.getEyePosition();
            net.minecraft.world.phys.Vec3 look = player.getLookAngle();
            targetPos = new Vector3d(eyePos.x + look.x * 10.0, eyePos.y + look.y * 10.0, eyePos.z + look.z * 10.0);
        } else {
            targetPos = new Vector3d(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z);
        }

        dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer container =
                (dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer) dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(source.getLevel());
        if (container == null) return 0;
        Pose3d pose = new Pose3d(targetPos.add(0, 0.1, 0), new Quaterniond(), new Vector3d(0), new Vector3d(1));
        ServerSubLevel newShip = (ServerSubLevel) container.allocateNewSubLevel(pose);

        CompoundTag remappedTag = entry.tag.copy();
        remapBlockEntityPositions(remappedTag, entry.sourcePlotPos, newShip.getPlot().plotPos);

        newShip.getPlot().load(remappedTag);
        newShip.updateLastPose();

        source.sendSuccess(() -> Component.literal("§6[Cosmonautics] §aShip successfully spawned into world."), true);
        return 1;
    }

    private static int executeGiveCartridge(CommandSourceStack source, String id) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        if (id == null || id.isEmpty()) {
            id = "default";
        }
        dev.devce.rocketnautics.content.blocks.mfd.cartridge.CartridgeManager.getCartridgeDir(id);

        net.minecraft.world.item.ItemStack cartridge = new net.minecraft.world.item.ItemStack(dev.devce.rocketnautics.registry.RocketItems.MFD_CARTRIDGE.get());
        dev.devce.rocketnautics.content.blocks.mfd.cartridge.MFDCartridgeItem.setCartridgeId(cartridge, id);

        if (!player.getInventory().add(cartridge)) {
            player.drop(cartridge, false);
        }

        final String finalId = id;
        source.sendSuccess(() -> Component.literal("§b[MFD] Given cartridge: §e" + finalId), false);
        return 1;
    }

    public static void remapBlockEntityPositions(CompoundTag rootTag, ChunkPos sourcePlot, ChunkPos targetPlot) {
        int logSize = rootTag.contains("log_size") ? rootTag.getInt("log_size") : 7;
        int shift = logSize + 4;
        int offsetX = (targetPlot.x - sourcePlot.x) << shift;
        int offsetZ = (targetPlot.z - sourcePlot.z) << shift;

        if (offsetX == 0 && offsetZ == 0) return;
        if (!rootTag.contains("chunks")) return;

        CompoundTag chunks = rootTag.getCompound("chunks");
        for (String key : chunks.getAllKeys()) {
            CompoundTag chunkTag = chunks.getCompound(key);
            if (!chunkTag.contains("block_entities")) continue;

            net.minecraft.nbt.ListTag beList = chunkTag.getList("block_entities", net.minecraft.nbt.Tag.TAG_COMPOUND);
            for (int i = 0; i < beList.size(); i++) {
                CompoundTag beTag = beList.getCompound(i);
                if (beTag.contains("x")) beTag.putInt("x", beTag.getInt("x") + offsetX);
                if (beTag.contains("z")) beTag.putInt("z", beTag.getInt("z") + offsetZ);
            }
        }
    }

    private static ServerSubLevel findTargetShip(CommandSourceStack source, ServerPlayer player) {
        ServerSubLevel subLevel = null;
        try {
            Object obj = Sable.HELPER.getContaining(player);
            if (obj instanceof ServerSubLevel ssl) {
                subLevel = ssl;
            }
        } catch (Throwable ignored) {}

        if (subLevel == null) {
            HitResult hit = player.pick(512.0, 1.0f, false);
            if (hit instanceof BlockHitResult blockHit) {
                try {
                    Object obj = Sable.HELPER.getContaining(source.getLevel(), blockHit.getBlockPos());
                    if (obj instanceof ServerSubLevel ssl) {
                        subLevel = ssl;
                    }
                } catch (Throwable ignored) {}
            }
        }

        if (subLevel == null) {
            dev.ryanhcode.sable.api.sublevel.SubLevelContainer container =
                    dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(player.serverLevel());
            if (container != null) {
                Vec3 eye = player.getEyePosition();
                Vec3 look = player.getLookAngle().normalize();
                ServerSubLevel bestShip = null;
                double bestDistAlongRay = Double.MAX_VALUE;

                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl instanceof ServerSubLevel ssl && !ssl.isRemoved()) {
                        Vector3d center = ssl.logicalPose().position();
                        Vec3 toCenter = new Vec3(center.x - eye.x, center.y - eye.y, center.z - eye.z);
                        double t = toCenter.dot(look);
                        if (t > 1.0 && t < 2048.0) {
                            Vec3 closest = eye.add(look.scale(t));
                            double distSq = closest.distanceToSqr(center.x, center.y, center.z);

                            double radius = 15.0;
                            ServerLevelPlot plot = ssl.getPlot();
                            if (plot != null) {
                                var bounds = plot.getBoundingBox();
                                if (bounds != null) {
                                    double sx = bounds.maxX() - bounds.minX() + 1;
                                    double sy = bounds.maxY() - bounds.minY() + 1;
                                    double sz = bounds.maxZ() - bounds.minZ() + 1;
                                    radius = Math.max(Math.sqrt(sx * sx + sy * sy + sz * sz) * 0.5, 4.0);
                                }
                            }

                            double effectiveRadius = Math.max(radius * 2.5, 10.0 + t * 0.08);
                            if (distSq <= effectiveRadius * effectiveRadius && t < bestDistAlongRay) {
                                bestDistAlongRay = t;
                                bestShip = ssl;
                            }
                        }
                    }
                }
                subLevel = bestShip;
            }
        }

        return subLevel;
    }

    private static Set<FluidTankBlockEntity> findShipFluidTanks(ServerSubLevel subLevel) {
        Set<FluidTankBlockEntity> controllers = new HashSet<>();
        ServerLevelPlot plot = subLevel.getPlot();
        if (plot == null) return controllers;
        ServerLevel level = subLevel.getLevel();
        if (level == null) return controllers;

        for (PlotChunkHolder chunkHolder : plot.getLoadedChunks()) {
            LevelChunk chunk = chunkHolder.getChunk();
            if (chunk == null) continue;
            for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof FluidTankBlockEntity tankBE) {
                    FluidTankBlockEntity controller = tankBE.getControllerBE();
                    FluidTankBlockEntity target = controller != null ? controller : tankBE;
                    if (!(target instanceof CreativeFluidTankBlockEntity)) {
                        controllers.add(target);
                    }
                } else if (be instanceof DrainValveBlockEntity valve) {
                    FluidTankBlockEntity tank = valve.getTank();
                    if (tank != null) {
                        FluidTankBlockEntity controller = tank.getControllerBE();
                        FluidTankBlockEntity target = controller != null ? controller : tank;
                        if (!(target instanceof CreativeFluidTankBlockEntity)) {
                            controllers.add(target);
                        }
                    }
                }
            }
        }
        return controllers;
    }

    private static FluidTankBlockEntity findTargetTank(CommandSourceStack source, ServerPlayer player) {
        HitResult hit = player.pick(128.0, 1.0f, false);
        if (hit instanceof BlockHitResult blockHit) {
            BlockPos pos = blockHit.getBlockPos();
            ServerLevel level = source.getLevel();
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof FluidTankBlockEntity tankBE) {
                return tankBE.getControllerBE() != null ? tankBE.getControllerBE() : tankBE;
            } else if (be instanceof DrainValveBlockEntity valve) {
                FluidTankBlockEntity tank = valve.getTank();
                if (tank != null) {
                    return tank.getControllerBE() != null ? tank.getControllerBE() : tank;
                }
            }
        }
        return null;
    }

    private static int executeShipExplode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();

        ServerSubLevel ship = findTargetShip(source, player);
        if (ship == null) {
            source.sendFailure(Component.literal("§c[Cosmonautics] No ship found in crosshairs (checked up to 2048 blocks)!"));
            return 0;
        }

        Set<FluidTankBlockEntity> tanks = findShipFluidTanks(ship);
        int count = tanks.size();
        float power = count > 0 ? (20.0f + (count * 5.0f)) : 35.0f;
        dev.devce.rocketnautics.content.physics.SubLevelExplosionHandler.explodeSubLevel(ship, power);

        final int finalCount = count;
        source.sendSuccess(() -> Component.literal(
                finalCount > 0
                        ? "§6[Cosmonautics] §cExploded ship with §e" + finalCount + "§c tank multiblock(s) from a distance!"
                        : "§6[Cosmonautics] §cExploded ship hull from a distance!"), true);
        return Math.max(1, count);
    }

    private static int executeTankExplode(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();

        FluidTankBlockEntity tank = findTargetTank(source, player);
        if (tank != null) {
            FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            if (controller instanceof CreativeFluidTankBlockEntity) {
                source.sendFailure(Component.literal("§c[Cosmonautics] Creative Fluid Tanks are immune to explosions!"));
                return 0;
            }
            if (controller instanceof ITankPressure p) {
                p.triggerCatastrophicExplosion();
                source.sendSuccess(() -> Component.literal("§6[Cosmonautics] §cExploded targeted fluid tank!"), true);
                return 1;
            }
        }

        ServerSubLevel ship = findTargetShip(source, player);
        if (ship != null) {
            Set<FluidTankBlockEntity> tanks = findShipFluidTanks(ship);
            int count = tanks.size();
            float power = count > 0 ? (20.0f + (count * 5.0f)) : 35.0f;
            dev.devce.rocketnautics.content.physics.SubLevelExplosionHandler.explodeSubLevel(ship, power);
            final int finalCount = count;
            source.sendSuccess(() -> Component.literal(
                    finalCount > 0
                            ? "§6[Cosmonautics] §cExploded ship with §e" + finalCount + "§c tank multiblock(s) from a distance!"
                            : "§6[Cosmonautics] §cExploded ship hull from a distance!"), true);
            return Math.max(1, count);
        }

        source.sendFailure(Component.literal("§c[Cosmonautics] Look at a fluid tank or a ship!"));
        return 0;
    }

    private static int executeTankInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayerOrException();

        FluidTankBlockEntity tank = findTargetTank(source, player);
        if (tank != null) {
            FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            if (controller instanceof CreativeFluidTankBlockEntity) {
                source.sendSuccess(() -> Component.literal(
                        String.format(Locale.ROOT,
                                "§6[Cosmonautics Tank Info]\n" +
                                "§7  • Controller: §b[%d, %d, %d] §7(%d blocks)\n" +
                                "§7  • Type: §aCreative Fluid Tank\n" +
                                "§7  • Pressure: §aIMMUNE §7(Infinite capacity, no overpressure)",
                                controller.getBlockPos().getX(), controller.getBlockPos().getY(), controller.getBlockPos().getZ(),
                                controller.getTotalTankSize())
                ), false);
                return 1;
            }
            if (controller instanceof ITankPressure p) {
                float pressure = p.getPressure();
                float max = p.getMaxPressure();
                boolean venting = p.isVenting();

                String fluidInfo = "Empty";
                try {
                    var fluidTank = controller.getTank(0);
                    if (fluidTank != null && !fluidTank.getFluid().isEmpty()) {
                        fluidInfo = String.format(Locale.ROOT, "%s (%,d / %,d mB)",
                                fluidTank.getFluid().getHoverName().getString(),
                                fluidTank.getFluid().getAmount(),
                                fluidTank.getCapacity());
                    } else if (fluidTank != null) {
                        fluidInfo = String.format(Locale.ROOT, "Empty (0 / %,d mB)", fluidTank.getCapacity());
                    }
                } catch (Throwable ignored) {}

                double altitude = controller.getBlockPos().getY();
                SubLevel subLevel = null;
                try {
                    Object obj = Sable.HELPER.getContaining(controller.getLevel(), controller.getBlockPos());
                    if (obj instanceof SubLevel sl) {
                        subLevel = sl;
                        altitude = sl.logicalPose().position().y();
                    }
                } catch (Throwable ignored) {}

                double fuelMass = 0.0;
                double massPerB = 0.0;
                try {
                    var fluidTank = controller.getTank(0);
                    if (fluidTank != null && !fluidTank.getFluid().isEmpty()) {
                        massPerB = dev.devce.rocketnautics.content.physics.FluidMassHelper.getMassPerBucket(fluidTank.getFluid());
                        fuelMass = (fluidTank.getFluidAmount() / 1000.0) * massPerB;
                    }
                } catch (Throwable ignored) {}

                double altThreshold = RocketConfig.SERVER.tankExplosionAltitude.get();
                final String fInfo = fluidInfo + (massPerB > 0 ? String.format(Locale.ROOT, " §7[%.2f kg/B]", massPerB) : "");
                final double finalAlt = altitude;
                final double finalFuelMass = fuelMass;
                final String slDesc = (subLevel != null ? "§aShip (" + subLevel.getUniqueId().toString().substring(0, 8) + "...)" : "§7World");

                source.sendSuccess(() -> Component.literal(
                        String.format(Locale.ROOT,
                                "§6[Cosmonautics Tank Info]\n" +
                                "§7  • Controller: §b[%d, %d, %d] §7(%d blocks)\n" +
                                "§7  • Pressure: §e%.2f / %.1f bar §7(Venting: %s§7)\n" +
                                "§7  • Contents: §b%s §7(Fuel Mass: §e%.1f kg§7)\n" +
                                "§7  • Altitude: §b%.1f m §7(Explosion Threshold: §c>%.0f m§7)\n" +
                                "§7  • SubLevel: %s",
                                controller.getBlockPos().getX(), controller.getBlockPos().getY(), controller.getBlockPos().getZ(),
                                controller.getTotalTankSize(),
                                pressure, max, (venting ? "§aYES" : "§cNO"),
                                fInfo, finalFuelMass,
                                finalAlt, altThreshold,
                                slDesc)
                ), false);
                return 1;
            }
        }

        ServerSubLevel ship = findTargetShip(source, player);
        if (ship != null) {
            Set<FluidTankBlockEntity> tanks = findShipFluidTanks(ship);
            double totalMass = ship.getMassTracker().getMass();
            double fuelMass = dev.devce.rocketnautics.content.physics.SubLevelFuelMassTracker.getTotalFuelMass(ship);
            double dryMass = Math.max(0.0, totalMass - fuelMass);

            StringBuilder sb = new StringBuilder(String.format(Locale.ROOT,
                    "§6[Cosmonautics Ship Mass & Tanks]:\n" +
                    "§7  • Dry Mass: §e%.1f kg\n" +
                    "§7  • Fuel Mass: §b%.1f kg\n" +
                    "§7  • Total Mass: §a%.1f kg\n" +
                    "§6Tanks (§e%d§6):\n",
                    dryMass, fuelMass, totalMass, tanks.size()));
            for (FluidTankBlockEntity t : tanks) {
                if (t instanceof ITankPressure p) {
                    sb.append(String.format(Locale.ROOT, "§7  • [%d, %d, %d]: §e%.2f/%.1f bar§7, size: §b%d blocks§7\n",
                            t.getBlockPos().getX(), t.getBlockPos().getY(), t.getBlockPos().getZ(),
                            p.getPressure(), p.getMaxPressure(), t.getTotalTankSize()));
                }
            }
            source.sendSuccess(() -> Component.literal(sb.toString().trim()), false);
            return Math.max(1, tanks.size());
        }

        source.sendFailure(Component.literal("§c[Cosmonautics] No fluid tank or ship found in crosshairs!"));
        return 0;
    }

    private static int executeTankSetPressure(CommandSourceStack source, float pressure) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        FluidTankBlockEntity tank = findTargetTank(source, player);
        if (tank != null) {
            FluidTankBlockEntity controller = tank.getControllerBE() != null ? tank.getControllerBE() : tank;
            if (controller instanceof CreativeFluidTankBlockEntity) {
                source.sendFailure(Component.literal("§c[Cosmonautics] Creative Fluid Tanks are immune to overpressure!"));
                return 0;
            }
            if (controller instanceof ITankPressure p) {
                p.setPressure(pressure);
                controller.sendData();
                source.sendSuccess(() -> Component.literal(
                        String.format(Locale.ROOT, "§6[Cosmonautics] §aSet tank pressure at §b[%d, %d, %d]§a to §e%.2f bar§a.",
                                controller.getBlockPos().getX(), controller.getBlockPos().getY(), controller.getBlockPos().getZ(), pressure)), true);
                return 1;
            }
        }

        ServerSubLevel ship = findTargetShip(source, player);
        if (ship != null) {
            Set<FluidTankBlockEntity> tanks = findShipFluidTanks(ship);
            if (!tanks.isEmpty()) {
                for (FluidTankBlockEntity t : tanks) {
                    if (t instanceof ITankPressure p) {
                        p.setPressure(pressure);
                        t.sendData();
                    }
                }
                final int count = tanks.size();
                source.sendSuccess(() -> Component.literal(
                        String.format(Locale.ROOT, "§6[Cosmonautics] §aSet pressure of §e%d§a tank multiblock(s) on ship to §e%.2f bar§a.",
                                count, pressure)), true);
                return count;
            }
        }

        source.sendFailure(Component.literal("§c[Cosmonautics] No fluid tank or ship found in crosshairs!"));
        return 0;
    }

    private static int executeTankTestExplosion(CommandSourceStack source, float power, float distance) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle().normalize();

        HitResult hit = player.pick(distance, 1.0f, false);
        Vec3 hitPos;
        if (hit.getType() != HitResult.Type.MISS) {
            hitPos = hit.getLocation();
        } else {
            hitPos = eye.add(look.scale(distance));
        }
        ServerLevel level = player.serverLevel();
        SubLevelExplosionHandler.sendExplosionParticle(level, hitPos.x, hitPos.y, hitPos.z, power);

        source.sendSuccess(() -> Component.literal(
                String.format(Locale.ROOT, "§6[Cosmonautics] §aTriggered visual explosion (power: §e%.1f§a, distance: §e%.1f§a blocks) at §b[%.1f, %.1f, %.1f]§a.",
                        power, eye.distanceTo(hitPos), hitPos.x, hitPos.y, hitPos.z)), true);
        return 1;
    }
}
