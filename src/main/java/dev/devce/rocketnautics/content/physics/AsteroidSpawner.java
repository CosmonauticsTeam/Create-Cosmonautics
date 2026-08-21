package dev.devce.rocketnautics.content.physics;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.companion.math.Pose3d;
import dev.ryanhcode.sable.platform.SableEventPlatform;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * System for procedurally spawning and generating asteroids in space.
 * Asteroids are generated as SubLevels with randomized shapes (lumps and craters) 
 * and varying compositions (stone, deepslate, ores).
 */
@EventBusSubscriber(modid = RocketNautics.MODID)
public class AsteroidSpawner {

    private static final Random RANDOM = new Random();
    /** Probability of an asteroid spawning near a player per tick. */
    private static final double SPAWN_CHANCE = 0.000083; 
    /** Minimum distance from player to spawn an asteroid. */
    private static final double MIN_SPAWN_DIST = 150.0;
    /** Maximum distance from player to spawn an asteroid. */
    private static final double MAX_SPAWN_DIST = 400.0;
    /** Distance at which an asteroid becomes 'permanent' and won't despawn. */
    private static final double DISCOVERY_DIST_SQ = 80.0 * 80.0; 
    /** Distance at which a non-permanent asteroid will despawn. */
    private static final double DESPAWN_DIST_SQ = 600.0 * 600.0; 
    
    private static final Set<UUID> PENDING_IMPULSE = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> ASTEROIDS = ConcurrentHashMap.newKeySet();
    private static final Set<UUID> PERMANENT_ASTEROIDS = ConcurrentHashMap.newKeySet();

    /**
     * Initializes the asteroid management cycle.
     * Handles initial physics impulses and despawning of distant/undiscovered asteroids.
     */
    public static void init() {
        SableEventPlatform.INSTANCE.onPhysicsTick((physicsSystem, timeStep) -> {
            ServerLevel level = physicsSystem.getLevel();
            ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
            if (container == null) return;
            
            Iterator<UUID> it = ASTEROIDS.iterator();
            while (it.hasNext()) {
                UUID uuid = it.next();
                ServerSubLevel asteroid = findSubLevel(container, uuid);
                
                if (asteroid == null) {
                    it.remove();
                    continue;
                }

                // Apply initial movement/rotation to new asteroids
                if (PENDING_IMPULSE.contains(uuid)) {
                    RigidBodyHandle handle = physicsSystem.getPhysicsHandle(asteroid);
                    if (handle != null) {
                        applyInitialImpulse(asteroid, handle);
                        PENDING_IMPULSE.remove(uuid);
                    }
                }

                Vector3d pos = asteroid.logicalPose().position();
                boolean nearPlayerForDiscovery = false;
                boolean nearPlayerForDespawn = false;

                // Check player proximity
                for (ServerPlayer player : level.players()) {
                    double distSq = pos.distanceSquared(player.getX(), player.getY(), player.getZ());
                    if (distSq < DISCOVERY_DIST_SQ) {
                        nearPlayerForDiscovery = true;
                    }
                    if (distSq < DESPAWN_DIST_SQ) {
                        nearPlayerForDespawn = true;
                    }
                }

                // If player gets close, the asteroid stays in the world permanently
                if (nearPlayerForDiscovery && !PERMANENT_ASTEROIDS.contains(uuid)) {
                    PERMANENT_ASTEROIDS.add(uuid);
                    RocketNautics.LOGGER.info("Asteroid {} marked as PERMANENT (Player discovered it)", uuid);
                }
                
                // Despawn logic for distant asteroids
                if (!nearPlayerForDespawn) {
                    if (PERMANENT_ASTEROIDS.contains(uuid)) {
                        // Permanent asteroids just stop being ticked but aren't deleted
                        it.remove();
                    } else {
                        // Transient asteroids are deleted if never seen
                        asteroid.markRemoved();
                        it.remove();
                        RocketNautics.LOGGER.info("Transient asteroid {} despawned (not discovered)", uuid);
                    }
                }
            }
        });
    }

    
    public static void clearAsteroids(ServerLevel level) {
        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) return;

        Iterator<UUID> it = ASTEROIDS.iterator();
        while (it.hasNext()) {
            UUID uuid = it.next();
            ServerSubLevel asteroid = findSubLevel(container, uuid);
            if (asteroid != null) {
                asteroid.markRemoved();
            }
            it.remove();
        }
        PENDING_IMPULSE.clear();
    }
    
    private static ServerSubLevel findSubLevel(ServerSubLevelContainer container, UUID uuid) {
        for (SubLevel sl : container.getAllSubLevels()) {
            if (sl.getUniqueId().equals(uuid)) return (ServerSubLevel) sl;
        }
        return null;
    }

    private static void applyInitialImpulse(ServerSubLevel asteroid, RigidBodyHandle handle) {
        double mass = asteroid.getMassTracker().getMass();
        
        Vector3d vel = new Vector3d(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5).mul(2.0 * mass);
        Vector3d angVel = new Vector3d(RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5, RANDOM.nextDouble() - 0.5).mul(0.5 * mass);
        
        handle.applyLinearImpulse(vel);
        handle.applyAngularImpulse(angVel);
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.level().isClientSide) return;

        ServerLevel level = (ServerLevel) player.level();
        
        
        boolean inSpace = level.dimension().location().getPath().equals("space") || player.getY() > 5000;
        if (!inSpace) return;

        if (RANDOM.nextDouble() < SPAWN_CHANCE) {
            spawnAsteroid(player, level);
        }
    }

    /**
     * Spawns a new asteroid at a random location around a player.
     */
    public static void spawnAsteroid(ServerPlayer player, ServerLevel level) {
        ServerSubLevelContainer container = (ServerSubLevelContainer) SubLevelContainer.getContainer(level);
        if (container == null) return;

        // Calculate random spherical coordinates for spawn position
        double theta = RANDOM.nextDouble() * Math.PI * 2;
        double phi = Math.acos(2.0 * RANDOM.nextDouble() - 1.0);
        double r = MIN_SPAWN_DIST + RANDOM.nextDouble() * (MAX_SPAWN_DIST - MIN_SPAWN_DIST);
        
        double x = r * Math.sin(phi) * Math.cos(theta);
        double y = r * Math.sin(phi) * Math.sin(theta);
        double z = r * Math.cos(phi);

        Vector3d spawnPos = new Vector3d(player.getX() + x, player.getY() + y, player.getZ() + z);
        Quaterniond rot = new Quaterniond().rotateXYZ(RANDOM.nextDouble() * Math.PI, RANDOM.nextDouble() * Math.PI, RANDOM.nextDouble() * Math.PI);
        
        Pose3d pose = new Pose3d(spawnPos, rot, new Vector3d(0), new Vector3d(1));
        ServerSubLevel newShip = (ServerSubLevel) container.allocateNewSubLevel(pose);
        UUID uuid = newShip.getUniqueId();

        try {
            newShip.setName("Asteroid " + uuid.toString().substring(0, 4));
        } catch (Throwable ignored) {}

        RocketNautics.LOGGER.info("SPAWN: Asteroid {} allocated at {} (Plot: {})", uuid, spawnPos, newShip.getPlot().getChunkMin());
        
        long seed = RANDOM.nextLong();
        generateAsteroidBlocks(level, newShip, seed);
        saveAsteroidToDisk(uuid, seed, spawnPos);
        
        ASTEROIDS.add(uuid);
        PENDING_IMPULSE.add(uuid);
    }
    
    private static final com.mojang.serialization.Codec<PalettedContainer<net.minecraft.world.level.block.state.BlockState>> BLOCK_STATE_CODEC = 
        PalettedContainer.codecRW(
            net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY, 
            net.minecraft.world.level.block.state.BlockState.CODEC, 
            PalettedContainer.Strategy.SECTION_STATES, 
            Blocks.AIR.defaultBlockState()
        );

    /**
     * Procedurally generates the block structure of an asteroid.
     * Combines multiple spheres (lumps) and carves out craters to create a realistic jagged shape.
     */
    private static void generateAsteroidBlocks(ServerLevel world, ServerSubLevel asteroid, long seed) {
        Random random = new Random(seed);
        
        
        int baseRadius = 6 + random.nextInt(8); 
        int numLumps = 5 + random.nextInt(5);
        int numCraters = 3 + random.nextInt(4);
        
        
        int centerX = 64;
        int centerY = 64;
        int centerZ = 64;

        List<Sphere> lumps = new java.util.ArrayList<>();
        
        lumps.add(new Sphere(centerX, centerY, centerZ, baseRadius));
        
        
        for (int i = 0; i < numLumps; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double dist = baseRadius * 0.7;
            
            double lx = centerX + dist * Math.sin(phi) * Math.cos(angle);
            double ly = centerY + dist * Math.sin(phi) * Math.sin(angle);
            double lz = centerZ + dist * Math.cos(phi);
            double lr = baseRadius * (0.4 + random.nextDouble() * 0.5);
            
            lumps.add(new Sphere(lx, ly, lz, lr));
        }

        
        List<Sphere> craters = new java.util.ArrayList<>();
        for (int i = 0; i < numCraters; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double phi = Math.acos(2.0 * random.nextDouble() - 1.0);
            double dist = baseRadius;
            
            double cx = centerX + dist * Math.sin(phi) * Math.cos(angle);
            double cy = centerY + dist * Math.sin(phi) * Math.sin(angle);
            double cz = centerZ + dist * Math.cos(phi);
            double cr = 3 + random.nextDouble() * 4;
            
            craters.add(new Sphere(cx, cy, cz, cr));
        }

        
        CompoundTag root = new CompoundTag();
        root.putInt("log_size", 7);
        root.putInt("data_version", 1);
        root.putString("biome", "minecraft:the_void");

        CompoundTag chunksTag = new CompoundTag();
        Map<Long, Map<Integer, PalettedContainer<net.minecraft.world.level.block.state.BlockState>>> chunkMap = new java.util.HashMap<>();

        
        int bounds = baseRadius * 2 + 10;
        for (int dx = -bounds; dx <= bounds; dx++) {
            for (int dy = -bounds; dy <= bounds; dy++) {
                for (int dz = -bounds; dz <= bounds; dz++) {
                    double vx = centerX + dx;
                    double vy = centerY + dy;
                    double vz = centerZ + dz;
                    
                    
                    boolean inside = false;
                    double noise = (random.nextDouble() - 0.5) * 1.5; 
                    
                    for (Sphere s : lumps) {
                        double d2 = (vx-s.x)*(vx-s.x) + (vy-s.y)*(vy-s.y) + (vz-s.z)*(vz-s.z);
                        if (d2 < (s.r + noise) * (s.r + noise)) {
                            inside = true;
                            break;
                        }
                    }
                    
                    
                    if (inside) {
                        for (Sphere c : craters) {
                            double d2 = (vx-c.x)*(vx-c.x) + (vy-c.y)*(vy-c.y) + (vz-c.z)*(vz-c.z);
                            if (d2 < c.r * c.r) {
                                inside = false;
                                break;
                            }
                        }
                    }
                    
                    if (inside) {
                        long chunkKey = ChunkPos.asLong((int)vx >> 4, (int)vz >> 4);
                        int sectionY = (int)vy >> 4;
                        
                        var sections = chunkMap.computeIfAbsent(chunkKey, k -> new java.util.HashMap<>());
                        var container = sections.computeIfAbsent(sectionY, k -> new PalettedContainer<>(
                            net.minecraft.world.level.block.Block.BLOCK_STATE_REGISTRY, 
                            Blocks.AIR.defaultBlockState(), 
                            PalettedContainer.Strategy.SECTION_STATES)
                        );
                        
                        
                        net.minecraft.world.level.block.state.BlockState state = Blocks.STONE.defaultBlockState();
                        
                        
                        double distFromCenter = Math.sqrt(dx*dx + dy*dy + dz*dz);
                        if (distFromCenter < baseRadius * 0.4) {
                            state = Blocks.DEEPSLATE.defaultBlockState();
                        }
                        
                        
                        if (distFromCenter > baseRadius * 0.8 && random.nextDouble() < 0.3) {
                            state = Blocks.GRAVEL.defaultBlockState();
                        }

                        
                        double oreRoll = random.nextDouble();
                        if (oreRoll < 0.05) state = Blocks.COAL_ORE.defaultBlockState();
                        else if (oreRoll < 0.08) state = Blocks.IRON_ORE.defaultBlockState();
                        else if (oreRoll < 0.09) state = Blocks.COPPER_ORE.defaultBlockState();
                        else if (oreRoll < 0.095) state = Blocks.GOLD_ORE.defaultBlockState();
                        else if (oreRoll < 0.10) state = RocketBlocks.TITANIUM_ORE.get().defaultBlockState();
                        
                        container.set((int)vx & 15, (int)vy & 15, (int)vz & 15, state);
                    }
                }
            }
        }

        
        for (var entry : chunkMap.entrySet()) {
            CompoundTag chunkTag = new CompoundTag();
            CompoundTag sectionsTag = new CompoundTag();
            
            for (var sectionEntry : entry.getValue().entrySet()) {
                CompoundTag sectionTag = new CompoundTag();
                sectionTag.put("block_states", BLOCK_STATE_CODEC.encodeStart(net.minecraft.nbt.NbtOps.INSTANCE, sectionEntry.getValue()).getOrThrow());
                sectionsTag.put(String.valueOf(sectionEntry.getKey()), sectionTag);
            }
            
            chunkTag.put("sections", sectionsTag);
            chunksTag.put(String.valueOf(entry.getKey()), chunkTag);
        }

        root.put("chunks", chunksTag);

        try {
            asteroid.getPlot().load(root);
            RocketNautics.LOGGER.info("BLOCKGEN: Success - Asteroid base radius: {}, lumps: {}", baseRadius, numLumps);
        } catch (Exception e) {
            RocketNautics.LOGGER.error("BLOCKGEN: Generation failure", e);
        }
        
        asteroid.getPlot().updateBoundingBox();
        asteroid.updateBoundingBox();
    }

    private static class Sphere {
        double x, y, z, r;
        Sphere(double x, double y, double z, double r) {
            this.x = x; this.y = y; this.z = z; this.r = r;
        }
    }

    private static void saveAsteroidToDisk(UUID uuid, long seed, Vector3d pos) {
        Path path = FMLPaths.CONFIGDIR.get().resolve("rocketnautics").resolve("asteroids_log.json");
        try {
            Files.createDirectories(path.getParent());
            
            JsonObject entry = new JsonObject();
            entry.addProperty("uuid", uuid.toString());
            entry.addProperty("seed", seed);
            entry.addProperty("x", pos.x);
            entry.addProperty("y", pos.y);
            entry.addProperty("z", pos.z);
            entry.addProperty("timestamp", System.currentTimeMillis());

            Files.writeString(path, entry.toString() + "\n", 
                StandardOpenOption.CREATE, 
                StandardOpenOption.APPEND);
                
        } catch (IOException e) {
            RocketNautics.LOGGER.error("Failed to save asteroid log to disk", e);
        }
    }
}
