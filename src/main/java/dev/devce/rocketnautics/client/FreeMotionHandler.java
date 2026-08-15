package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.RocketNauticsClient;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.PlanetDimensionData;
import dev.devce.rocketnautics.network.FreeMotionPayload;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;

import static net.minecraft.util.Mth.approach;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class FreeMotionHandler {
    private static float rollVelocity = 0.0f;
    private static float currentRoll = 0.0f;

    private static final float ROLL_ACCELERATION = 240.0f; // deg/sec2
    private static final float MAX_ROLL_SPEED = 180.0f; // deg/sec
    private static final float ROLL_DAMPING = 4.0f;

    private static final Map<Integer, Vector3f> thrustStrength = new HashMap<>();

    public static Vector3f getThrustStrength(int id) {
        return thrustStrength.getOrDefault(id, new Vector3f(0));
    }

    public static void putThrustStrength(int id, Vector3f thrustStrength) {
        FreeMotionHandler.thrustStrength.put(id, thrustStrength);
    }

    @SubscribeEvent
    public static void onRender(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;

        if (player == null) return;

        FreeMotionEntity fme = (FreeMotionEntity) player;

        if (!fme.is6DOFEnabled()) {
            currentRoll = 0.0f;
            rollVelocity = 0.0f;

            return;
        }

        Quaternionf quat = fme.getOrientation();

        DeltaTracker dTracker = event.getPartialTick();
        float dTime = dTracker.getRealtimeDeltaTicks() / 20.0f;

        quat.rotateZ((float) Math.toRadians(-currentRoll));

        if (Math.abs(rollVelocity) > 0.001f) {
            currentRoll += rollVelocity * dTime;
        }

        quat.rotateZ((float) Math.toRadians(currentRoll));
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;

        if (player == null) return;
        if (!player.level().getFluidState(player.blockPosition()).isEmpty()) return;

        FreeMotionEntity fme = (FreeMotionEntity) player;
        if (!fme.is6DOFEnabled()) {
            if (player.isSwimming() && player.level().getFluidState(player.blockPosition()).isEmpty()) {
                player.setSwimming(false);
                player.setPose(net.minecraft.world.entity.Pose.STANDING);
            }
            return;
        }

        Quaternionf quat = new Quaternionf(fme.getOrientation());
        Vector3f thrust = getThrustStrength(player.getId());

        PacketDistributor.sendToServer(new FreeMotionPayload(quat, player.getDeltaMovement().toVector3f(), thrust));

        if (!fme.isAmbulant()) return;

        float dTime = 1.0f / 20.0f;

        float rollInput = 0.0f;

        if (RocketNauticsClient.JETPACK_ROLL_LEFT.isDown()) rollInput += 1.0f;
        if (RocketNauticsClient.JETPACK_ROLL_RIGHT.isDown()) rollInput -= 1.0f;

        rollVelocity += Math.clamp(
                rollInput * ROLL_ACCELERATION * dTime,
                -MAX_ROLL_SPEED,
                MAX_ROLL_SPEED
        );

        if (rollInput == 0) {
            rollVelocity = approach(
                    rollVelocity,
                    0.0f,
                    ROLL_DAMPING * 100.0f * dTime
            );
        }

        float vertical = 0.0f;

        if (mc.options.keyJump.isDown()) vertical += 1.0f;
        if (mc.options.keyShift.isDown()) vertical -= 1.0f;

        Vector3f motion = new Vector3f(0, vertical, 0);
        motion = quat.transform(motion);

        if (Math.abs(vertical) != 0 ) player.addDeltaMovement(new Vec3(motion).normalize().scale(0.02f));
    }

    @SubscribeEvent
    public static void onPlayerRender(net.neoforged.neoforge.client.event.RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;
        if (!(player instanceof FreeMotionEntity fme)) return;

        if (!fme.is6DOFEnabled() || !fme.isAmbulant()) return;

        Vector3f thrustStrength = FreeMotionHandler.getThrustStrength(player.getId());
        if (thrustStrength.lengthSquared() < 0.001f) return;

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.BODY, new Vector3f(0, 11, 8), new Vector3f(0.0f, 2.0f, 4.0f), 3.0f, thrustStrength, 2.0f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.BODY, new Vector3f(0, 6, -6), new Vector3f(0.0f, 0.0f, -2.0f), 3.0f, thrustStrength, 2.0f, player);

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.BODY, new Vector3f(6, 2.5f, 3.5f), new Vector3f(1.5f, 0.0f, 0.0f), 0.75f, thrustStrength, 0.75f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.BODY, new Vector3f(-6, 2.5f, 3.5f), new Vector3f(-1.5f, 0.0f, 0.0f), 0.75f, thrustStrength, 0.75f, player);

        Vector3f arm_origin = new Vector3f(-0.5f, 8.0f, 5.0f);
        Vector3f common_velocity = new Vector3f(0.0f, 2.0f, 1.0f);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.RIGHT_ARM, new Vector3f(arm_origin), common_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.LEFT_ARM, new Vector3f(arm_origin).mul(-1, 1, 1), common_velocity, 1.0f, thrustStrength, 1.0f, player);

        Vector3f leg_down_main_origin = new Vector3f(0, 12, 5);

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_down_main_origin), common_velocity, 2.0f, thrustStrength, 1.5f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_down_main_origin).mul(-1, 1, 1), common_velocity, 2.0f, thrustStrength, 1.5f, player);

        Vector3f leg_up_main_origin = new Vector3f(0, 3, 4);
        Vector3f leg_up_main_velocity = new Vector3f(common_velocity).mul(1, -1, 1);

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_up_main_origin), leg_up_main_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_up_main_origin).mul(-1, 1, 1), leg_up_main_velocity, 1.0f, thrustStrength, 1.0f, player);

        Vector3f leg_sides_main_origin = new Vector3f(-4.75f, 8f, 0.0f);
        Vector3f leg_sides_main_velocity = new Vector3f(-1.0f, 2.0f, 0.0f);

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_sides_main_origin), leg_sides_main_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_sides_main_origin).mul(-1, 1, 1), new Vector3f(leg_sides_main_velocity).mul(-1, 1, 1), 1.0f, thrustStrength, 1.0f, player);

        Vector3f leg_forward_aux_origin = new Vector3f(-3f, 3f, -3f);
        Vector3f leg_forward_aux_velocity = new Vector3f(0.0f, 0.0f, -1.0f);

        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_forward_aux_origin), leg_forward_aux_velocity, 0.75f, thrustStrength, 0.75f, player);
        spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_forward_aux_origin).mul(-1, 1, 1), leg_forward_aux_velocity, 0.75f, thrustStrength, 0.75f, player);
    }

    private static void spawnJetpackFlame(dev.devce.rocketnautics.client.render.JetpackLayer.JetpackModelPart anchor, Vector3f originLocal, Vector3f velocityLocal, float maxThrust, Vector3f thrust, float scale, Player player) {
        Vector3f origin = dev.devce.rocketnautics.client.render.JetpackLayer.modelPart2worldSpace(player, anchor, originLocal);
        if (origin == null) return;

        Vector3f tail = dev.devce.rocketnautics.client.render.JetpackLayer.modelPart2worldSpace(player, anchor, new Vector3f(originLocal).add(velocityLocal));
        if (tail == null) return;

        Vector3f delta = new Vector3f(tail).sub(origin);
        if (delta.lengthSquared() < 1e-6f) return;
        Vector3f direction = new Vector3f(delta).mul(-1).normalize();

        Vector3f thrustNorm = new Vector3f(thrust);
        if (thrustNorm.lengthSquared() > 1e-6f) {
            thrustNorm.normalize();
        }
        float thrustStrength = (direction.dot(thrustNorm) + 1.0f) * 0.5f;

        delta.mul(maxThrust * thrustStrength * 0.5f);

        if (thrustStrength > 0.1f) {
            player.level().addParticle(
                    new dev.devce.rocketnautics.content.particles.JetpackFlameParticle.JetpackFlameParticleOptions(new Vector3f(0.1f, 1.0f, 1.0f), scale * thrustStrength, 3),
                    origin.x, origin.y, origin.z,
                    delta.x, delta.y, delta.z
            );
        }
    }
    public static boolean apply6DOFPhysics(Vector3f motion, LivingEntity e) {
        //
        // fallbacks
        //

        if (!(e instanceof Player p)) return false;

        if (p.onClimbable()) return false;

        if (!(e instanceof FreeMotionEntity fme)) return false;
        if (!fme.is6DOFEnabled()) return false;

        Level l = p.level();
        if (!l.getFluidState(p.blockPosition()).isEmpty()) {
            fme.setAmbulant(false);
            return false;
        }

        float maxDampenerAcceleration = fme.isAmbulant() ? fme.getDampenerForce() * fme.getMovementAcceleration() : 0.0f;
        Vector3f velocity = e.getDeltaMovement().toVector3f();

        //
        // natural forces
        //

        Vector3f environmentalForceMultiplier;

        // drag

        int speedLimit = RocketConfig.SERVER.entitySpeedLimit.get();

        float pressure;

        if (DeepSpaceHelper.isDeepSpace(l)) {
            pressure = 0;
        } else {
            pressure = (float)DeepSpaceHelper.getDataForDimension(l).map(PlanetDimensionData::entityDragMultiplier).orElse(PlanetDimensionData.EMPTY_BEZIER).evaluateFunction(e.getY());
        }

        float dragXZ = 1 - (1 - 0.91f) * pressure;
        float dragY = 1 - 0.02f * pressure;

        environmentalForceMultiplier = new Vector3f(dragXZ, dragY, dragXZ);


        // friction

        if (!p.shouldDiscardFriction() && e.onGround()) {
            float friction = l.getBlockState(e.getBlockPosBelowThatAffectsMyMovement()).getFriction(l, e.getBlockPosBelowThatAffectsMyMovement(), e);
            environmentalForceMultiplier.sub(new Vector3f(friction));
        }



        // gravity

        //kinda cheated gravity dampener
        Vector3f gravity = new Vector3f(0, -(float)p.getGravity(), 0);



        // apply

        velocity.mul(environmentalForceMultiplier);
        if (!p.getAbilities().flying) {
            velocity.add(gravity);
            velocity.y += Math.clamp(maxDampenerAcceleration, 0, (float)p.getGravity());
        }

        //
        // propulsion
        //

        Quaternionf orientation = new Quaternionf(fme.getOrientation());

        Vector3f acceleration = orientation.transform(motion.mul(-1));
        acceleration.mul(fme.isAmbulant() ? fme.getMovementAcceleration() : 0.0f);

        //
        // dampeners
        //

        // only dampen existing velocity
        Vector3f movementDampenerAcceleration = new Vector3f(
                -Math.clamp(Math.abs(velocity.x), 0, maxDampenerAcceleration) * Math.signum(velocity.x),
                -Math.clamp(Math.abs(velocity.y), 0, maxDampenerAcceleration) * Math.signum(velocity.y),
                -Math.clamp(Math.abs(velocity.z), 0, maxDampenerAcceleration) * Math.signum(velocity.z)
        ).mul(0.05f);

        velocity.add(movementDampenerAcceleration);

        //
        // apply
        //

        velocity.add(acceleration);

        e.setDeltaMovement(new Vec3(velocity));
        e.move(MoverType.SELF, new Vec3(velocity));

        // remove default fall damage
        e.fallDistance = 0;

        // calculate impact damage
        if (!l.isClientSide && (e.horizontalCollision || e.verticalCollision)) {
            float speed = velocity.length();

            if (speed > 0.3f) {
                e.playSound((speed * 10) > 4 ? e.getFallSounds().big() : e.getFallSounds().small(), 1.0f, 1.0f);
                e.hurt(
                    e.damageSources().flyIntoWall(),
                    speed * 10.0f
                );
            }
        }

        // effects
        e.setSwimming(true);
        e.calculateEntityAnimation(false);

        float maxThrust = maxDampenerAcceleration + fme.getMovementAcceleration();
        Vector3f thrust = new Vector3f(acceleration).add(movementDampenerAcceleration).sub(gravity);
        Vector3f thrustStrength = maxThrust > 0 ? new Vector3f(thrust).div(maxThrust) : new Vector3f(0);

        FreeMotionHandler.thrustStrength.put(e.getId(), thrustStrength);

        return true;
    }
}
