package dev.devce.rocketnautics.content.blocks.gyrodyne;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.devce.rocketnautics.RocketConfig;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.companion.math.JOMLConversion;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.AxisAngle4d;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;

public class GyrodyneBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor {

    public ScrollValueBehaviour modeSelector;

    // Server-side SAS target orientation lock
    private Quaterniond lockedOrientation = null;
    private GyrodyneMode lastMode = GyrodyneMode.OFF;

    // Animation states for renderer
    private float rotorAngle = 0;
    private float prevRotorAngle = 0;
    private float gimbalTiltX = 0;
    private float prevGimbalTiltX = 0;
    private float gimbalTiltZ = 0;
    private float prevGimbalTiltZ = 0;
    private float targetGimbalTiltX = 0;
    private float targetGimbalTiltZ = 0;

    private static final Vector3d tempAngVel = new Vector3d();
    private static final Vector3d tempTorque = new Vector3d();

    public GyrodyneBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        modeSelector = new ScrollValueBehaviour(
                Component.translatable("gui.rocketnautics.gyrodyne.mode"),
                this,
                new CenteredSideValueBoxTransform(
                        (state, direction) -> {
                            if (!state.hasProperty(GyrodyneBlock.FACING)) return direction.getAxis().isHorizontal();
                            Direction facing = state.getValue(GyrodyneBlock.FACING);
                            return direction != facing && direction != facing.getOpposite();
                        }
                )
        );
        modeSelector.between(0, GyrodyneMode.values().length - 1);
        modeSelector.withFormatter(v -> GyrodyneMode.fromIndex(v).getComponent().getString());
        modeSelector.setValue(GyrodyneMode.OFF.ordinal());

        behaviours.add(modeSelector);
    }

    public GyrodyneMode getMode() {
        if (modeSelector == null) return GyrodyneMode.OFF;
        return GyrodyneMode.fromIndex(modeSelector.getValue());
    }

    public void setMode(GyrodyneMode mode) {
        if (modeSelector != null) {
            modeSelector.setValue(mode.ordinal());
            notifyUpdate();
        }
    }

    public boolean isActive() {
        if (getBlockState().getValue(GyrodyneBlock.POWERED)) return false;
        return getMode() != GyrodyneMode.OFF;
    }

    @Override
    public void tick() {
        super.tick();

        prevRotorAngle = rotorAngle;
        prevGimbalTiltX = gimbalTiltX;
        prevGimbalTiltZ = gimbalTiltZ;

        if (isActive()) {
            rotorAngle = (rotorAngle + 25.0f) % 360.0f;
            gimbalTiltX = Mth.lerp(0.1f, gimbalTiltX, targetGimbalTiltX);
            gimbalTiltZ = Mth.lerp(0.1f, gimbalTiltZ, targetGimbalTiltZ);
        } else {
            gimbalTiltX = Mth.lerp(0.05f, gimbalTiltX, 0f);
            gimbalTiltZ = Mth.lerp(0.05f, gimbalTiltZ, 0f);
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        if (!isActive()) {
            lockedOrientation = null;
            lastMode = GyrodyneMode.OFF;
            targetGimbalTiltX = 0f;
            targetGimbalTiltZ = 0f;
            return;
        }

        double mass = subLevel.getMassTracker().getMass();
        if (mass <= 0) return;

        GyrodyneMode currentMode = getMode();
        Quaterniond shipOrientation = new Quaterniond(subLevel.logicalPose().orientation());

        // Mode switch check
        if (currentMode != lastMode) {
            lockedOrientation = new Quaterniond(shipOrientation);
            lastMode = currentMode;
        }

        handle.getAngularVelocity(tempAngVel);

        double strength = RocketConfig.SERVER.gyrodyneStrength.getAsDouble();
        double kp = 6.0;  // Proportional gain
        double kd = 3.5;  // Derivative gain (damping)

        Vector3d correctiveTorque = new Vector3d();

        switch (currentMode) {
            case SAS -> {
                if (lockedOrientation == null) {
                    lockedOrientation = new Quaterniond(shipOrientation);
                }

                // Error quaternion: from current to locked
                Quaterniond errorQuat = new Quaterniond(lockedOrientation).mul(new Quaterniond(shipOrientation).conjugate());
                if (errorQuat.w < 0) {
                    errorQuat.x = -errorQuat.x;
                    errorQuat.y = -errorQuat.y;
                    errorQuat.z = -errorQuat.z;
                    errorQuat.w = -errorQuat.w;
                }

                AxisAngle4d axisAngle = new AxisAngle4d(errorQuat);
                double angle = axisAngle.angle;
                if (angle > Math.PI) angle -= 2 * Math.PI;

                Vector3d errorRot = new Vector3d(axisAngle.x, axisAngle.y, axisAngle.z).mul(angle);
                if (Double.isFinite(errorRot.x) && Double.isFinite(errorRot.y) && Double.isFinite(errorRot.z)) {
                    correctiveTorque.set(errorRot).mul(kp).sub(new Vector3d(tempAngVel).mul(kd));
                }
            }

            case PROGRADE -> {
                Vector3d vel = new Vector3d(subLevel.logicalPose().position()).sub(subLevel.lastPose().position());
                if (vel.lengthSquared() > 1e-4) {
                    Vector3d desiredDir = new Vector3d(vel).normalize();
                    Vector3d shipForward = shipOrientation.transform(new Vector3d(0, 0, -1));

                    Vector3d rotAxis = new Vector3d(shipForward).cross(desiredDir);
                    double dot = Math.clamp(shipForward.dot(desiredDir), -1.0, 1.0);
                    double angle = Math.acos(dot);

                    if (rotAxis.lengthSquared() > 1e-6) {
                        rotAxis.normalize(angle);
                        correctiveTorque.set(rotAxis).mul(kp).sub(new Vector3d(tempAngVel).mul(kd));
                    } else {
                        correctiveTorque.set(tempAngVel).negate().mul(kd);
                    }
                    lockedOrientation = new Quaterniond(shipOrientation);
                } else {
                    // Not moving fast enough: dampen angular velocity
                    correctiveTorque.set(tempAngVel).negate().mul(kd);
                }
            }

            case RETROGRADE -> {
                Vector3d vel = new Vector3d(subLevel.logicalPose().position()).sub(subLevel.lastPose().position());
                if (vel.lengthSquared() > 1e-4) {
                    Vector3d desiredDir = new Vector3d(vel).negate().normalize();
                    Vector3d shipForward = shipOrientation.transform(new Vector3d(0, 0, -1));

                    Vector3d rotAxis = new Vector3d(shipForward).cross(desiredDir);
                    double dot = Math.clamp(shipForward.dot(desiredDir), -1.0, 1.0);
                    double angle = Math.acos(dot);

                    if (rotAxis.lengthSquared() > 1e-6) {
                        rotAxis.normalize(angle);
                        correctiveTorque.set(rotAxis).mul(kp).sub(new Vector3d(tempAngVel).mul(kd));
                    } else {
                        correctiveTorque.set(tempAngVel).negate().mul(kd);
                    }
                    lockedOrientation = new Quaterniond(shipOrientation);
                } else {
                    // Not moving fast enough: dampen angular velocity
                    correctiveTorque.set(tempAngVel).negate().mul(kd);
                }
            }

            default -> {}
        }

        if (correctiveTorque.lengthSquared() > 1e-6) {
            double maxTorque = strength * 2.0;
            if (correctiveTorque.length() > maxTorque) {
                correctiveTorque.normalize(maxTorque);
            }

            // Transform corrective torque from world to sublevel local frame
            Vector3d localTorque = new Vector3d(correctiveTorque);
            subLevel.logicalPose().orientation().transformInverse(localTorque);

            targetGimbalTiltX = (float) Math.clamp(localTorque.x * 2.0, -30.0, 30.0);
            targetGimbalTiltZ = (float) Math.clamp(localTorque.z * 2.0, -30.0, 30.0);

            // Apply angular impulse scaled with mass
            Vector3d impulse = new Vector3d(localTorque).mul(mass * timeStep * 0.05);
            handle.applyAngularImpulse(impulse);
        } else {
            targetGimbalTiltX = 0f;
            targetGimbalTiltZ = 0f;
        }
    }

    public float getRotorAngle(float partialTicks) {
        return Mth.lerp(partialTicks, prevRotorAngle, rotorAngle);
    }

    public float getGimbalTiltX(float partialTicks) {
        return Mth.lerp(partialTicks, prevGimbalTiltX, gimbalTiltX);
    }

    public float getGimbalTiltZ(float partialTicks) {
        return Mth.lerp(partialTicks, prevGimbalTiltZ, gimbalTiltZ);
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        if (lockedOrientation != null) {
            tag.putDouble("LockedQuatX", lockedOrientation.x);
            tag.putDouble("LockedQuatY", lockedOrientation.y);
            tag.putDouble("LockedQuatZ", lockedOrientation.z);
            tag.putDouble("LockedQuatW", lockedOrientation.w);
        }
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.contains("LockedQuatX")) {
            lockedOrientation = new Quaterniond(
                    tag.getDouble("LockedQuatX"),
                    tag.getDouble("LockedQuatY"),
                    tag.getDouble("LockedQuatZ"),
                    tag.getDouble("LockedQuatW")
            );
        }
    }
}
