package dev.devce.rocketnautics.content.orbit;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.UnboundedMapCodec;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseLoader;
import dev.devce.rocketnautics.network.UniverseDefinitionPayload;
import dev.devce.rocketnautics.network.UniverseTimeSyncPayload;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelObserver;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.sublevel.storage.SubLevelRemovalReason;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.Map;
import java.util.UUID;

@EventBusSubscriber(modid = RocketNautics.MODID)
public class DeepSpaceData extends SavedData implements SubLevelObserver {
    public static final int LOGICAL_INSTANCE_HEIGHT = 1000;

    public static final String ID = "cosmonautics_deep_space_data";

    protected boolean observing = false;

    protected static final UnboundedMapCodec<UUID, Pair<ResourceKey<Level>, Vec3>> TELEPORTS_CODEC = Codec.unboundedMap(UUIDUtil.CODEC, Codec.pair(Level.RESOURCE_KEY_CODEC, Vec3.CODEC));
    protected final Map<UUID, Pair<ResourceKey<Level>, Vec3>> offlinePlayerTeleports = new Object2ObjectOpenHashMap<>();

    protected final Map<UUID, DeepSpaceInstance> knownSublevelAssociations = new Object2ObjectOpenHashMap<>();

    public static boolean tooSoon(MinecraftServer server) {
        return server.getLevel(RocketDimensions.DEEP_SPACE) == null;
    }

    public static DeepSpaceData getInstance(MinecraftServer server) {
        ServerLevel deepSpace = server.getLevel(RocketDimensions.DEEP_SPACE);
        DeepSpaceData data = deepSpace.getChunkSource().getDataStorage().computeIfAbsent(new Factory<>(DeepSpaceData::new, DeepSpaceData::load, null), ID);
        if (!data.observing) {
            SubLevelContainer.getContainer(deepSpace).addObserver(data);
            data.observing = true;
        }
        return data;
    }

    @SubscribeEvent
    public static void advanceUniverse(ServerTickEvent.Post event) {
        getInstance(event.getServer()).tick(event.getServer());
        dev.devce.rocketnautics.server.telemetry.TelemetryServer.INSTANCE.tick(event.getServer());
    }

    @SubscribeEvent
    public static void handlePlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer splayer)) return;
        PacketDistributor.sendToPlayer(splayer, new UniverseDefinitionPayload(getInstance(splayer.server).getUniverse()));
        DeepSpaceData data = getInstance(event.getEntity().getServer());
        if (data.offlinePlayerTeleports.containsKey(event.getEntity().getUUID())) {
            var pair = data.offlinePlayerTeleports.get(event.getEntity().getUUID());
            ServerLevel dest = event.getEntity().getServer().getLevel(pair.getFirst());
            if (dest != null) {
                ((ServerPlayer) event.getEntity()).teleportTo(dest, pair.getSecond().x(), pair.getSecond().y(), pair.getSecond().z(), event.getEntity().getYRot(), event.getEntity().getXRot());
            }
        } else {
            DeepSpaceInstance instance = data.getInstanceForPos(event.getEntity().getBlockX(), event.getEntity().getBlockZ());
            if (instance != null) {
                instance.stopTrackingOfflinePlayer(event.getEntity());
            }
        }
    }

    @SubscribeEvent
    public static void handlePlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        DeepSpaceInstance instance = getInstance(event.getEntity().getServer()).getInstanceForPos(event.getEntity().getBlockX(), event.getEntity().getBlockZ());
        if (instance != null) {
            instance.trackOfflinePlayer(event.getEntity());
        }
    }

    // end static //

    private final UniverseDefinition universe = UniverseLoader.INSTANCE.getLoaded();

    private final Int2ObjectMap<InstanceList> instances = new Int2ObjectOpenHashMap<>();

    private long universeTicks;
    private int nextFreeID = 0;

    protected float lastTickRate = 20;

    public void tick(MinecraftServer server) {
        universeTicks += 1;
        instances.values().forEach(i -> i.tick(server));
        setDirty();
        if (server.tickRateManager().tickrate() != lastTickRate || realtimeClock(1)) {
            lastTickRate = server.tickRateManager().tickrate();
            PacketDistributor.sendToAllPlayers(new UniverseTimeSyncPayload(universeTicks, lastTickRate));
        }
    }

    public boolean realtimeClock(int secondsPer) {
        return realtimeClock(secondsPer, 0);
    }

    public boolean realtimeClock(int secondsPer, int tickOffset) {
        return universeTicks % (int) (lastTickRate * secondsPer) == tickOffset;
    }

    private void debugInstance() {
        // execute in rocketnautics:deep_space run tp Dev 48 1016 16
        DeepSpaceInstance instance = claimNewInstance(2);
        instance.getPosition().init(universe, "overworld", new TimeStampedPVCoordinates(DeepSpaceHelper.EPOCH, new Vector3D(0, 0, 9_000_000D), new Vector3D(0, 3_300, 0)));
    }

    public void trackOfflinePlayerTeleport(UUID id, ResourceKey<Level> destinationLevel, Vec3 destinationPos) {
        offlinePlayerTeleports.put(id, Pair.of(destinationLevel, destinationPos));
    }

    public DeepSpaceInstance claimNewInstance(int chunkSize) {
        setDirty();
        int powerSize = (int) Math.ceil(Math.log(chunkSize * 4) / Math.log(2)) - 1;
        return instances.computeIfAbsent(powerSize, InstanceList::new).createInstance(this);
    }

    @Nullable
    public DeepSpaceInstance getInstanceForPos(int xPos, int zPos) {
        int[] params = getChunkPowerSizeIdWithinSizeForParameters(xPos, zPos);
        return getInstance(params[0], params[1]);
    }

    @Nullable
    public DeepSpaceInstance getInstance(long id) {
        return getInstance(unpackSize(id), unpackIdWithinSize(id));
    }

    @Nullable
    public DeepSpaceInstance getInstance(int chunkPowerSize, int idWithinSize) {
        InstanceList l = instances.get(chunkPowerSize);
        if (l == null) return null;
        return l.getInstance(idWithinSize);
    }

    @Nullable
    public DeepSpaceInstance retireInstance(long id) {
        return retireInstance(unpackSize(id), unpackIdWithinSize(id));
    }

    @Nullable
    public DeepSpaceInstance retireInstance(int chunkPowerSize, int idWithinSize) {
        InstanceList l = instances.get(chunkPowerSize);
        if (l == null) return null;
        setDirty();
        return l.retireInstance(idWithinSize);
    }

    public static long pack(int chunkPowSize, int idWithinSize) {
        return ((long) chunkPowSize << 32) + (idWithinSize & 0xFFFFFFFFL);
    }

    public static int unpackSize(long id) {
        return (int) (id >> 32);
    }

    public static int unpackIdWithinSize(long id) {
        return (int) (id & 0xFFFFFFFFL);
    }

    public UniverseDefinition getUniverse() {
        return UniverseLoader.INSTANCE.getLoaded();
    }

    public long getUniverseTicks() {
        return universeTicks;
    }

    public AbsoluteDate getUniverseTime() {
        return DeepSpaceHelper.getDateByTicks(universeTicks);
    }

    public java.util.List<DeepSpaceInstance> getAllInstances() {
        java.util.List<DeepSpaceInstance> all = new java.util.ArrayList<>();
        for (InstanceList list : instances.values()) {
            all.addAll(list.getAllInstances());
        }
        return all;
    }

    @Override
    public void onSubLevelAdded(SubLevel subLevel) {
        DeepSpaceInstance instance = getInstanceForPos((int) subLevel.logicalPose().position().x(), (int) subLevel.logicalPose().position().z());
        if (instance != null) instance.stopTrackingSublevel(subLevel);
    }

    @Override
    public void onSubLevelRemoved(SubLevel subLevel, SubLevelRemovalReason reason) {
        if (!(subLevel instanceof ServerSubLevel)) return;
        DeepSpaceInstance instance = getInstanceForPos((int) subLevel.logicalPose().position().x(), (int) subLevel.logicalPose().position().z());
        if (instance == null) return;
        switch (reason) {
            case UNLOADED -> instance.trackUnloadingSublevel((ServerSubLevel) subLevel);
            case REMOVED -> instance.stopTrackingSublevel(subLevel);
        }
    }

    @Override
    public @NotNull CompoundTag save(CompoundTag compoundTag, HolderLookup.Provider provider) {
        compoundTag.putLong("UniverseTicks", universeTicks);
        compoundTag.putInt("NextID", nextFreeID);
        ListTag list = new ListTag();
        for (InstanceList instanceList : instances.values()) {
            list.add(instanceList.write());
        }
        compoundTag.put("InstanceLists", list);
        Tag teleports = DeepSpaceHelper.write(TELEPORTS_CODEC, offlinePlayerTeleports);
        if (teleports != null) {
            compoundTag.put("PendingTeleports", teleports);
        }
        return compoundTag;
    }

    private static DeepSpaceData load(CompoundTag tag, HolderLookup.Provider registries) {
        DeepSpaceData data = new DeepSpaceData();
        data.universeTicks = tag.getLong("UniverseTicks");
        data.nextFreeID = tag.getInt("NextID");
        ListTag list = tag.getList("InstanceLists", ListTag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            InstanceList instance = new InstanceList(data, list.getCompound(i));
            data.instances.put(instance.getChunkPowerSize(), instance);
        }
        data.offlinePlayerTeleports.putAll(DeepSpaceHelper.read(TELEPORTS_CODEC, tag.get("PendingTeleports"), Map.of()));
        return data;
    }

    // collision box at the same place the world border's collision box is added.
    public static VoxelShape getColliderForPosition(Vec3 position) {
        // subtract the instance bounds from the infinity box
        return Shapes.join(
                Shapes.INFINITY,
                getBoxForPosition(position),
                BooleanOp.ONLY_FIRST
        );
    }

    public static VoxelShape getBoxForPosition(Vec3 position) {
        // compute the instance we are in
        int[] sizeAndId = getChunkPowerSizeIdWithinSizeForParameters((int) position.x, (int) position.z);
        ChunkPos corner = getMinCornerForParameters(sizeAndId[0], sizeAndId[1]);
        int blockSize = 16 * (2 << sizeAndId[0]);
        return Shapes.box(
                corner.getMinBlockX(),
                LOGICAL_INSTANCE_HEIGHT,
                corner.getMinBlockZ(),
                corner.getMinBlockX() + blockSize + 1,
                LOGICAL_INSTANCE_HEIGHT + blockSize + 1,
                corner.getMinBlockZ() + blockSize + 1
        );
    }

    public static int[] getChunkPowerSizeIdWithinSizeForParameters(int negX, int negZ) {
        if (negX < 0 || negZ < 0) return new int[] { 1, 0 };
        // convert to chunkpos
        negX /= 16;
        negZ /= 16;
        // derive chunk size from X position
        // since the power term dominates at large scale, get a definite upper bound
        int chunkPowerSize = Math.max((int) (Math.log(negX * 1.1) / Math.log(2)) + 1, 1);
        // descend until we are below or equal to the target; at large scales, we will need to do this once.
        int size = chunkPowerSize * 16 + (2 << chunkPowerSize);
        while (size > negX && chunkPowerSize > 0) {
            chunkPowerSize--;
            size = chunkPowerSize * 16 + (2 << chunkPowerSize);
        }
        // derive id from Z position and chunk size
        return new int[] { chunkPowerSize, negZ / (16 + size - chunkPowerSize * 16) };
    }

    public static ChunkPos getMinCornerForParameters(int chunkPowerSize, int idWithinSize) {
        int chunkSize = 2 << chunkPowerSize;
        return new ChunkPos((chunkPowerSize * 16 + chunkSize), (idWithinSize * (16 + chunkSize)));
    }
}
