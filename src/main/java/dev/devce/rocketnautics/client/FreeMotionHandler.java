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
        if (!fme.is6DOFEnabled()) return;

        Quaternionf quat = new Quaternionf(fme.getOrientation());

        PacketDistributor.sendToServer(new FreeMotionPayload(quat, player.getDeltaMovement().toVector3f()));

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
