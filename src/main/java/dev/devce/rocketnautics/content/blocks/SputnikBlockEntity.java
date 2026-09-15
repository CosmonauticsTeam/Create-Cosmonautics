package dev.devce.rocketnautics.content.blocks;

import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.sputnik.model.SputnikGraph;
import dev.devce.rocketnautics.content.sputnik.storage.SputnikStorageManager;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import dev.devce.rocketnautics.content.energy.CustomEnergyStorage;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;
import net.minecraft.core.Direction;

import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class SputnikBlockEntity extends BlockEntity implements IHaveGoggleInformation {
    public static final int ENERGY_CONSUMPTION_RATE = 0;
    public static final int ENERGY_CAPACITY = 5000;

    private int sputnikId = 0;
    private SputnikGraph graph;
    private final Map<String, String> displayBridge = new ConcurrentHashMap<>();
    private final Map<String, Double> lastWirelessRedstone = new ConcurrentHashMap<>();
    private final Map<String, Double> lastRadioPackets = new ConcurrentHashMap<>();
    private final CustomEnergyStorage energyStorage = new CustomEnergyStorage(ENERGY_CAPACITY, 500);
    private int syncCooldown = 0;
    private int lastSyncedEnergy = 0;

    public SputnikBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public int getSputnikId() {
        return sputnikId;
    }

    public void setSputnikId(int sputnikId) {
        this.sputnikId = sputnikId;
    }

    public SputnikGraph getGraph() {
        if (graph == null) {
            if (level != null && !level.isClientSide() && level.getServer() != null && sputnikId > 0) {
                graph = SputnikStorageManager.loadGraph(level.getServer(), sputnikId);
            }
        }
        return graph != null ? graph : SputnikGraph.createDefault();
    }

    public void setGraph(SputnikGraph graph) {
        this.graph = graph;
    }

    public IEnergyStorage getEnergyStorage(@Nullable Direction side) {
        return energyStorage;
    }

    public boolean isPowered() {
        return true;
    }

    public Map<String, String> getDisplayBridge() {
        return displayBridge;
    }

    public Map<String, Double> getLastWirelessRedstone() {
        return lastWirelessRedstone;
    }

    public Map<String, Double> getLastRadioPackets() {
        return lastRadioPackets;
    }

    public void sendRadioPacket(String channel, double data) {
        if (channel != null && !channel.isEmpty()) {
            lastRadioPackets.put(channel, data);
        }
    }

    public double getRadioPacket(String channel) {
        return lastRadioPackets.getOrDefault(channel, 0.0);
    }

    public void setGlobalThrottle(double val) {
    }

    public void applyTorque(double pitch, double yaw, double roll) {
    }

    public void setDrainValveOpen(boolean open) {
    }

    public double getTotalFuelAmount() {
        return 0.0;
    }

    public double getTotalFuelCapacity() {
        return 0.0;
    }

    public int getEnergyStored() {
        return energyStorage.getEnergyStored();
    }

    private SubLevel getSubLevel() {
        if (level == null) return null;
        Object lvlObj = level;
        if (lvlObj instanceof SubLevel sl) return sl;
        if (level.isClientSide()) {
            var csl = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(this);
            if (csl != null) return csl;
        }
        Object obj = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
        if (obj instanceof SubLevel sl) return sl;
        return null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SputnikBlockEntity blockEntity) {
        if (!level.isClientSide) {
            if (blockEntity.sputnikId <= 0 && level.getServer() != null) {
                blockEntity.sputnikId = SputnikStorageManager.allocateId(level.getServer());
                blockEntity.setChanged();
            }

            blockEntity.tickNodes();
            blockEntity.syncCooldown++;
            boolean energyChanged = Math.abs(blockEntity.energyStorage.getEnergyStored() - blockEntity.lastSyncedEnergy) >= 50;
            if (blockEntity.syncCooldown >= 10 || energyChanged) {
                blockEntity.syncCooldown = 0;
                blockEntity.lastSyncedEnergy = blockEntity.energyStorage.getEnergyStored();
                level.sendBlockUpdated(pos, state, state, 2);
            }
        }
    }

    private void tickNodes() {
        getGraph().evaluate(this);
    }

    public double getX() { return getGlobalPos().x; }
    public double getY() { return getGlobalPos().y; }
    public double getZ() { return getGlobalPos().z; }

    public double getAltitude() {
        return getGlobalPos().y;
    }

    public double getVelocity() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            var pose = subLevel.logicalPose();
            var lastPose = subLevel.lastPose();
            return new Vector3d(pose.position()).distance(lastPose.position()) * 20.0;
        }
        return 0;
    }

    public Vector3d getVelocityVector() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            var pose = subLevel.logicalPose();
            var lastPose = subLevel.lastPose();
            return new Vector3d(pose.position()).sub(lastPose.position()).mul(20.0);
        }
        return new Vector3d(0, 0, 0);
    }

    public Vector3d getAngularVelocity() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            Vector3d currentEuler = subLevel.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
            Vector3d lastEuler = subLevel.lastPose().orientation().getEulerAnglesYXZ(new Vector3d());

            double dx = Math.toDegrees(currentEuler.x - lastEuler.x);
            double dy = Math.toDegrees(currentEuler.y - lastEuler.y);
            double dz = Math.toDegrees(currentEuler.z - lastEuler.z);

            dx = normalizeAngleDifference(dx);
            dy = normalizeAngleDifference(dy);
            dz = normalizeAngleDifference(dz);

            return new Vector3d(dx * 20.0, dy * 20.0, dz * 20.0);
        }
        return new Vector3d(0, 0, 0);
    }

    private double normalizeAngleDifference(double angle) {
        while (angle < -180.0) angle += 360.0;
        while (angle > 180.0) angle -= 360.0;
        return angle;
    }

    public Quaterniond getOrientation() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            if (subLevel instanceof dev.ryanhcode.sable.sublevel.ClientSubLevel csl) {
                return new Quaterniond(csl.renderPose().orientation());
            }
            return new Quaterniond(subLevel.logicalPose().orientation());
        }
        return new Quaterniond();
    }

    public Vector3d getForwardVector() {
        Quaterniond rot = getOrientation();
        Vector3d fwd = new Vector3d(0, 0, 1);
        rot.transform(fwd);
        return fwd;
    }

    public double getAttitudePitch() {
        Vector3d fwd = getForwardVector();
        return Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, fwd.y))));
    }

    public double getAttitudeYaw() {
        Vector3d fwd = getForwardVector();
        return (Math.toDegrees(Math.atan2(fwd.x, fwd.z)) + 360.0) % 360.0;
    }

    public double getAttitudeRoll() {
        Quaterniond rot = getOrientation();
        Vector3d camUp = new Vector3d(0, 1, 0);
        rot.transformInverse(camUp);
        return Math.toDegrees(Math.atan2(camUp.x, camUp.y));
    }

    public double getPitch() {
        return getAttitudePitch();
    }

    public double getYaw() {
        return getAttitudeYaw();
    }

    public double getRoll() {
        return getAttitudeRoll();
    }

    public double getShipMass() {
        SubLevel subLevel = getSubLevel();
        if (subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl) {
            var tracker = ssl.getMassTracker();
            if (tracker != null) {
                return tracker.getMass();
            }
        }
        return 0.0;
    }

    public Vector3d getInertiaTensorDiagonal() {
        SubLevel subLevel = getSubLevel();
        if (subLevel instanceof dev.ryanhcode.sable.sublevel.ServerSubLevel ssl) {
            var tracker = ssl.getMassTracker();
            if (tracker != null) {
                var matrix = tracker.getInertiaTensor();
                if (matrix != null) {
                    return new Vector3d(matrix.m00(), matrix.m11(), matrix.m22());
                }
            }
        }
        return new Vector3d(0, 0, 0);
    }

    public int getBiomeColor() {
        if (level == null) return 0;
        Biome biome = level.getBiome(worldPosition).value();
        return biome.getFoliageColor();
    }

    public String getBiomeName() {
        if (level == null) return "Unknown";
        return level.getBiome(worldPosition).getRegisteredName();
    }

    public Vector3d getGlobalPos() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            return subLevel.logicalPose().position();
        }
        return new Vector3d(worldPosition.getX() + 0.5, worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5);
    }

    public Biome getGlobalBiome() {
        if (level == null) return null;
        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);

        if (subLevel != null && !level.isClientSide) {
            Vector3d global = getGlobalPos();
            if (this.level.getServer() != null) {
                for (var sl : this.level.getServer().getAllLevels()) {
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
                    if (container != null) {
                        for (var slItem : container.getAllSubLevels()) {
                            if (slItem.getUniqueId().equals(subLevel.getUniqueId())) {
                                return sl.getBiome(BlockPos.containing(global.x, 64, global.z)).value();
                            }
                        }
                    }
                }
            }
        }

        return level.getBiome(worldPosition).value();
    }

    public int getGlobalBiomeColor() {
        Biome b = getGlobalBiome();
        return b != null ? b.getFoliageColor() : 0;
    }

    public String getGlobalBiomeName() {
        Biome b = getGlobalBiome();
        return b != null ? level.getBiome(worldPosition).getRegisteredName() : getBiomeName();
    }

    public int getLightLevel() {
        if (level == null) return 0;
        return level.getMaxLocalRawBrightness(worldPosition);
    }

    public float getTemperature() {
        if (level == null) return 0f;
        return level.getBiome(worldPosition).value().getBaseTemperature();
    }

    public String getDimensionId() {
        if (level == null) return "unknown";
        return level.dimension().location().toString();
    }

    public long getWorldTime() {
        if (level == null) return 0L;
        return level.getDayTime();
    }

    public double getSpeed() {
        return getVelocityVector().length();
    }

    public boolean isInDeepSpace() {
        if (level == null) return false;
        if (level.isClientSide()) {
            return dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(level);
        }
        if (dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(level)) {
            return true;
        }
        SubLevel subLevel = getSubLevel();
        if (subLevel != null && level.getServer() != null) {
            for (var sl : level.getServer().getAllLevels()) {
                if (dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(sl)) {
                    var container = dev.ryanhcode.sable.api.sublevel.SubLevelContainer.getContainer(sl);
                    if (container != null) {
                        for (var slItem : container.getAllSubLevels()) {
                            if (slItem.getUniqueId().equals(subLevel.getUniqueId())) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    public DeepSpaceInstance getDeepSpaceInstance() {
        if (!isInDeepSpace()) return null;
        if (level == null || level.isClientSide() || level.getServer() == null) return null;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        Vector3d pos = getGlobalPos();
        return data.getInstanceForPos((int) pos.x, (int) pos.z);
    }

    public double getOrbitalSemiMajorAxis() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return SputnikClientHelper.getReceivedPosition.get().getCurrentOrbit().getA();
            }
            return Double.NaN;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return Double.NaN;
        return inst.getPosition().getCurrentOrbit().getA();
    }

    public double getOrbitalEccentricity() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return SputnikClientHelper.getReceivedPosition.get().getCurrentOrbit().getE();
            }
            return Double.NaN;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return Double.NaN;
        return inst.getPosition().getCurrentOrbit().getE();
    }

    public double getOrbitalInclination() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return Math.toDegrees(SputnikClientHelper.getReceivedPosition.get().getCurrentOrbit().getI());
            }
            return Double.NaN;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return Double.NaN;
        return Math.toDegrees(inst.getPosition().getCurrentOrbit().getI());
    }

    public double getOrbitalPeriod() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                try {
                    return SputnikClientHelper.getReceivedPosition.get().getCurrentOrbit().getKeplerianPeriod();
                } catch (Exception e) {
                    return Double.NaN;
                }
            }
            return Double.NaN;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return Double.NaN;
        try {
            return inst.getPosition().getCurrentOrbit().getKeplerianPeriod();
        } catch (Exception e) {
            return Double.NaN;
        }
    }

    public double getOrbitalSpeed() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return SputnikClientHelper.getReceivedPosition.get().getCurrentPVCoords().getVelocity().getNorm();
            }
            return 0;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return 0;
        return inst.getPosition().getCurrentPVCoords().getVelocity().getNorm();
    }

    public double getGravityAcceleration() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                var pos = SputnikClientHelper.getReceivedPosition.get();
                double mu = pos.getCurrentOrbit().getMu();
                double r = pos.getCurrentPosition().getNorm();
                if (r < 1) return 0;
                return mu / (r * r);
            }
            return 0;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return 0;
        double mu = inst.getPosition().getCurrentOrbit().getMu();
        double r = inst.getPosition().getCurrentPosition().getNorm();
        if (r < 1) return 0;
        return mu / (r * r);
    }

    public String getParentBodyName() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return SputnikClientHelper.getReceivedPosition.get().getFrame().getName();
            }
            return getDimensionId();
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return getDimensionId();
        return inst.getPosition().getFrame().getName();
    }

    public double getParentBodyRadius() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get() && SputnikClientHelper.getUniverse.get() != null) {
                String frameName = SputnikClientHelper.getReceivedPosition.get().getFrame().getName();
                return SputnikClientHelper.getUniverse.get().getPlanets().stream()
                        .filter(p -> p.orekitFrame().getName().equals(frameName))
                        .mapToDouble(CubePlanet::radius)
                        .findFirst().orElse(0);
            }
            return 0;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted() || level == null || level.getServer() == null) return 0;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        String frameName = inst.getPosition().getFrame().getName();
        return data.getUniverse().getPlanets().stream()
                .filter(p -> p.orekitFrame().getName().equals(frameName))
                .mapToDouble(CubePlanet::radius)
                .findFirst().orElse(0);
    }

    public double getDistanceToPlanet() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                double r = SputnikClientHelper.getReceivedPosition.get().getCurrentPosition().getNorm();
                double radius = getParentBodyRadius();
                return Math.max(0, r - radius);
            }
            return Double.NaN;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted()) return Double.NaN;
        double r = inst.getPosition().getCurrentPosition().getNorm();
        double radius = getParentBodyRadius();
        return Math.max(0, r - radius);
    }

    public boolean isInAtmosphere() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get() && SputnikClientHelper.getUniverse.get() != null) {
                String frameName = SputnikClientHelper.getReceivedPosition.get().getFrame().getName();
                CubePlanet orbiting = null;
                for (CubePlanet p : SputnikClientHelper.getUniverse.get().getPlanets()) {
                    if (p.orekitFrame().getName().equals(frameName)) { orbiting = p; break; }
                }
                if (orbiting == null || orbiting.linkedDimension() == null) return false;
                double dist = getDistanceToPlanet();
                return dist <= orbiting.linkedDimension().transitionHeight();
            }
            return false;
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted() || level == null || level.getServer() == null) return false;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        CubePlanet orbiting = null;
        String frameName = inst.getPosition().getFrame().getName();
        for (CubePlanet p : data.getUniverse().getPlanets()) {
            if (p.orekitFrame().getName().equals(frameName)) { orbiting = p; break; }
        }
        if (orbiting == null || orbiting.linkedDimension() == null) return false;
        double dist = getDistanceToPlanet();
        return dist <= orbiting.linkedDimension().transitionHeight();
    }

    public boolean hasAtmosphere() {
        return !isInDeepSpace() || isInAtmosphere();
    }

    public boolean isAtmosphereBreathable() {
        if (!isInDeepSpace()) {
            return level != null && level.dimension() == Level.OVERWORLD;
        }
        return getAtmosphereFlags().contains("breathable");
    }

    public String getAtmosphereFlags() {
        if (level != null && level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get() && SputnikClientHelper.getUniverse.get() != null) {
                String frameName = SputnikClientHelper.getReceivedPosition.get().getFrame().getName();
                CubePlanet orbiting = null;
                for (CubePlanet p : SputnikClientHelper.getUniverse.get().getPlanets()) {
                    if (p.orekitFrame().getName().equals(frameName)) { orbiting = p; break; }
                }
                if (orbiting == null || orbiting.linkedDimension() == null) return "";
                double dist = getDistanceToPlanet();
                var atmosphere = orbiting.linkedDimension().atmosphere();
                EnumSet<AtmosphereFlags> flags = null;
                for (var entry : atmosphere.int2ObjectEntrySet()) {
                    if (dist <= entry.getIntKey()) { flags = entry.getValue(); break; }
                }
                if (flags == null || flags.isEmpty()) return "";
                StringJoiner sj = new StringJoiner(",");
                for (AtmosphereFlags f : flags) sj.add(f.getSerializedName());
                return sj.toString();
            }
            return "";
        }
        DeepSpaceInstance inst = getDeepSpaceInstance();
        if (inst == null || inst.isCorrupted() || level == null || level.getServer() == null) return "";
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        CubePlanet orbiting = null;
        String frameName = inst.getPosition().getFrame().getName();
        for (CubePlanet p : data.getUniverse().getPlanets()) {
            if (p.orekitFrame().getName().equals(frameName)) { orbiting = p; break; }
        }
        if (orbiting == null || orbiting.linkedDimension() == null) return "";
        double dist = getDistanceToPlanet();
        var atmosphere = orbiting.linkedDimension().atmosphere();
        EnumSet<AtmosphereFlags> flags = null;
        for (var entry : atmosphere.int2ObjectEntrySet()) {
            if (dist <= entry.getIntKey()) { flags = entry.getValue(); break; }
        }
        if (flags == null || flags.isEmpty()) return "";
        StringJoiner sj = new StringJoiner(",");
        for (AtmosphereFlags f : flags) sj.add(f.getSerializedName());
        return sj.toString();
    }

    public long getUniverseTime() {
        if (level == null) return 0L;
        if (level.isClientSide()) {
            if (isInDeepSpace() && SputnikClientHelper.hasReceivedPosition.get()) {
                return (long) (SputnikClientHelper.getReceivedPosition.get().getLocalUniverseTime().durationFrom(org.orekit.time.AbsoluteDate.ARBITRARY_EPOCH) / 0.05);
            }
            return 0L;
        }
        if (level.getServer() == null) return 0L;
        return DeepSpaceData.getInstance(level.getServer()).getUniverseTicks();
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener> getUpdatePacket() {
        return net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable("block.rocketnautics.sputnik").withStyle(ChatFormatting.GOLD)));
        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.status")).append(": ")
                .append(Component.translatable("rocketnautics.goggles.active").withStyle(ChatFormatting.GREEN)));
        return true;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("SputnikId", sputnikId);
        tag.putInt("Energy", energyStorage.getEnergyStored());

        CompoundTag redstoneTag = new CompoundTag();
        for (var entry : lastWirelessRedstone.entrySet()) {
            redstoneTag.putDouble(entry.getKey(), entry.getValue());
        }
        tag.put("WirelessRedstoneCache", redstoneTag);

        CompoundTag radioTag = new CompoundTag();
        for (var entry : lastRadioPackets.entrySet()) {
            radioTag.putDouble(entry.getKey(), entry.getValue());
        }
        tag.put("RadioPacketsCache", radioTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("SputnikId")) {
            this.sputnikId = tag.getInt("SputnikId");
            this.graph = null;
        }
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }

        lastWirelessRedstone.clear();
        if (tag.contains("WirelessRedstoneCache")) {
            CompoundTag redstoneTag = tag.getCompound("WirelessRedstoneCache");
            for (String key : redstoneTag.getAllKeys()) {
                lastWirelessRedstone.put(key, redstoneTag.getDouble(key));
            }
        }

        lastRadioPackets.clear();
        if (tag.contains("RadioPacketsCache")) {
            CompoundTag radioTag = tag.getCompound("RadioPacketsCache");
            for (String key : radioTag.getAllKeys()) {
                lastRadioPackets.put(key, radioTag.getDouble(key));
            }
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        if (level != null && !level.isClientSide()) {
            dev.devce.rocketnautics.content.blocks.sputnik_link.SputnikLinkManager.onSputnikRemoved(this.sputnikId);
            dev.devce.rocketnautics.content.sputnik.comms.SputnikCommsManager.onSputnikRemoved(this.sputnikId);
        }
    }
}
