package dev.devce.rocketnautics.content.blocks;

import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.PointGravitySource;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3d;
import dev.devce.websnodelib.api.WGraph;

import java.util.EnumSet;
import java.util.StringJoiner;

/**
 * Sputnik Block Entity acts as the core central computer of a spacecraft.
 * It ticks the Lua script graph and provides vector telemetry (velocity, rotation, position).
 */
public class SputnikBlockEntity extends BlockEntity {
    public final WGraph graph = new WGraph();
    private final java.util.Map<String, String> displayBridge = new java.util.concurrent.ConcurrentHashMap<>();

    public java.util.Map<String, String> getDisplayBridge() {
        return displayBridge;
    }

    public SputnikBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        graph.setContext(this);
        if (level != null) graph.setRegistries(level.registryAccess());
    }

    @Override
    public void setLevel(Level level) {
        super.setLevel(level);
        if (level != null) {
            graph.setRegistries(level.registryAccess());
            graph.setContext(this);
        }
    }

    private SubLevel getSubLevel() {
        if (level == null) return null;
        Object lvlObj = level;
        if (lvlObj instanceof SubLevel sl) return sl;
        Object obj = dev.ryanhcode.sable.Sable.HELPER.getContaining(level, worldPosition);
        if (obj instanceof SubLevel sl) return sl;
        return null;
    }

    public static void tick(Level level, BlockPos pos, BlockState state, SputnikBlockEntity blockEntity) {
        if (!level.isClientSide) {
            blockEntity.tickNodes();
        }
    }

    private void tickNodes() {
        // NOTE: LinkedSignalHandler.tick() is called once per server tick from
        // RocketNautics.onLevelTick() to avoid double-ticking when multiple Sputniks exist.
        graph.tick();
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

    public double getPitch() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            Vector3d euler = subLevel.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
            return Math.toDegrees(euler.x);
        }
        return 0;
    }

    public double getYaw() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            Vector3d euler = subLevel.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
            return Math.toDegrees(euler.y);
        }
        return 0;
    }

    public double getRoll() {
        SubLevel subLevel = getSubLevel();
        if (subLevel != null) {
            Vector3d euler = subLevel.logicalPose().orientation().getEulerAnglesYXZ(new Vector3d());
            return Math.toDegrees(euler.z);
        }
        return 0;
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

    // -----------------------------------------------------------------------
    // New World Telemetry
    // -----------------------------------------------------------------------

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

    // -----------------------------------------------------------------------
    // DeepSpace API helpers
    // -----------------------------------------------------------------------

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

    /** Returns the DeepSpaceInstance this sputnik is part of, or null. */
    public DeepSpaceInstance getDeepSpaceInstance() {
        if (!isInDeepSpace()) return null;
        if (level == null || level.isClientSide() || level.getServer() == null) return null;
        DeepSpaceData data = DeepSpaceData.getInstance(level.getServer());
        Vector3d pos = getGlobalPos();
        return data.getInstanceForPos((int) pos.x, (int) pos.z);
    }

    /** Orbital semi-major axis in metres, or NaN if not in DeepSpace. */
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

    /** Orbital eccentricity (0 = circular). */
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

    /** Orbital inclination in degrees. */
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

    /** Orbital period in seconds, or NaN if orbit is hyperbolic. */
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

    /** Current orbital speed in m/s. */
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

    /** Gravitational acceleration at current position (m/s²). */
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

    /** Name of the current orbital frame (parent body). */
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

    /** Radius of the parent body in metres, or 0 if unknown. */
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

    /** Distance from current position to the nearest planet surface in metres. */
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

    /** Returns true if the sputnik is within the transition height of the nearest planet's atmosphere. */
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

    /** Comma-separated AtmosphereFlags at current altitude, or empty string. */
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

    /** Universe tick count from DeepSpaceData. */
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
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("NodeGraph", graph.save());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("NodeGraph")) {
            CompoundTag graphTag = tag.getCompound("NodeGraph");
            graph.setRegistries(registries);
            graph.load(graphTag);
            graph.setContext(this);
        }
    }
}
