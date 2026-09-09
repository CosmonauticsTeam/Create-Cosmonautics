package dev.devce.rocketnautics.server.telemetry;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.DeepSpaceInstance;
import dev.devce.rocketnautics.content.orbit.universe.*;
import dev.ryanhcode.sable.physics.config.dimension_physics.BezierResourceFunction;
import net.minecraft.server.MinecraftServer;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Immutable snapshot of the complete universe, planetary ephemerides,
 * celestial mechanics, and active spacecraft state.
 */
public final class TelemetrySnapshot {
    public static final double GRAVITATIONAL_CONSTANT_G = 6.67430e-11;
    public static final double ASTRONOMICAL_UNIT_M = 149597870700.0;
    public static final double SPEED_OF_LIGHT_M_S = 299792458.0;
    public static final double STANDARD_GRAVITY_G0 = 9.80665;

    public final long universeTick;
    public final String epochIso;
    public final double julianDate;
    public final double modifiedJulianDate;
    public final double timescale;
    public final float tickRate;

    public final Map<String, FrameData> frames;
    public final Map<String, BodyData> bodies;
    public final Map<String, VesselData> vessels;

    public TelemetrySnapshot(long universeTick, String epochIso, double julianDate, double modifiedJulianDate,
                             double timescale, float tickRate,
                             Map<String, FrameData> frames,
                             Map<String, BodyData> bodies,
                             Map<String, VesselData> vessels) {
        this.universeTick = universeTick;
        this.epochIso = epochIso;
        this.julianDate = julianDate;
        this.modifiedJulianDate = modifiedJulianDate;
        this.timescale = timescale;
        this.tickRate = tickRate;
        this.frames = Collections.unmodifiableMap(frames);
        this.bodies = Collections.unmodifiableMap(bodies);
        this.vessels = Collections.unmodifiableMap(vessels);
    }

    public static TelemetrySnapshot capture(MinecraftServer server) {
        if (DeepSpaceData.tooSoon(server)) {
            return new TelemetrySnapshot(0, Instant.now().toString(), 0, 0, 1.0, 20f, Map.of(), Map.of(), Map.of());
        }

        DeepSpaceData data = DeepSpaceData.getInstance(server);
        UniverseDefinition universe = data.getUniverse();
        long tick = data.getUniverseTicks();
        AbsoluteDate universeDate = data.getUniverseTime();
        float tps = server.tickRateManager().tickrate();

        // Time calculations
        long epochMillis = (long) (universeDate.durationFrom(DeepSpaceHelper.EPOCH) * 1000L);
        Instant instant = Instant.ofEpochMilli(epochMillis);
        String epochIso = DateTimeFormatter.ISO_INSTANT.format(instant);
        double jd = 2440587.5 + (epochMillis / 86400000.0);
        double mjd = jd - 2400000.5;

        // 1. Frames Tree
        Map<String, FrameData> framesMap = new LinkedHashMap<>();
        if (universe != null) {
            captureFrames(universe, framesMap);
        }

        // 2. Celestial Bodies
        Map<String, BodyData> bodiesMap = new LinkedHashMap<>();
        if (universe != null) {
            for (CubePlanet planet : universe.getPlanets()) {
                FrameTree ft = planet.frame();
                String name = ft.getName();
                PointGravitySource grav = universe.getGravitySourceById(ft.getId());

                double mu = grav != null ? grav.mu() : 0.0;
                double mass = mu > 0 ? mu / GRAVITATIONAL_CONSTANT_G : 0.0;
                double radius = planet.radius();
                double surfaceG = (mu > 0 && radius > 0) ? mu / (radius * radius) : 0.0;
                double escapeV = (mu > 0 && radius > 0) ? Math.sqrt(2.0 * mu / radius) : 0.0;
                double roi = grav != null ? grav.roi() : 0.0;

                // Positions & velocities
                Vector3D pos = Vector3D.ZERO;
                Vector3D vel = Vector3D.ZERO;
                double speed = 0.0;
                try {
                    Frame parentOrekitFrame = ft.getParent() != null ? ft.getParent().getOrekitFrame() : Frame.getRoot();
                    TimeStampedPVCoordinates pv = planet.getPVCoordinates(universeDate, parentOrekitFrame);
                    pos = pv.getPosition();
                    vel = pv.getVelocity();
                    speed = vel.getNorm();
                } catch (Exception ignored) {}

                // Keplerian elements
                KeplerianOrbit kep = null;
                try {
                    if (ft.getParent() != null && mu > 0) {
                        PointGravitySource parentGrav = universe.getGravitySourceById(ft.getParent().getId());
                        double parentMu = parentGrav != null ? parentGrav.mu() : mu;
                        TimeStampedPVCoordinates pvInParent = planet.getPVCoordinates(universeDate, ft.getParent().getOrekitFrame());
                        kep = new KeplerianOrbit(pvInParent, ft.getParent().getOrekitFrame(), parentMu);
                    }
                } catch (Exception ignored) {}

                // Rotation
                Rotation rot = planet.getRotationAtTime(universeDate);
                double rotRate = planet.rotationDescription().getRotationRate().getNorm();
                double rotPeriod = rotRate > 0 ? (2.0 * Math.PI) / rotRate : Double.NaN;

                // Atmosphere
                AtmosphereData atmoData = null;
                PlanetDimensionData pdd = planet.linkedDimension();
                if (pdd != null) {
                    List<String> flags = new ArrayList<>();
                    for (EnumSet<AtmosphereFlags> flagSet : pdd.atmosphere().values()) {
                        for (AtmosphereFlags f : flagSet) {
                            String fName = f.name();
                            if (!flags.contains(fName)) flags.add(fName);
                        }
                    }
                    List<BezierPointData> dragPoints = new ArrayList<>();
                    for (BezierResourceFunction.BezierPoint bp : pdd.entityDragMultiplier().getPoints()) {
                        dragPoints.add(new BezierPointData(bp.altitude(), bp.value(), bp.slope()));
                    }
                    atmoData = new AtmosphereData(
                            pdd.transitionHeight(),
                            flags,
                            pdd.dimensionDayTimeControllerID(),
                            pdd.applyGravityCorrectionToEntities(),
                            pdd.allowedTransfer().name(),
                            dragPoints
                    );
                }

                BodyData bData = new BodyData(
                        ft.getId(),
                        name,
                        determineBodyType(ft, grav),
                        ft.getParent() != null ? ft.getParent().getId() : -1,
                        ft.getParent() != null ? ft.getParent().getName() : null,
                        pdd != null ? pdd.key().location().toString() : null,
                        mass,
                        radius,
                        mu,
                        surfaceG,
                        escapeV,
                        roi,
                        pos.getX(), pos.getY(), pos.getZ(),
                        vel.getX(), vel.getY(), vel.getZ(),
                        speed,
                        pos.getNorm(),
                        kep != null ? kep.getA() : Double.NaN,
                        kep != null ? kep.getE() : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getI()) : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getRightAscensionOfAscendingNode()) : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getPerigeeArgument()) : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getTrueAnomaly()) : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getMeanAnomaly()) : Double.NaN,
                        kep != null ? Math.toDegrees(kep.getEccentricAnomaly()) : Double.NaN,
                        kep != null ? kep.getKeplerianPeriod() : Double.NaN,
                        kep != null ? kep.getA() * (1.0 - kep.getE()) : Double.NaN,
                        kep != null ? kep.getA() * (1.0 + kep.getE()) : Double.NaN,
                        rot.getQ0(), rot.getQ1(), rot.getQ2(), rot.getQ3(),
                        rotRate,
                        rotPeriod,
                        atmoData
                );
                bodiesMap.put(name.toLowerCase(Locale.ROOT), bData);
            }
        }

        // 3. Active Spacecraft / Vessels
        Map<String, VesselData> vesselsMap = new LinkedHashMap<>();
        for (DeepSpaceInstance inst : data.getAllInstances()) {
            if (inst.isCorrupted()) continue;
            DeepSpacePosition dsp = inst.getPosition();
            KeplerianOrbit orb = dsp.getCurrentOrbit();
            TimeStampedPVCoordinates pv = dsp.getCurrentPVCoords();

            double a = orb.getA();
            double e = orb.getE();
            double inc = Math.toDegrees(orb.getI());
            double raan = Math.toDegrees(orb.getRightAscensionOfAscendingNode());
            double aop = Math.toDegrees(orb.getPerigeeArgument());
            double nu = Math.toDegrees(orb.getTrueAnomaly());
            double meanAnom = Math.toDegrees(orb.getMeanAnomaly());
            double period = orb.getKeplerianPeriod();
            double peAlt = (a * (1.0 - e));
            double apAlt = (a * (1.0 + e));

            VesselData vData = new VesselData(
                    inst.getId(),
                    dsp.getFrame().getName(),
                    pv.getPosition().getX(), pv.getPosition().getY(), pv.getPosition().getZ(),
                    pv.getVelocity().getX(), pv.getVelocity().getY(), pv.getVelocity().getZ(),
                    pv.getVelocity().getNorm(),
                    a, e, inc, raan, aop, nu, meanAnom, period, peAlt, apAlt,
                    inst.getChunkSideLength(),
                    inst.getSideLength(),
                    inst.getNegXCorner(),
                    inst.getNegZCorner()
            );
            vesselsMap.put(String.valueOf(inst.getId()), vData);
        }

        return new TelemetrySnapshot(tick, epochIso, jd, mjd, 1.0, tps, framesMap, bodiesMap, vesselsMap);
    }

    private static void captureFrames(UniverseDefinition universe, Map<String, FrameData> map) {
        for (PointGravitySource src : universe.getGravitySources()) {
            FrameTree ft = src.frame();
            map.put(ft.getName().toLowerCase(Locale.ROOT), new FrameData(
                    ft.getId(),
                    ft.getName(),
                    ft.getParent() != null ? ft.getParent().getId() : -1,
                    ft.getParent() != null ? ft.getParent().getName() : null
            ));
        }
    }

    private static String determineBodyType(FrameTree ft, PointGravitySource grav) {
        if (ft.getParent() == null) return "STAR";
        if (ft.getParent().getParent() == null) return "PLANET";
        return "MOON";
    }

    // --- JSON Serialization Methods ---

    public JsonObject toJson() {
        JsonObject root = new JsonObject();
        root.add("time", timeToJson());
        root.add("constants", constantsToJson());
        root.add("frames", framesToJson());
        root.add("bodies", bodiesToJson());
        root.add("vessels", vesselsToJson());
        return root;
    }

    public JsonObject toFlatMap() {
        JsonObject flat = new JsonObject();
        JsonObject nested = toJson();
        flattenJson("", nested, flat);
        return flat;
    }

    private static void flattenJson(String prefix, JsonElement element, JsonObject flat) {
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
                String newPrefix = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
                flattenJson(newPrefix, entry.getValue(), flat);
            }
        } else if (element.isJsonArray()) {
            JsonArray arr = element.getAsJsonArray();
            for (int i = 0; i < arr.size(); i++) {
                flattenJson(prefix + "[" + i + "]", arr.get(i), flat);
            }
        } else {
            flat.add(prefix, element);
        }
    }

    public JsonObject timeToJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("universe_tick", universeTick);
        obj.addProperty("epoch_iso", epochIso);
        obj.addProperty("julian_date", julianDate);
        obj.addProperty("modified_julian_date", modifiedJulianDate);
        obj.addProperty("timescale", timescale);
        obj.addProperty("tick_rate", tickRate);
        return obj;
    }

    public JsonObject constantsToJson() {
        JsonObject obj = new JsonObject();
        obj.addProperty("gravitational_constant_G_m3_kg_s2", GRAVITATIONAL_CONSTANT_G);
        obj.addProperty("astronomical_unit_AU_m", ASTRONOMICAL_UNIT_M);
        obj.addProperty("speed_of_light_c_m_s", SPEED_OF_LIGHT_M_S);
        obj.addProperty("standard_gravity_g0_m_s2", STANDARD_GRAVITY_G0);
        return obj;
    }

    public JsonObject framesToJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, FrameData> e : frames.entrySet()) {
            JsonObject f = new JsonObject();
            f.addProperty("id", e.getValue().id);
            f.addProperty("name", e.getValue().name);
            f.addProperty("parent_id", e.getValue().parentId);
            f.addProperty("parent_name", e.getValue().parentName);
            obj.add(e.getKey(), f);
        }
        return obj;
    }

    public JsonObject bodiesToJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, BodyData> e : bodies.entrySet()) {
            obj.add(e.getKey(), bodyToJson(e.getValue()));
        }
        return obj;
    }

    public JsonObject bodyToJson(BodyData b) {
        JsonObject obj = new JsonObject();
        obj.addProperty("id", b.id);
        obj.addProperty("name", b.name);
        obj.addProperty("type", b.type);
        obj.addProperty("parent_id", b.parentId);
        obj.addProperty("parent_name", b.parentName);
        obj.addProperty("linked_dimension", b.linkedDimension);

        JsonObject phys = new JsonObject();
        phys.addProperty("mass_kg", b.massKg);
        phys.addProperty("radius_m", b.radiusM);
        phys.addProperty("gravitational_parameter_mu_m3_s2", b.mu);
        phys.addProperty("surface_gravity_m_s2", b.surfaceGM_s2);
        phys.addProperty("escape_velocity_m_s", b.escapeVelocityM_s);
        phys.addProperty("sphere_of_influence_roi_m", b.roiM);
        obj.add("physics", phys);

        JsonObject pos = new JsonObject();
        pos.addProperty("x_m", b.posX);
        pos.addProperty("y_m", b.posY);
        pos.addProperty("z_m", b.posZ);
        pos.addProperty("distance_from_parent_m", b.distanceFromParentM);
        obj.add("position", pos);

        JsonObject vel = new JsonObject();
        vel.addProperty("x_m_s", b.velX);
        vel.addProperty("y_m_s", b.velY);
        vel.addProperty("z_m_s", b.velZ);
        vel.addProperty("speed_m_s", b.speedM_s);
        obj.add("velocity", vel);

        JsonObject orb = new JsonObject();
        orb.addProperty("semi_major_axis_m", b.semiMajorAxisM);
        orb.addProperty("eccentricity", b.eccentricity);
        orb.addProperty("inclination_deg", b.inclinationDeg);
        orb.addProperty("raan_deg", b.raanDeg);
        orb.addProperty("arg_periapsis_deg", b.argPeriapsisDeg);
        orb.addProperty("true_anomaly_deg", b.trueAnomalyDeg);
        orb.addProperty("mean_anomaly_deg", b.meanAnomalyDeg);
        orb.addProperty("eccentric_anomaly_deg", b.eccentricAnomalyDeg);
        orb.addProperty("period_s", b.periodS);
        orb.addProperty("periapsis_radius_m", b.periapsisRadiusM);
        orb.addProperty("apoapsis_radius_m", b.apoapsisRadiusM);
        obj.add("orbit", orb);

        JsonObject rot = new JsonObject();
        JsonObject quat = new JsonObject();
        quat.addProperty("w", b.rotQuatW);
        quat.addProperty("x", b.rotQuatX);
        quat.addProperty("y", b.rotQuatY);
        quat.addProperty("z", b.rotQuatZ);
        rot.add("quaternion", quat);
        rot.addProperty("rotation_rate_rad_s", b.rotRateRadS);
        rot.addProperty("rotation_period_s", b.rotPeriodS);
        obj.add("rotation", rot);

        if (b.atmosphere != null) {
            JsonObject atmo = new JsonObject();
            atmo.addProperty("transition_height_m", b.atmosphere.transitionHeightM);
            atmo.addProperty("day_time_controller_id", b.atmosphere.dayTimeControllerId);
            atmo.addProperty("apply_gravity_correction", b.atmosphere.applyGravityCorrection);
            atmo.addProperty("allowed_transfer", b.atmosphere.allowedTransfer);

            JsonArray flagsArr = new JsonArray();
            for (String f : b.atmosphere.flags) flagsArr.add(f);
            atmo.add("flags", flagsArr);

            JsonArray dragArr = new JsonArray();
            for (BezierPointData bp : b.atmosphere.dragPoints) {
                JsonObject p = new JsonObject();
                p.addProperty("altitude_m", bp.altitude);
                p.addProperty("value", bp.value);
                p.addProperty("slope", bp.slope);
                dragArr.add(p);
            }
            atmo.add("drag_multiplier_curve", dragArr);
            obj.add("atmosphere", atmo);
        } else {
            obj.add("atmosphere", null);
        }

        return obj;
    }

    public JsonObject vesselsToJson() {
        JsonObject obj = new JsonObject();
        for (Map.Entry<String, VesselData> e : vessels.entrySet()) {
            obj.add(e.getKey(), vesselToJson(e.getValue()));
        }
        return obj;
    }

    public JsonObject vesselToJson(VesselData v) {
        JsonObject obj = new JsonObject();
        obj.addProperty("instance_id", v.instanceId);
        obj.addProperty("frame_name", v.frameName);

        JsonObject pos = new JsonObject();
        pos.addProperty("x_m", v.posX);
        pos.addProperty("y_m", v.posY);
        pos.addProperty("z_m", v.posZ);
        obj.add("position", pos);

        JsonObject vel = new JsonObject();
        vel.addProperty("x_m_s", v.velX);
        vel.addProperty("y_m_s", v.velY);
        vel.addProperty("z_m_s", v.velZ);
        vel.addProperty("speed_m_s", v.speedM_s);
        obj.add("velocity", vel);

        JsonObject orb = new JsonObject();
        orb.addProperty("semi_major_axis_m", v.semiMajorAxisM);
        orb.addProperty("eccentricity", v.eccentricity);
        orb.addProperty("inclination_deg", v.inclinationDeg);
        orb.addProperty("raan_deg", v.raanDeg);
        orb.addProperty("arg_periapsis_deg", v.argPeriapsisDeg);
        orb.addProperty("true_anomaly_deg", v.trueAnomalyDeg);
        orb.addProperty("mean_anomaly_deg", v.meanAnomalyDeg);
        orb.addProperty("period_s", v.periodS);
        orb.addProperty("periapsis_altitude_m", v.periapsisAltM);
        orb.addProperty("apoapsis_altitude_m", v.apoapsisAltM);
        obj.add("orbit", orb);

        JsonObject bounds = new JsonObject();
        bounds.addProperty("chunk_side_length", v.chunkSideLength);
        bounds.addProperty("side_length_m", v.sideLengthM);
        bounds.addProperty("neg_x_corner", v.negXCorner);
        bounds.addProperty("neg_z_corner", v.negZCorner);
        obj.add("bounds", bounds);

        return obj;
    }

    // --- Record Data Structures ---

    public record FrameData(int id, String name, int parentId, String parentName) {}

    public record BodyData(
            int id, String name, String type, int parentId, String parentName, String linkedDimension,
            double massKg, double radiusM, double mu, double surfaceGM_s2, double escapeVelocityM_s, double roiM,
            double posX, double posY, double posZ, double velX, double velY, double velZ, double speedM_s, double distanceFromParentM,
            double semiMajorAxisM, double eccentricity, double inclinationDeg, double raanDeg, double argPeriapsisDeg,
            double trueAnomalyDeg, double meanAnomalyDeg, double eccentricAnomalyDeg, double periodS,
            double periapsisRadiusM, double apoapsisRadiusM,
            double rotQuatW, double rotQuatX, double rotQuatY, double rotQuatZ, double rotRateRadS, double rotPeriodS,
            AtmosphereData atmosphere
    ) {}

    public record AtmosphereData(
            int transitionHeightM, List<String> flags, int dayTimeControllerId,
            boolean applyGravityCorrection, String allowedTransfer, List<BezierPointData> dragPoints
    ) {}

    public record BezierPointData(double altitude, double value, double slope) {}

    public record VesselData(
            long instanceId, String frameName,
            double posX, double posY, double posZ,
            double velX, double velY, double velZ, double speedM_s,
            double semiMajorAxisM, double eccentricity, double inclinationDeg, double raanDeg, double argPeriapsisDeg,
            double trueAnomalyDeg, double meanAnomalyDeg, double periodS,
            double periapsisAltM, double apoapsisAltM,
            int chunkSideLength, int sideLengthM, int negXCorner, int negZCorner
    ) {}
}
