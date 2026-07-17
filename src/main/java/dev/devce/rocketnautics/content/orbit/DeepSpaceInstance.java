package dev.devce.rocketnautics.content.orbit;

import com.mojang.serialization.Codec;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.physics.SpaceTransitionHandler;
import dev.devce.rocketnautics.network.DebugLogPayload;
import dev.devce.rocketnautics.network.DeepSpacePositionPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.holding.GlobalSavedSubLevelPointer;
import it.unimi.dsi.fastutil.doubles.DoubleObjectPair;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import java.util.stream.Stream;

public final class DeepSpaceInstance {

    private final DeepSpaceData manager;
    private final int chunkSideLength;
    private final ChunkPos minCorner;
    private final AABB boundingBox;
    private Vector3d center;
    private final long id;

    private final DeepSpacePosition position = new DeepSpacePosition();
    private boolean forceClientSync = false;

    private CubePlanet lastOrbiting;

    private final Map<UUID, DoubleObjectPair<Vector3d>> pendingPhysics = new Object2ObjectOpenHashMap<>();

    private static final Codec<Map<UUID, Optional<GlobalSavedSubLevelPointer>>> UNLOADED_SUBLEVEL_CODEC = Codec.unboundedMap(UUIDUtil.CODEC, ExtraCodecs.optionalEmptyMap(GlobalSavedSubLevelPointer.CODEC));
    private final Map<UUID, Optional<GlobalSavedSubLevelPointer>> unloadedSublevels = new Object2ObjectOpenHashMap<>();
    private static final Codec<Map<UUID, Vec3>> OFFLINE_PLAYER_CODEC = Codec.unboundedMap(UUIDUtil.CODEC, Vec3.CODEC);
    private final Map<UUID, Vec3> offlinePlayers = new Object2ObjectOpenHashMap<>();

    private boolean isProcessingRetirement = false;

    private final Deque<Float> velocityChangeLast20Ticks = new ArrayDeque<>();

    public DeepSpaceInstance(DeepSpaceData manager, int chunkSideLength, ChunkPos minCorner, long id) {
        this.manager = manager;
        this.chunkSideLength = chunkSideLength;
        this.minCorner = minCorner;
        this.id = id;
        this.position.setLocalUniverseTicks(manager.getUniverseTicks());
        this.boundingBox = buildBoundingBox();

    }

    private AABB buildBoundingBox() {
        return new AABB(getNegXCorner(), DeepSpaceData.LOGICAL_INSTANCE_HEIGHT, getNegZCorner(), getNegXCorner() + getSideLength() + 1, DeepSpaceData.LOGICAL_INSTANCE_HEIGHT + getSideLength() + 1, getNegZCorner() + getSideLength() + 1);
    }

    public DeepSpaceInstance(DeepSpaceData manager, CompoundTag tag) {
        this.manager = manager;
        this.chunkSideLength = tag.getInt("ChunkLength");
        this.minCorner = new ChunkPos(tag.getLong("MinChunkCorner"));
        this.id = tag.getLong("Id");
        this.position.setLocalUniverseTicks(tag.getLong("LocalTicks"));
        this.position.init(manager.getUniverse(), tag.getString("Frame"), DeepSpaceHelper.read(DeepSpaceHelper.STAMPED_PVCOORDS_CODEC, tag.get("Coords")));
        this.unloadedSublevels.putAll(DeepSpaceHelper.read(UNLOADED_SUBLEVEL_CODEC, tag.get("TrackedSublevels"), Map.of()));
        this.offlinePlayers.putAll(DeepSpaceHelper.read(OFFLINE_PLAYER_CODEC, tag.get("TrackedPlayers"), Map.of()));
        this.boundingBox = buildBoundingBox();
    }

    // cannot be codec-driven due to the need for the DeepSpaceData object during deserialization.
    public CompoundTag write() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("ChunkLength", chunkSideLength);
        tag.putLong("MinChunkCorner", minCorner.toLong());
        tag.putLong("Id", id);
        tag.putLong("LocalTicks", position.getLocalUniverseTicks());
        tag.putString("Frame", position.getOrbit().getFrame().getName());
        Tag c = DeepSpaceHelper.write(DeepSpaceHelper.STAMPED_PVCOORDS_CODEC, position.getOrbit().getPVCoordinates());
        if (c != null) tag.put("Coords", c);
        c = DeepSpaceHelper.write(UNLOADED_SUBLEVEL_CODEC, unloadedSublevels);
        if (c != null) tag.put("TrackedSublevels", c);
        c = DeepSpaceHelper.write(OFFLINE_PLAYER_CODEC, offlinePlayers);
        if (c != null) tag.put("TrackedPlayers", c);
        return tag;
    }

    public boolean isCorrupted() {
        return position.isCorrupted();
    }

    public DeepSpaceData getManager() {
        return manager;
    }

    public long getId() {
        return id;
    }

    public int getChunkSideLength() {
        return chunkSideLength;
    }

    public int getSideLength() {
        return chunkSideLength * 16;
    }

    public int getNegXCorner() {
        return minCorner.getMinBlockX();
    }

    public int getNegZCorner() {
        return minCorner.getMinBlockZ();
    }

    public DeepSpacePosition getPosition() {
        return position;
    }

    public void tick(MinecraftServer server) {
        if (isProcessingRetirement || isCorrupted()) return;
        // handle physics
        if (!pendingPhysics.isEmpty()) {
            TimeStampedPVCoordinates coords = position.getCurrentPVCoords();
            Vector3d momentum = new Vector3d();
            double mass = 0;
            for (DoubleObjectPair<Vector3d> value : pendingPhysics.values()) {
                mass += value.firstDouble();
                value.right().mulAdd(value.firstDouble(), momentum, momentum);
            }
            pendingPhysics.clear();
            if (mass != 0 && momentum.lengthSquared() > 1e-20) {
                Vector3D velocityChange = DeepSpaceHelper.adapt(momentum.div(mass));
                position.init(manager.getUniverse(), position.getFrame(),
                        new TimeStampedPVCoordinates(coords.getDate(), coords.getPosition(), coords.getVelocity().add(velocityChange)));
                manager.setDirty();
                velocityChangeLast20Ticks.addFirst((float) velocityChange.getNorm());
            } else {
                velocityChangeLast20Ticks.addFirst(0f);
            }
            if (velocityChangeLast20Ticks.size() > 20) {
                velocityChangeLast20Ticks.removeLast();
            }
        }
        AbsoluteDate lastTime = position.getLocalUniverseTime();
        Vector3D lastPosition = position.getPosition(lastTime);
        // update position
        position.propagate(manager.getUniverse());
        // handle render data
        if (forceClientSync || manager.realtimeClock(1)) {
            forceClientSync = false;
            ServerLevel deepSpace = server.getLevel(RocketDimensions.DEEP_SPACE);
            List<ServerPlayer> players = deepSpace.getPlayers(p -> boundingBox().contains(p.position()));
            for (ServerPlayer player : players) {
                double velChange = velocityChangeLast20Ticks.stream().mapToDouble(f -> f).sum();
                PacketDistributor.sendToPlayer(player, DeepSpacePositionPayload.of(position, manager.getUniverse()), new DebugLogPayload("Recent acceleration: " + velChange, 0xFFFFFF));
            }
        }
        // check for planetary intersection
        if (lastOrbiting == null || lastOrbiting.orekitFrame() != getPosition().getFrame()) {
            OptionalInt id = getManager().getUniverse().getIDByFrameName(getPosition().getFrame().getName());
            if (id.isPresent()) {
                lastOrbiting = getManager().getUniverse().getPlanetById(id.getAsInt());
            } else {
                lastOrbiting = null;
            }
        }
        if (lastOrbiting != null && lastOrbiting.linkedDimension() != null && lastOrbiting.linkedDimension().allowedTransfer().allowToDimension()) {
            // rotate the frame to view the planet aligned with the cardinal axes
            Vector3D p = lastOrbiting.getRotationAtTime(position.getLocalUniverseTime())
                    .applyInverseTo(getPosition().getCurrentPosition());
            double d2;
            if (dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isSphereShape()) {
                double dist = p.getNorm() - lastOrbiting.radius();
                d2 = dist > 0 ? dist * dist : 0;
            } else {
                double ax = Math.abs(p.getX());
                double ay = Math.abs(p.getY());
                double az = Math.abs(p.getZ());
                double dx = Math.max(0, ax - lastOrbiting.radius());
                double dy = Math.max(0, ay - lastOrbiting.radius());
                double dz = Math.max(0, az - lastOrbiting.radius());
                d2 = dx * dx + dy * dy + dz * dz;
            }
            if (d2 < lastOrbiting.linkedDimension().transitionHeight() * lastOrbiting.linkedDimension().transitionHeight()) {
                SpaceTransitionHandler.exitDeepSpace(server, lastOrbiting, new TimeStampedPVCoordinates(lastTime, lastPosition, Vector3D.ZERO),
                        this, () -> manager.retireInstance(this.getId()));
                isProcessingRetirement = true;
            }
        }
        if (!isProcessingRetirement && manager.realtimeClock(10, 10)) {
            // check for whether we are completely empty and can retire
            if (unloadedSublevels.isEmpty() && offlinePlayers.isEmpty()) {
                AtomicBoolean flag = new AtomicBoolean(true);
                collectLoadedSublevels(server, l -> flag.set(false));
                collectOnlinePlayers(server, p -> flag.set(false));
                if (flag.get()) {
                    isProcessingRetirement = true;
                    manager.retireInstance(this.getId());
                }
            }
        }
    }

    public Stream<ChunkPos> interiorPositions() {
        UnaryOperator<ChunkPos> func =pos -> {
            int x = pos.x + 1;
            int z = pos.z;
            if (x - minCorner.x >= chunkSideLength) {
                x = minCorner.x;
                z += 1;
                if (z - minCorner.z >= chunkSideLength) {
                    return null;
                }
            }
            return new ChunkPos(x, z);
        };
        return Stream.iterate(minCorner, Objects::nonNull, func);
    }

    public void applyVelocity(UUID id, Vector3dc velocity, double mass) {
        pendingPhysics.compute(id, (k, v) -> {
            if (v == null) {
                return DoubleObjectPair.of(mass, new Vector3d(velocity));
            }
            v.right().add(velocity);
            return v;
        });
    }

    public AABB boundingBox() {
        return boundingBox;
    }

    public Vector3dc getCenter() {
        if (center == null) {
            Vec3 v = boundingBox().getCenter();
            center = new Vector3d(v.x(), v.y(), v.z());
        }
        return center;
    }

    public void forceClientSync() {
        this.forceClientSync = true;
    }

    public void trackUnloadingSublevel(ServerSubLevel subLevel) {
        handlePointerInformation(subLevel.getUniqueId(), subLevel.getLastSerializationPointer());
        //noinspection ConstantValue
        if ((Object) SubLevelContainer.getContainer(subLevel.getLevel()).getHoldingChunkMap()
                .getHoldingSubLevel(subLevel.getUniqueId()) instanceof PointerListenable listenable) {
            listenable.rocketnautics$addListener(this::handlePointerInformation);
        }
    }

    private void handlePointerInformation(UUID uuid, @Nullable GlobalSavedSubLevelPointer pointer) {
        unloadedSublevels.put(uuid, Optional.ofNullable(pointer));
    }

    public void stopTrackingSublevel(SubLevel subLevel) {
        unloadedSublevels.remove(subLevel.getUniqueId());
    }

    public Map<UUID, Optional<GlobalSavedSubLevelPointer>> getUnloadedSublevels() {
        return unloadedSublevels;
    }

    public void trackOfflinePlayer(Player player) {
        offlinePlayers.put(player.getUUID(), player.position());
    }

    public void stopTrackingOfflinePlayer(Player player) {
        offlinePlayers.remove(player.getUUID());
    }

    public Map<UUID, Vec3> getOfflinePlayers() {
        return offlinePlayers;
    }

    public void collectLoadedSublevels(MinecraftServer server, Consumer<SubLevel> consumer) {
        SubLevelContainer.getContainer(server.getLevel(RocketDimensions.DEEP_SPACE)).getAllSubLevels()
                .stream().filter(l -> boundingBox().contains(l.logicalPose().position().x(), l.logicalPose().position().y(), l.logicalPose().position().z()))
                .forEach(consumer);
    }

    public void collectOnlinePlayers(MinecraftServer server, Consumer<ServerPlayer> consumer) {
        server.getLevel(RocketDimensions.DEEP_SPACE).players()
                .stream().filter(p -> boundingBox().contains(p.position())).forEach(consumer);
    }
}
