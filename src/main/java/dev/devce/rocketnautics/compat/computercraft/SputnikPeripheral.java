package dev.devce.rocketnautics.compat.computercraft;

import dan200.computercraft.api.lua.LuaFunction;
import dan200.computercraft.api.peripheral.IPeripheral;
import dev.devce.rocketnautics.content.blocks.SputnikBlockEntity;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.ryanhcode.sable.physics.config.dimension_physics.DimensionPhysicsData;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.phys.Vec3;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import org.joml.Vector3d;
import org.orekit.utils.TimeStampedPVCoordinates;

public class SputnikPeripheral implements IPeripheral {
    private final SputnikBlockEntity sputnik;

    public SputnikPeripheral(SputnikBlockEntity sputnik) {
        this.sputnik = sputnik;
    }

    @NotNull
    @Override
    public String getType() {
        return "sputnik";
    }

    @Override
    public boolean equals(@Nullable IPeripheral other) {
        return other instanceof SputnikPeripheral && ((SputnikPeripheral) other).sputnik == this.sputnik;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getBiomeInfo() {
        Map<String, Object> data = new HashMap<>();
        data.put("name", sputnik.getBiomeName());
        data.put("color", sputnik.getBiomeColor());
        return data;
    }

    @LuaFunction(mainThread = true)
    public final String getBiomeName() {
        return sputnik.getBiomeName();
    }

    @LuaFunction(mainThread = true)
    public final int getBiomeColor() {
        return sputnik.getBiomeColor();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Double> getGlobalPos() {
        org.joml.Vector3d pos = sputnik.getGlobalPos();
        Map<String, Double> data = new HashMap<>();
        data.put("x", pos.x);
        data.put("y", pos.y);
        data.put("z", pos.z);
        return data;
    }

    @LuaFunction(mainThread = true)
    public final String getGlobalBiomeName() {
        return sputnik.getGlobalBiomeName();
    }

    @LuaFunction(mainThread = true)
    public final int getGlobalBiomeColor() {
        return sputnik.getGlobalBiomeColor();
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getPhysics() {
        Map<String, Object> data = new HashMap<>();
        data.put("space", buildSpaceData());
        if (sputnik.getLevel() == null) return data;

        var subLevel = dev.ryanhcode.sable.Sable.HELPER.getContaining(sputnik.getLevel(), sputnik.getBlockPos());
        if (subLevel instanceof ServerSubLevel serverSubLevel) {
            // Mass
            data.put("mass", serverSubLevel.getMassTracker().getMass());

            // Pose (Position & Orientation)
            var pose = serverSubLevel.logicalPose();
            var lastPose = serverSubLevel.lastPose();

            // Orientation Quaternions
            Map<String, Double> quat = new HashMap<>();
            quat.put("w", pose.orientation().w());
            quat.put("x", pose.orientation().x());
            quat.put("y", pose.orientation().y());
            quat.put("z", pose.orientation().z());
            data.put("quaternion", quat);

            // Euler Angles (Pitch, Yaw, Roll in degrees)
            Vector3d euler = pose.orientation().getEulerAnglesYXZ(new Vector3d());
            Map<String, Double> angles = new HashMap<>();
            angles.put("pitch", Math.toDegrees(euler.x));
            angles.put("yaw", Math.toDegrees(euler.y));
            angles.put("roll", Math.toDegrees(euler.z));
            data.put("euler", angles);

            // Velocity (Calculated from position delta over 1 tick)
            Vector3d velocity = new Vector3d(pose.position()).sub(lastPose.position()).mul(20.0);
            Map<String, Double> velData = new HashMap<>();
            velData.put("x", velocity.x);
            velData.put("y", velocity.y);
            velData.put("z", velocity.z);
            data.put("velocity", velData);

            // Local Gravity approximation (if needed by flight computers)
            Vector3d gravity = DimensionPhysicsData.getGravity(sputnik.getLevel())
                    .mul(1 - GlobalSpacePhysicsHandler.calculateGravityFactor(sputnik.getLevel(), sputnik.getY()));
            data.put("gravityX", gravity.x());
            data.put("gravityY", gravity.y());
            data.put("gravityZ", gravity.z());
        }

        return data;
    }

    @LuaFunction(mainThread = true)
    public final Map<String, Object> getDeepSpaceData() {
        Map<String, Object> data = new HashMap<>();

        boolean inDeepSpace = sputnik.isInDeepSpace();
        data.put("inDeepSpace", inDeepSpace);

        if (!inDeepSpace) {
            data.put("semiMajorAxis", Double.NaN);
            data.put("eccentricity", Double.NaN);
            data.put("inclination", Double.NaN);
            data.put("period", Double.NaN);
            data.put("speed", 0.0);
            data.put("gravity", 0.0);
            data.put("parentBody", sputnik.getDimensionId());
            data.put("parentRadius", 0.0);
            data.put("distanceToPlanet", Double.NaN);
            data.put("inAtmosphere", false);
            data.put("atmosphereFlags", "");
            data.put("universeTime", 0.0);

            Map<String, Double> vel = new HashMap<>();
            vel.put("x", Double.NaN); vel.put("y", Double.NaN); vel.put("z", Double.NaN);
            data.put("velocity", vel);

            Map<String, Double> dir = new HashMap<>();
            dir.put("x", Double.NaN); dir.put("y", Double.NaN); dir.put("z", Double.NaN);
            data.put("velocityDir", dir);

            return data;
        }

        data.put("semiMajorAxis", sputnik.getOrbitalSemiMajorAxis());
        data.put("eccentricity", sputnik.getOrbitalEccentricity());
        data.put("inclination", sputnik.getOrbitalInclination());
        data.put("period", sputnik.getOrbitalPeriod());
        data.put("speed", sputnik.getOrbitalSpeed());
        data.put("gravity", sputnik.getGravityAcceleration());
        data.put("parentBody", sputnik.getParentBodyName());
        data.put("parentRadius", sputnik.getParentBodyRadius());
        data.put("distanceToPlanet", sputnik.getDistanceToPlanet());
        data.put("inAtmosphere", sputnik.isInAtmosphere());
        data.put("atmosphereFlags", sputnik.getAtmosphereFlags());
        data.put("universeTime", (double) sputnik.getUniverseTime());

        DeepSpaceInstance inst = sputnik.getDeepSpaceInstance();
        Map<String, Double> vel = new HashMap<>();
        Map<String, Double> dir = new HashMap<>();
        if (inst != null && !inst.isCorrupted()) {
            Vector3D v = inst.getPosition().getCurrentPVCoords().getVelocity();
            vel.put("x", v.getX());
            vel.put("y", v.getY());
            vel.put("z", v.getZ());

            double len = v.getNorm();
            if (len > 1e-6) {
                dir.put("x", v.getX() / len);
                dir.put("y", v.getY() / len);
                dir.put("z", v.getZ() / len);
            } else {
                dir.put("x", 0.0); dir.put("y", 0.0); dir.put("z", 0.0);
            }
        } else {
            vel.put("x", Double.NaN); vel.put("y", Double.NaN); vel.put("z", Double.NaN);
            dir.put("x", Double.NaN); dir.put("y", Double.NaN); dir.put("z", Double.NaN);
        }
        data.put("velocity", vel);
        data.put("velocityDir", dir);

        return data;
    }

    private Map<String, Object> buildSpaceData() {
        try {
            if (sputnik.getLevel() == null) return buildUnavailableSpaceData();

            MinecraftServer server = sputnik.getLevel().getServer();
            if (server == null || DeepSpaceData.tooSoon(server)) return buildUnavailableSpaceData();

            Vector3d global = sputnik.getGlobalPos();
            if (global == null) return buildUnavailableSpaceData();

            DeepSpaceData deepSpaceData = DeepSpaceData.getInstance(server);
            if (deepSpaceData == null) return buildUnavailableSpaceData();

            DeepSpaceInstance instance = deepSpaceData.getInstanceForPos((int) global.x, (int) global.z);
            if (instance == null || instance.isCorrupted() || !instance.boundingBox().contains(new Vec3(global.x, global.y, global.z))) {
                return buildUnavailableSpaceData();
            }

            DeepSpacePosition spacePos = instance.getPosition();
            if (spacePos == null || spacePos.getFrame() == null) return buildUnavailableSpaceData();

            TimeStampedPVCoordinates pv = spacePos.getCurrentPVCoords();
            if (pv == null || pv.getPosition() == null || pv.getVelocity() == null) {
                return buildUnavailableSpaceData();
            }

            Vector3D pos = pv.getPosition();
            Vector3D vel = pv.getVelocity();

            Map<String, Object> space = new HashMap<>();
            space.put("available", true);
            space.put("frame", spacePos.getFrame().getName());

            Map<String, Double> position = new HashMap<>();
            position.put("x", pos.getX());
            position.put("y", pos.getY());
            position.put("z", pos.getZ());
            space.put("position", position);

            Map<String, Double> velocity = new HashMap<>();
            velocity.put("x", vel.getX());
            velocity.put("y", vel.getY());
            velocity.put("z", vel.getZ());
            velocity.put("speed", vel.getNorm());
            space.put("velocity", velocity);

            space.put("velocityAngles", velocityToYawPitch(vel.getX(), vel.getY(), vel.getZ()));
            return space;
        } catch (RuntimeException ignored) {
            return buildUnavailableSpaceData();
        }
    }

    private static Map<String, Object> buildUnavailableSpaceData() {
        Map<String, Object> space = new HashMap<>();
        space.put("available", false);
        return space;
    }

    private static Map<String, Double> velocityToYawPitch(double vx, double vy, double vz) {
        double horizontal = Math.sqrt(vx * vx + vz * vz);

        Map<String, Double> angles = new HashMap<>();

        if (horizontal < 1e-9 && Math.abs(vy) < 1e-9) {
            angles.put("yaw", 0.0);
            angles.put("pitch", 0.0);
            return angles;
        }

        double yaw = Math.toDegrees(Math.atan2(-vx, vz));
        double pitch = Math.toDegrees(Math.atan2(-vy, horizontal));

        angles.put("yaw", yaw);
        angles.put("pitch", pitch);
        return angles;
    }
}
