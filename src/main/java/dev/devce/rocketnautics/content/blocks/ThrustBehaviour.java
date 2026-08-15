package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.BehaviourType;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;

/**
 * Universal module for thrust physics and exhaust visuals.
 * The BlockEntity (host) manages logic/fuel and provides state via {@link #update}.
 */
public class ThrustBehaviour extends BlockEntityBehaviour {
    public static final BehaviourType<ThrustBehaviour> TYPE = new BehaviourType<>();

    public enum EngineType {
        ROCKET, RCS, STEAM, ION
    }

    // Configuration
    private EngineType engineType = EngineType.ROCKET;
    private Vec3 offset = new Vec3(0.5, 0.5, 0.5); // Local offset within block

    // State (Updated by host BE)
    private float currentThrustN = 0f;
    private float throttle = 0f;
    private Vec3 exhaustDir = new Vec3(0, -1, 0); // Direction of the plume
    private boolean active = false;

    // Internal state for visual/logic parity
    private int ignitionTicks = 0;
    protected Object soundInstance;

    public ThrustBehaviour(SmartBlockEntity be) {
        super(be);
    }

    public ThrustBehaviour withType(EngineType type) {
        this.engineType = type;
        return this;
    }

    public ThrustBehaviour withOffset(Vec3 offset) {
        this.offset = offset;
        return this;
    }

    /**
     * Updates the module state. Should be called by the host BE every tick.
     */
    public void update(float thrustN, float throttle, Vec3 exhaustDir, boolean active) {
        this.currentThrustN = thrustN;
        this.throttle = throttle;
        this.exhaustDir = exhaustDir.normalize();
        this.active = active;
    }

    @Override
    public void tick() {
        super.tick();
        Level level = getWorld();
        if (level == null) return;

        float visualThrottle = this.throttle;
        float maxThrust = 0f;
        if (blockEntity instanceof dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity rt) {
            maxThrust = rt.maxThrust.getValue() * 50.0f;
        } else if (blockEntity instanceof dev.devce.rocketnautics.content.blocks.RCSThrusterBlockEntity rcs) {
            double rcsMax = 105.0;
            if (level.isClientSide) {
                dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(rcs);
                double y = subLevel != null ? subLevel.logicalPose().position().y : rcs.getBlockPos().getY();
                if (y < 5000) {
                    if (y <= 2000) {
                        rcsMax = 12.0;
                    } else {
                        double factor = (y - 2000.0) / 3000.0;
                        rcsMax = 12.0 + (93.0 * factor);
                    }
                }
            } else {
                dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER.getContaining(level, rcs.getBlockPos());
                double y = ship != null ? ship.logicalPose().position().y : rcs.getBlockPos().getY();
                if (y < 5000) {
                    if (y <= 2000) {
                        rcsMax = 12.0;
                    } else {
                        double factor = (y - 2000.0) / 3000.0;
                        rcsMax = 12.0 + (93.0 * factor);
                    }
                }
            }
            maxThrust = (float) rcsMax;
        } else if (blockEntity instanceof dev.devce.rocketnautics.content.blocks.ThrusterMountBlockEntity tm) {
            maxThrust = (200 * tm.getThrustModifier()) * 50.0f;
        }
        
        if (maxThrust > 0.01f) {
            visualThrottle = Math.max(0f, Math.min(1f, this.currentThrustN / maxThrust));
        }

        // Parity: Ignition ticks logic from RocketThrusterBlockEntity
        if (active && visualThrottle > 0.01f) {
            if (ignitionTicks < 100) ignitionTicks++;
        } else {
            if (ignitionTicks > 0) ignitionTicks--;
        }

        if (level.isClientSide) {
            updateSound();
            
            boolean isSteamWarmup = false;
            if (blockEntity instanceof dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity rt) {
                isSteamWarmup = rt.isSteamMode();
            }

            if (active && visualThrottle > 0.01f) {
                // Register/update plume renderer with dynamic offset and exhaust direction
                dev.devce.rocketnautics.client.render.ExhaustClientRenderer.registerPlume(
                    level,
                    blockEntity.getBlockPos(),
                    this.offset,
                    this.exhaustDir,
                    visualThrottle,
                    (float) this.ignitionTicks,
                    this.engineType == EngineType.RCS,
                    this.engineType == EngineType.ION
                );
                spawnParticles(level);
                if (this.engineType != EngineType.ION && this.engineType != EngineType.RCS) {
                    handleCameraShake(level);
                }
            } else {
                dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(level, blockEntity.getBlockPos());
                if (isSteamWarmup) {
                    spawnSteamParticles(level);
                }
            }
        } else {
            if (active && visualThrottle > 0.01f) {
                if (level.getGameTime() % 10 == 0) {
                    applyWorldEffects(level);
                }
            }
        }
    }

    private void spawnSteamParticles(Level level) {
        RandomSource random = level.getRandom();
        BlockPos pos = getPos();
        Vec3 start = new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z)
                .add(exhaustDir.scale(0.2)); // Slight offset to exit nozzle

        for (int i = 0; i < 2; i++) {
            double rx = start.x + (random.nextDouble() - 0.5) * 0.15;
            double ry = start.y + (random.nextDouble() - 0.5) * 0.15;
            double rz = start.z + (random.nextDouble() - 0.5) * 0.15;

            double speedX = exhaustDir.x * (0.1 + random.nextDouble() * 0.2) + (random.nextDouble() - 0.5) * 0.05;
            double speedY = exhaustDir.y * (0.1 + random.nextDouble() * 0.2) + (random.nextDouble() - 0.5) * 0.05;
            double speedZ = exhaustDir.z * (0.1 + random.nextDouble() * 0.2) + (random.nextDouble() - 0.5) * 0.05;

            level.addParticle(net.minecraft.core.particles.ParticleTypes.CLOUD, rx, ry, rz, speedX, speedY, speedZ);
        }
    }

    private void spawnParticles(Level level) {
        RandomSource random = level.getRandom();
        BlockPos pos = getPos();
        Vec3 start = new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z)
                .add(exhaustDir.scale(0.2)); // Slight offset to exit nozzle

        if (engineType == EngineType.RCS) {
            // Disable legacy particles in favor of custom 3D shader plume
            return;
        }

        // Rocket/Steam logic
        float visualBoost = 1.0f + (throttle * 0.5f);
        int visualPower = (int) (currentThrustN / 50.0f * 14.25f); // Scaled for particle density
        int plumeCount = 1 + (visualPower / 20);
        float baseSpeedMult = 0.8f + (visualPower / 100.0f) * 1.2f;

        for (int i = 0; i < plumeCount; i++) {
            double rx = start.x + (random.nextDouble() - 0.5) * 0.1;
            double ry = start.y + (random.nextDouble() - 0.5) * 0.1;
            double rz = start.z + (random.nextDouble() - 0.5) * 0.1;

            double speedX = exhaustDir.x * (0.3 + random.nextDouble() * 0.4) * visualBoost * baseSpeedMult + (random.nextDouble() - 0.5) * 0.05;
            double speedY = exhaustDir.y * (0.3 + random.nextDouble() * 0.4) * visualBoost * baseSpeedMult + (random.nextDouble() - 0.5) * 0.05;
            double speedZ = exhaustDir.z * (0.3 + random.nextDouble() * 0.4) * visualBoost * baseSpeedMult + (random.nextDouble() - 0.5) * 0.05;

            if (engineType == EngineType.ROCKET) {
                // Legacy flame particles are disabled in favor of the custom 3D GPU-shaded exhaust plume.
                // level.addParticle(RocketParticles.PLASMA.get(), rx, ry, rz, speedX * 1.1, speedY * 1.1, speedZ * 1.1);
                // level.addParticle(RocketParticles.PLUME.get(), rx, ry, rz, speedX, speedY, speedZ);
                
                // if (ignitionTicks >= 40 && random.nextFloat() < 0.3f) {
                //     level.addParticle(RocketParticles.BLUE_FLAME.get(), rx, ry, rz, speedX * 1.3, speedY * 1.3, speedZ * 1.3);
                // }

                // Persistent smoke trail (contrail)
                if (random.nextFloat() < 0.08f) {
                    dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER
                            .getContaining(level, pos);
                    if (ship != null) {
                        dev.ryanhcode.sable.companion.math.Pose3dc pose = ship.logicalPose();
                        dev.ryanhcode.sable.companion.math.Pose3dc lastPose = ship.lastPose();
                        double distSq = pose.position().distanceSquared(lastPose.position());

                        if (distSq > 0.0225) { // Threshold: 0.15 blocks per tick
                            Vec3 smokeLocalPos = new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z)
                                     .add(exhaustDir.scale(2.5));
                            Vec3 smokeWorldPos = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, smokeLocalPos);

                            if (smokeWorldPos.y < 2000.0) {
                                double sSpeedX = (random.nextDouble() - 0.5) * 0.05;
                                double sSpeedY = (random.nextDouble() - 0.5) * 0.05;
                                double sSpeedZ = (random.nextDouble() - 0.5) * 0.05;
                                ThrusterClientHelper.addParticle(RocketParticles.JET_SMOKE.get(),
                                             smokeWorldPos.x, smokeWorldPos.y, smokeWorldPos.z,
                                             sSpeedX, sSpeedY, sSpeedZ);
                            }
                        }
                    }
                }
            } else if (engineType == EngineType.STEAM) {
                level.addParticle(RocketParticles.JET_SMOKE.get(), rx, ry, rz, speedX * 0.5, speedY * 0.5, speedZ * 0.5);
            }
        }

        // Ground smoke and contrail logic (Only for ROCKET)
        if (engineType == EngineType.ROCKET) {
            handleExhaustCollisions(level, start, random, visualPower);
        }
    }

    private void handleExhaustCollisions(Level level, Vec3 start, RandomSource random, int visualPower) {
        double maxSearchDist = 15.0 + (visualPower / 10.0);
        Vec3 end = start.add(exhaustDir.scale(maxSearchDist));

        Level clipLevel = ThrusterClientHelper.getClientLevel();
        if (clipLevel == null) {
            clipLevel = level;
        }
        Vec3 worldStart = start;
        Vec3 worldEnd = end;

        dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER
                .getContaining(level, getPos());
        if (ship != null) {
            worldStart = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, start);
            worldEnd = dev.ryanhcode.sable.Sable.HELPER.projectOutOfSubLevel(level, end);
        }

        Vec3 currentStart = worldStart;
        net.minecraft.world.phys.BlockHitResult hit = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            hit = clipLevel.clip(new net.minecraft.world.level.ClipContext(
                    currentStart, worldEnd, net.minecraft.world.level.ClipContext.Block.COLLIDER,
                    net.minecraft.world.level.ClipContext.Fluid.NONE,
                    net.minecraft.world.phys.shapes.CollisionContext.empty()));

            if (hit.getType() != net.minecraft.world.phys.HitResult.Type.BLOCK) {
                break;
            }

            BlockPos hitBlockPos = hit.getBlockPos();
            if (ship == null && hitBlockPos.equals(getPos())) {
                currentStart = hit.getLocation().add(exhaustDir.scale(0.1));
            } else {
                break;
            }
        }

        if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
            if (random.nextFloat() < (visualPower / 100.0f)) {
                Vec3 hitPos = hit.getLocation();
                for (int i = 0; i < (1 + visualPower / 40); i++) {
                    Vec3 normal = Vec3.atLowerCornerOf(hit.getDirection().getNormal());
                    Vec3 randomDir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
                    Vec3 spreadDir = randomDir.subtract(normal.scale(randomDir.dot(normal))).normalize();
                    double speed = 0.5 + random.nextDouble() * 1.5;
                    clipLevel.addParticle(RocketParticles.JET_SMOKE.get(), hitPos.x, hitPos.y, hitPos.z,
                            spreadDir.x * speed, spreadDir.y * speed, spreadDir.z * speed);
                }
            }
        }
    }

    private void handleCameraShake(Level level) {
        if (engineType == EngineType.RCS) return;
        float shakeInt = RocketConfig.CLIENT.shakeIntensity.get().floatValue();
        double shakeRad = RocketConfig.CLIENT.shakeRadius.get();
        ThrusterClientHelper.handleCameraShake(getPos(), throttle, shakeRad, shakeInt);
    }

    private void applyWorldEffects(Level level) {
        BlockPos pos = getPos();
        int visualPower = (int) (currentThrustN / 50.0f * 2.85f);
        double reach = 1.0 + (visualPower / 5.0);
        Vec3 start = new Vec3(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
        Vec3 end = start.add(exhaustDir.scale(reach));
        AABB damageArea = new AABB(start, end).inflate(0.5);

        List<LivingEntity> affectedEntities = level.getEntitiesOfClass(LivingEntity.class, damageArea);
        affectedEntities.forEach(entity -> {
            if (entity.isAlive()) {
                entity.hurt(level.damageSources().lava(), (float) (visualPower / 10.0));
                entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 40);
                entity.hurtMarked = true;
            }
        });

        if (engineType == EngineType.RCS) return;

        // Block melting logic
        for (int dist = 1; dist <= 3; dist++) {
            BlockPos targetPos = pos.relative(Direction.getNearest(exhaustDir.x, exhaustDir.y, exhaustDir.z), dist);
            BlockState targetState = level.getBlockState(targetPos);
            if (targetState.isAir()) continue;

            if (targetState.is(RocketBlocks.ENGINE_PIPES.get()) || targetState.is(RocketBlocks.ENGINE_NOZZLE.get())) {
                continue;
            }

            float hardness = targetState.getDestroySpeed(level, targetPos);
            if (hardness < 0 || hardness > 10.0f) break;

            if (level.random.nextInt(100) < (visualPower * 2)) {
                if (targetState.is(Blocks.MAGMA_BLOCK)) {
                    level.setBlock(targetPos, Blocks.LAVA.defaultBlockState(), 3);
                } else if (!targetState.is(Blocks.LAVA) && !targetState.is(Blocks.AIR)) {
                    level.setBlock(targetPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                }
            }
            if (targetState.isCollisionShapeFullBlock(level, targetPos)) break;
        }
    }

    public void applyPhysicsForce(RigidBodyHandle handle, double deltaTime) {
        if (!active || currentThrustN <= 0) return;

        // Thrust is opposite to exhaust direction
        Vector3d thrustVector = new Vector3d(-exhaustDir.x, -exhaustDir.y, -exhaustDir.z)
                .mul(currentThrustN);

        BlockPos pos = getPos();
        Vector3d worldPos = new Vector3d(pos.getX() + offset.x, pos.getY() + offset.y, pos.getZ() + offset.z);
        
        handle.applyImpulseAtPoint(worldPos, thrustVector.mul(deltaTime));
    }

    protected void updateSound() {
        if (getWorld().isClientSide) {
            if (!active || throttle < 0.01f) {
                ThrusterClientHelper.stopSound(this);
                return;
            }
            if (soundInstance == null) {
                if (blockEntity instanceof IThruster thruster) {
                    ThrusterClientHelper.startSound(thruster, this);
                }
            }
        }
    }

    @Override
    public void unload() {
        super.unload();
        if (getWorld() != null && getWorld().isClientSide) {
            ThrusterClientHelper.stopSound(this);
            dev.devce.rocketnautics.client.render.ExhaustClientRenderer.removePlume(getWorld(), getPos());
        }
    }

    @Override
    public BehaviourType<?> getType() {
        return TYPE;
    }

    public float getThrottle() { return throttle; }
    public boolean isActive() { return active; }
    public int getIgnitionTicks() { return ignitionTicks; }
}
