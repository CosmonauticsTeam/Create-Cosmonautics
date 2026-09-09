package dev.devce.rocketnautics.content.blocks.gyrodyne;

import com.simibubi.create.api.equipment.goggles.IHaveGoggleInformation;
import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.peripherals.EngineIdManager;
import dev.devce.rocketnautics.api.peripherals.IPeripheral;
import dev.devce.rocketnautics.api.peripherals.PeripheralRegistry;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class GyrodyneBlockEntity extends SmartBlockEntity implements BlockEntitySubLevelActor, IPeripheral, IHaveGoggleInformation {

    public ScrollValueBehaviour modeSelector;

    private UUID uniqueId = UUID.randomUUID();
    private int peripheralId = -1;

    // Animation states for renderer (outer gimbal X, inner gimbal Z, spherical rotor Y)
    private float rotorAngle = 0f;
    private float prevRotorAngle = 0f;
    private float rotorSpeed = 0f;
    private float targetRotorSpeed = 0f;

    private float gimbalTiltX = 0f;
    private float prevGimbalTiltX = 0f;
    private float gimbalTiltZ = 0f;
    private float prevGimbalTiltZ = 0f;

    private float targetGimbalTiltX = 0f;
    private float targetGimbalTiltZ = 0f;

    private int syncCooldown = 0;
    private float lastSyncedTiltX = 0f;
    private float lastSyncedTiltZ = 0f;

    private static final Vector3d tempAngVel = new Vector3d();

    public static final int ENERGY_CONSUMPTION_RATE = 200; // 200 FE/t when active
    public static final int ENERGY_CAPACITY = 10000;

    private final dev.devce.rocketnautics.content.energy.CustomEnergyStorage energyStorage = 
            new dev.devce.rocketnautics.content.energy.CustomEnergyStorage(ENERGY_CAPACITY, 500);

    public net.neoforged.neoforge.energy.IEnergyStorage getEnergyStorage(@org.jetbrains.annotations.Nullable Direction side) {
        return energyStorage;
    }

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
            if (level != null && !level.isClientSide) {
                sendData();
            }
        }
    }

    public boolean isActive() {
        if (getBlockState().hasProperty(GyrodyneBlock.POWERED) && getBlockState().getValue(GyrodyneBlock.POWERED)) return false;
        if (getMode() == GyrodyneMode.OFF) return false;
        return energyStorage.getEnergyStored() >= ENERGY_CONSUMPTION_RATE;
    }

    private int lastSyncedEnergy = 0;

    @Override
    public void tick() {
        super.tick();

        prevRotorAngle = rotorAngle;
        prevGimbalTiltX = gimbalTiltX;
        prevGimbalTiltZ = gimbalTiltZ;

        boolean active = isActive();

        if (level != null && !level.isClientSide && active) {
            energyStorage.extractEnergyInternal(ENERGY_CONSUMPTION_RATE);
            if (energyStorage.getEnergyStored() < ENERGY_CONSUMPTION_RATE) {
                active = false;
            }
        }

        targetRotorSpeed = active ? 40.0f : 0.0f;
        rotorSpeed = Mth.lerp(0.05f, rotorSpeed, targetRotorSpeed);

        if (rotorSpeed > 0.01f) {
            rotorAngle = (rotorAngle + rotorSpeed) % 360.0f;
        }

        if (active) {
            gimbalTiltX = Mth.lerp(0.25f, gimbalTiltX, targetGimbalTiltX);
            gimbalTiltZ = Mth.lerp(0.25f, gimbalTiltZ, targetGimbalTiltZ);
        } else {
            gimbalTiltX = Mth.lerp(0.08f, gimbalTiltX, 0f);
            gimbalTiltZ = Mth.lerp(0.08f, gimbalTiltZ, 0f);
        }

        if (level != null && !level.isClientSide) {
            syncCooldown++;
            boolean tiltChanged = Math.abs(targetGimbalTiltX - lastSyncedTiltX) > 0.3f || Math.abs(targetGimbalTiltZ - lastSyncedTiltZ) > 0.3f;
            boolean energyChanged = Math.abs(energyStorage.getEnergyStored() - lastSyncedEnergy) >= 50;
            if (syncCooldown >= 10 || tiltChanged || energyChanged) {
                lastSyncedTiltX = targetGimbalTiltX;
                lastSyncedTiltZ = targetGimbalTiltZ;
                lastSyncedEnergy = energyStorage.getEnergyStored();
                sendData();
                syncCooldown = 0;
            }
        }
    }

    // Attitude tracking for HOLD mode
    private Quaterniond holdOrientation = null;
    private GyrodyneMode lastMode = GyrodyneMode.OFF;

    @Override
    public void sable$physicsTick(ServerSubLevel subLevel, RigidBodyHandle handle, double timeStep) {
        GyrodyneMode currentMode = getMode();

        if (!isActive()) {
            targetGimbalTiltX = 0f;
            targetGimbalTiltZ = 0f;
            holdOrientation = null;
            lastMode = currentMode;
            return;
        }

        double mass = subLevel.getMassTracker().getMass();
        if (mass <= 0) return;

        Quaterniond shipOrientation = new Quaterniond(subLevel.logicalPose().orientation());
        Direction facing = getBlockState().hasProperty(GyrodyneBlock.FACING)
                ? getBlockState().getValue(GyrodyneBlock.FACING)
                : Direction.UP;

        Quaterniond blockFacingRot = getFacingRotation(facing);
        Quaterniond blockWorldOrientation = new Quaterniond(shipOrientation).mul(blockFacingRot);

        handle.getAngularVelocity(tempAngVel);
        Vector3d localAngVel = blockWorldOrientation.transformInverse(new Vector3d(tempAngVel));

        double baseStrength = RocketConfig.SERVER.gyrodyneStrength.getAsDouble();
        double massScale = Math.max(1.0, Math.sqrt(mass / 30.0));

        double kp = 30.0;  // Proportional stiffness for directional alignment / attitude hold
        double kd = 22.0;  // Damping coefficient for killing angular velocity/inertia

        Vector3d localTorque = new Vector3d();
        float maxGimbalAngle = 45.0f;

        if (currentMode != lastMode) {
            if (currentMode == GyrodyneMode.HOLD) {
                holdOrientation = new Quaterniond(shipOrientation);
            } else {
                holdOrientation = null;
            }
            lastMode = currentMode;
        }

        switch (currentMode) {
            case SAS -> {
                // SAS purely damps angular velocity and kills rotational inertia without returning
                double angSpeed = localAngVel.length();
                if (angSpeed > 1e-5) {
                    localTorque.set(localAngVel).negate().mul(kd * 2.5);

                    double gimbalX = -localAngVel.x * 25.0 + (localTorque.x / (baseStrength + 1e-4)) * 6.0;
                    double gimbalZ = -localAngVel.z * 25.0 + (localTorque.z / (baseStrength + 1e-4)) * 6.0;

                    targetGimbalTiltX = (float) Math.clamp(gimbalX, -maxGimbalAngle, maxGimbalAngle);
                    targetGimbalTiltZ = (float) Math.clamp(gimbalZ, -maxGimbalAngle, maxGimbalAngle);
                } else {
                    targetGimbalTiltX = 0f;
                    targetGimbalTiltZ = 0f;
                }
            }

            case HOLD -> {
                if (holdOrientation == null) {
                    holdOrientation = new Quaterniond(shipOrientation);
                }

                // Calculate orientation error relative to locked hold orientation
                Quaterniond deltaQ = new Quaterniond(holdOrientation).mul(shipOrientation.invert(new Quaterniond()));
                Vector3d rotVecWorld = quaternionToRotationVector(deltaQ);
                Vector3d localThetaErr = blockWorldOrientation.transformInverse(new Vector3d(rotVecWorld));

                localTorque.set(localThetaErr).mul(kp).sub(new Vector3d(localAngVel).mul(kd));

                double gimbalX = Math.toDegrees(localThetaErr.x) + (localTorque.x / (baseStrength + 1e-4)) * 8.0;
                double gimbalZ = Math.toDegrees(localThetaErr.z) + (localTorque.z / (baseStrength + 1e-4)) * 8.0;

                targetGimbalTiltX = (float) Math.clamp(gimbalX, -maxGimbalAngle, maxGimbalAngle);
                targetGimbalTiltZ = (float) Math.clamp(gimbalZ, -maxGimbalAngle, maxGimbalAngle);
            }

            default -> {
                // Vector-targeted modes: Prograde, Retrograde, Normal, Antinormal, Radial In/Out, Horizon, Sun
                Vector3d targetDir = getTargetDirection(currentMode, subLevel, handle, facing, shipOrientation);

                if (targetDir != null && targetDir.lengthSquared() > 1e-4) {
                    Vector3d localForward = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                    if (localForward.lengthSquared() < 0.5) localForward.set(0, 1, 0);
                    Vector3d shipForward = shipOrientation.transform(localForward, new Vector3d());

                    Vector3d rotAxis = new Vector3d(shipForward).cross(targetDir);
                    double dot = Math.clamp(shipForward.dot(targetDir), -1.0, 1.0);
                    double angle = Math.acos(dot);

                    Vector3d rotVecWorld = new Vector3d();
                    if (rotAxis.lengthSquared() > 1e-6) {
                        rotVecWorld.set(rotAxis).normalize(angle);
                    } else if (dot < -0.999) {
                        // Opposite direction: pick an orthogonal rotation axis
                        rotVecWorld.set(0, 1, 0).cross(shipForward);
                        if (rotVecWorld.lengthSquared() < 1e-4) rotVecWorld.set(1, 0, 0);
                        rotVecWorld.normalize(Math.PI);
                    }

                    Vector3d localThetaErr = blockWorldOrientation.transformInverse(new Vector3d(rotVecWorld));
                    localTorque.set(localThetaErr).mul(kp).sub(new Vector3d(localAngVel).mul(kd));

                    double gimbalX = Math.toDegrees(localThetaErr.x) + (localTorque.x / (baseStrength + 1e-4)) * 8.0;
                    double gimbalZ = Math.toDegrees(localThetaErr.z) + (localTorque.z / (baseStrength + 1e-4)) * 8.0;

                    targetGimbalTiltX = (float) Math.clamp(gimbalX, -maxGimbalAngle, maxGimbalAngle);
                    targetGimbalTiltZ = (float) Math.clamp(gimbalZ, -maxGimbalAngle, maxGimbalAngle);
                } else {
                    // Fallback to velocity damping if target vector is undefined (e.g. speed is zero)
                    localTorque.set(localAngVel).negate().mul(kd);
                    targetGimbalTiltX = (float) Math.clamp(-localAngVel.x * 20.0, -maxGimbalAngle, maxGimbalAngle);
                    targetGimbalTiltZ = (float) Math.clamp(-localAngVel.z * 20.0, -maxGimbalAngle, maxGimbalAngle);
                }
            }
        }

        if (localTorque.lengthSquared() > 1e-6) {
            double maxTorque = baseStrength * 3.0 * massScale;
            if (localTorque.length() > maxTorque) {
                localTorque.normalize(maxTorque);
            }

            // Transform torque from block local frame to sublevel frame
            Vector3d sublevelTorque = blockFacingRot.transform(new Vector3d(localTorque));

            // Apply angular impulse to rigid body physics
            Vector3d impulse = new Vector3d(sublevelTorque).mul(mass * timeStep * 0.25);
            handle.applyAngularImpulse(impulse);
        }
    }

    private Vector3d getTargetDirection(GyrodyneMode mode, ServerSubLevel subLevel, RigidBodyHandle handle, Direction facing, Quaterniond shipOrientation) {
        Vector3d up = new Vector3d(0, 1, 0);
        Vector3d vel = getShipVelocity(subLevel, handle);
        double speedSq = vel.lengthSquared();

        return switch (mode) {
            case PROGRADE -> speedSq > 0.04 ? new Vector3d(vel).normalize() : null;
            case RETROGRADE -> speedSq > 0.04 ? new Vector3d(vel).negate().normalize() : null;
            case NORMAL -> {
                if (speedSq > 0.04) {
                    Vector3d norm = new Vector3d(vel).cross(up);
                    yield norm.lengthSquared() > 1e-4 ? norm.normalize() : new Vector3d(1, 0, 0);
                }
                yield null;
            }
            case ANTINORMAL -> {
                if (speedSq > 0.04) {
                    Vector3d norm = new Vector3d(vel).cross(up);
                    yield norm.lengthSquared() > 1e-4 ? norm.negate().normalize() : new Vector3d(-1, 0, 0);
                }
                yield null;
            }
            case RADIAL_IN -> new Vector3d(0, -1, 0);
            case RADIAL_OUT -> new Vector3d(0, 1, 0);
            case HORIZON -> {
                // Horizontal flight vector level with horizon
                if (speedSq > 0.04) {
                    Vector3d horiz = new Vector3d(vel.x, 0, vel.z);
                    if (horiz.lengthSquared() > 1e-4) yield horiz.normalize();
                }
                Vector3d localForward = new Vector3d(facing.getStepX(), facing.getStepY(), facing.getStepZ());
                if (localForward.lengthSquared() < 0.5) localForward.set(0, 1, 0);
                Vector3d shipForward = shipOrientation.transform(localForward, new Vector3d());
                Vector3d h = new Vector3d(shipForward.x, 0, shipForward.z);
                yield h.lengthSquared() > 1e-4 ? h.normalize() : new Vector3d(0, 0, 1);
            }
            case SUN -> {
                if (level != null) {
                    float sunAngle = level.getSunAngle(1.0f);
                    yield new Vector3d(Math.sin(sunAngle), Math.cos(sunAngle), 0.2).normalize();
                }
                yield new Vector3d(0.5, 0.8, 0.3).normalize();
            }
            default -> null;
        };
    }

    private static Vector3d quaternionToRotationVector(Quaterniond q) {
        Quaterniond normQ = new Quaterniond(q);
        if (normQ.w < 0) {
            normQ.x = -normQ.x;
            normQ.y = -normQ.y;
            normQ.z = -normQ.z;
            normQ.w = -normQ.w;
        }

        double sinHalfAngle = Math.sqrt(normQ.x * normQ.x + normQ.y * normQ.y + normQ.z * normQ.z);
        if (sinHalfAngle < 1e-6) {
            return new Vector3d();
        }

        double angle = 2.0 * Math.atan2(sinHalfAngle, normQ.w);
        return new Vector3d(normQ.x, normQ.y, normQ.z).mul(angle / sinHalfAngle);
    }

    public static Quaterniond getFacingRotation(Direction facing) {
        return switch (facing) {
            case DOWN -> new Quaterniond().rotateX(Math.PI);
            case UP -> new Quaterniond();
            case NORTH -> new Quaterniond().rotateX(Math.PI / 2.0).rotateY(Math.PI);
            case SOUTH -> new Quaterniond().rotateX(Math.PI / 2.0);
            case WEST -> new Quaterniond().rotateZ(Math.PI / 2.0);
            case EAST -> new Quaterniond().rotateZ(-Math.PI / 2.0);
        };
    }

    private Vector3d getShipVelocity(ServerSubLevel subLevel, RigidBodyHandle handle) {
        Vector3d vel = null;
        try {
            if (handle.isValid()) {
                vel = new Vector3d(handle.getLinearVelocity());
            }
        } catch (Exception ignored) {}
        if (vel == null || vel.lengthSquared() < 1e-4) {
            vel = new Vector3d(subLevel.logicalPose().position()).sub(subLevel.lastPose().position()).mul(20.0);
        }
        return vel;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            if (peripheralId == -1) {
                peripheralId = EngineIdManager.getNextPeripheralId(level);
                notifyUpdate();
            }
            PeripheralRegistry.register(level, this);
        }
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null && !level.isClientSide) {
            PeripheralRegistry.unregister(level, this);
        }
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        if (level != null && !level.isClientSide) {
            PeripheralRegistry.unregister(level, this);
        }
    }

    @Override
    public String getPeripheralType() {
        return "gyrodyne";
    }

    @Override
    public UUID getUniqueId() {
        return uniqueId;
    }

    @Override
    public int getPeripheralId() {
        return peripheralId;
    }

    @Override
    public double readValue(String key) {
        return switch (key.toLowerCase(Locale.ROOT)) {
            case "mode" -> getMode().ordinal();
            case "active" -> isActive() ? 1.0 : 0.0;
            case "tilt_x", "tiltx" -> gimbalTiltX;
            case "tilt_z", "tiltz" -> gimbalTiltZ;
            case "target_tilt_x", "targettiltx" -> targetGimbalTiltX;
            case "target_tilt_z", "targettiltz" -> targetGimbalTiltZ;
            case "rotorspeed", "rotor_speed" -> rotorSpeed;
            case "id" -> peripheralId;
            default -> 0.0;
        };
    }

    @Override
    public void writeValue(String key, double value) {
        switch (key.toLowerCase(Locale.ROOT)) {
            case "mode" -> {
                int ord = (int) Math.round(value);
                GyrodyneMode[] modes = GyrodyneMode.values();
                if (ord >= 0 && ord < modes.length) {
                    setMode(modes[ord]);
                }
            }
            case "sas" -> setMode(value > 0.5 ? GyrodyneMode.SAS : GyrodyneMode.OFF);
            case "hold" -> setMode(value > 0.5 ? GyrodyneMode.HOLD : GyrodyneMode.OFF);
            case "prograde" -> setMode(value > 0.5 ? GyrodyneMode.PROGRADE : GyrodyneMode.OFF);
            case "retrograde" -> setMode(value > 0.5 ? GyrodyneMode.RETROGRADE : GyrodyneMode.OFF);
            case "normal" -> setMode(value > 0.5 ? GyrodyneMode.NORMAL : GyrodyneMode.OFF);
            case "antinormal" -> setMode(value > 0.5 ? GyrodyneMode.ANTINORMAL : GyrodyneMode.OFF);
            case "radial_in" -> setMode(value > 0.5 ? GyrodyneMode.RADIAL_IN : GyrodyneMode.OFF);
            case "radial_out" -> setMode(value > 0.5 ? GyrodyneMode.RADIAL_OUT : GyrodyneMode.OFF);
            case "horizon" -> setMode(value > 0.5 ? GyrodyneMode.HORIZON : GyrodyneMode.OFF);
            case "sun" -> setMode(value > 0.5 ? GyrodyneMode.SUN : GyrodyneMode.OFF);
            case "off" -> setMode(GyrodyneMode.OFF);
        }
    }

    @Override
    public void writeValues(String key, double... values) {
        if (values != null && values.length > 0) {
            writeValue(key, values[0]);
        }
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId())
                .withStyle(ChatFormatting.GOLD)));
        if (peripheralId != -1) {
            tooltip.add(Component.literal("  ID: ")
                    .append(Component.literal(String.valueOf(peripheralId)).withStyle(ChatFormatting.GOLD)));
        }
        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.status")).append(": ")
                .append(isActive()
                        ? Component.translatable("rocketnautics.goggles.active").withStyle(ChatFormatting.GREEN)
                        : Component.translatable("rocketnautics.goggles.inactive").withStyle(ChatFormatting.RED)));
        tooltip.add(Component.literal("  ").append(Component.translatable("gui.rocketnautics.gyrodyne.mode")).append(": ")
                .append(Component.translatable(getMode().getTranslationKey()).withStyle(ChatFormatting.AQUA)));
        tooltip.add(Component.literal("  ").append(Component.translatable("gui.rocketnautics.goggles.energy_stored")).append(": ")
                .append(Component.literal(energyStorage.getEnergyStored() + " / " + ENERGY_CAPACITY + " FE").withStyle(ChatFormatting.YELLOW)));
        if (getMode() != GyrodyneMode.OFF) {
            tooltip.add(Component.literal("  ").append(Component.translatable("gui.rocketnautics.goggles.energy_usage")).append(": ")
                    .append(Component.literal(ENERGY_CONSUMPTION_RATE + " FE/t").withStyle(ChatFormatting.RED)));
        }
        return true;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putUUID("UniqueId", uniqueId);
        tag.putInt("PeripheralId", peripheralId);
        tag.putFloat("TargetTiltX", targetGimbalTiltX);
        tag.putFloat("TargetTiltZ", targetGimbalTiltZ);
        tag.putFloat("RotorSpeed", rotorSpeed);
        tag.putInt("Energy", energyStorage.getEnergyStored());
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        if (tag.hasUUID("UniqueId")) {
            uniqueId = tag.getUUID("UniqueId");
        }
        if (tag.contains("PeripheralId")) {
            peripheralId = tag.getInt("PeripheralId");
        }
        targetGimbalTiltX = tag.getFloat("TargetTiltX");
        targetGimbalTiltZ = tag.getFloat("TargetTiltZ");
        if (tag.contains("RotorSpeed")) {
            rotorSpeed = tag.getFloat("RotorSpeed");
        }
        if (tag.contains("Energy")) {
            energyStorage.setEnergy(tag.getInt("Energy"));
        }
    }

    public float getRotorSpeed() {
        return rotorSpeed;
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
}


