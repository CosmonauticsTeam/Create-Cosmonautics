package dev.devce.rocketnautics.content.blocks;

import com.simibubi.create.foundation.blockEntity.SmartBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import com.simibubi.create.foundation.blockEntity.behaviour.CenteredSideValueBoxTransform;
import com.simibubi.create.foundation.blockEntity.behaviour.scrollValue.ScrollValueBehaviour;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.registry.RocketParticles;
import dev.ryanhcode.sable.api.block.BlockEntitySubLevelActor;
import dev.ryanhcode.sable.api.physics.handle.RigidBodyHandle;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

import java.util.List;
import java.util.UUID;

public class BoosterThrusterBlockEntity extends AbstractThrusterBlockEntity {

    private static final long FUEL_SCAN_CACHE_TICKS = 5L;

    public ScrollValueBehaviour thrustPower;
    public ThrustBehaviour thrust;

    @Override
    public int getWarmupTime() {
        return 40;
    }

    @Override
    public ScrollValueBehaviour getThrustPower() {
        return thrustPower;
    }

    public BoosterThrusterBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        thrust = new ThrustBehaviour(this)
                .withType(ThrustBehaviour.EngineType.ROCKET)
                .withOffset(new Vec3(0.5, 0.5, 0.5));
        behaviours.add(thrust);

        thrustPower = new ScrollValueBehaviour(
                Component.translatable("gui.rocketnautics.thrust_power"),
                this,
                new CenteredSideValueBoxTransform((state, direction) -> direction != state.getValue(RocketThrusterBlock.FACING))
        );
        int limit = RocketConfig.SERVER.brokenBarrier.get() ? 100 : 20;
        thrustPower.between(1, limit);
        thrustPower.withFormatter(v -> (v * 50) + " N");
        thrustPower.setValue(limit);

        behaviours.add(thrustPower);
    }

    @Override
    public void tick() {
        super.tick();
        if (level == null) return;

        Direction facing = getThrustDirection();
        thrust.withOffset(new Vec3(0.5, 0.5, 0.5).add(facing.getStepX() * 0.5, facing.getStepY() * 0.5, facing.getStepZ() * 0.5));
        thrust.update(
                (float) (thrustPower.getValue() * 50.0f),
                currentlyBurning ? 1.0f : 0.0f,
                new Vec3(facing.getStepX(), facing.getStepY(), facing.getStepZ()),
                isActive()
        );

        // Runtime limit update for "Break Barrier" command
        int targetMax = RocketConfig.SERVER.brokenBarrier.get() ? 100 : 20;
        if (thrustPower.getValue() > targetMax || (targetMax == 100 && thrustPower.getValue() <= 20 && level.getGameTime() % 20 == 0)) {
             thrustPower.between(1, targetMax);
        }

        boolean active = isActive();
        if (!level.isClientSide) {
            if (active != currentlyBurning) {
                currentlyBurning = active;
                sendData();
            }
        }

        if (level.isClientSide()) {
            updateSound();

            if (active) {
                BlockPos fuelPos = findFuelPos();
                if (fuelPos != null) {
                    for (int i = 0; i < 2; i++) {
                        double fx = fuelPos.getX() + level.random.nextDouble();
                        double fy = fuelPos.getY() + level.random.nextDouble();
                        double fz = fuelPos.getZ() + level.random.nextDouble();
                        level.addParticle(ParticleTypes.FLAME, fx, fy, fz, 0, 0, 0);
                    }
                }

                Direction nozzle = getThrustDirection();
                double x = worldPosition.getX() + 0.5 + nozzle.getStepX() * 0.7;
                double y = worldPosition.getY() + 0.5 + nozzle.getStepY() * 0.7;
                double z = worldPosition.getZ() + 0.5 + nozzle.getStepZ() * 0.7;

                RandomSource random = level.getRandom();
                int power = thrustPower.getValue();
                int visualPower = (int)(power * 14.25f);

                Vec3 start = new Vec3(x, y, z);
                double maxSearchDist = 64.0;
                Vec3 end = start.add(nozzle.getStepX() * maxSearchDist, nozzle.getStepY() * maxSearchDist, nozzle.getStepZ() * maxSearchDist);

                Level clipLevel = ThrusterClientHelper.getClientLevel();
                if (clipLevel == null) {
                    clipLevel = level;
                }
                Vec3 worldStart = start;
                Vec3 worldEnd = end;

                dev.ryanhcode.sable.sublevel.SubLevel ship = (dev.ryanhcode.sable.sublevel.SubLevel) dev.ryanhcode.sable.Sable.HELPER
                        .getContaining(level, worldPosition);
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
                    if (ship == null && hitBlockPos.equals(worldPosition)) {
                        currentStart = hit.getLocation().add(nozzle.getStepX() * 0.1, nozzle.getStepY() * 0.1, nozzle.getStepZ() * 0.1);
                    } else {
                        break;
                    }
                }

                double hitDist = maxSearchDist;
                boolean hitBlock = false;
                Vec3 hitPos = worldEnd;
                if (hit != null && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
                    hitDist = worldStart.distanceTo(hit.getLocation());
                    hitBlock = true;
                    hitPos = hit.getLocation();
                }

                // Spawn exhaust particles based on power and distance
                int plumeCount = 1 + (visualPower / 4);
                float boost = 1.0f + (getFlow() * 0.5f);
                float actualSpeedMult = (float)Math.min(1.2f, hitDist / 10.0);

                for (int i = 0; i < plumeCount; i++) {
                    double speedX = nozzle.getStepX() * (2.0 + random.nextDouble() * 2.0) * boost * actualSpeedMult + (random.nextDouble() - 0.5) * 0.4;
                    double speedY = nozzle.getStepY() * (2.0 + random.nextDouble() * 2.0) * boost * actualSpeedMult + (random.nextDouble() - 0.5) * 0.4;
                    double speedZ = nozzle.getStepZ() * (2.0 + random.nextDouble() * 2.0) * boost * actualSpeedMult + (random.nextDouble() - 0.5) * 0.4;

                    // Legacy flame particles are disabled in favor of the custom 3D GPU-shaded exhaust plume.
                    // var particle = (ignitionTicks < getWarmupTime() / 2) ?
                    //         RocketParticles.PLUME.get() :
                    //         RocketParticles.PLASMA.get();
                    // level.addParticle(particle, x, y, z, speedX, speedY, speedZ);
                }

                if (hitBlock && random.nextFloat() < (visualPower / 50.0f)) {
                    for (int i = 0; i < (1 + visualPower / 5); i++) {
                        Vec3 normal = new Vec3(hit.getDirection().getStepX(), hit.getDirection().getStepY(), hit.getDirection().getStepZ());
                        Vec3 randomDir = new Vec3(random.nextDouble() - 0.5, random.nextDouble() - 0.5, random.nextDouble() - 0.5).normalize();
                        Vec3 spreadDir = randomDir.subtract(normal.scale(randomDir.dot(normal))).normalize();
                        double speed = 0.5 + random.nextDouble() * 1.5;
                        clipLevel.addParticle(RocketParticles.JET_SMOKE.get(), hitPos.x, hitPos.y, hitPos.z, spreadDir.x * speed, spreadDir.y * speed, spreadDir.z * speed);
                    }
                }
            }
        }

        if (active) {
            if (ignitionTicks < 100) ignitionTicks++;
        } else {
            if (ignitionTicks > 0) {
                ignitionTicks--;
            }
        }

        if (!level.isClientSide && active) {
            handleHeat();
            consumeFuel();
        }
    }

    private void handleHeat() {
        Direction nozzle = getThrustDirection();
        int power = thrustPower.getValue();
        int visualPower = (int)(power * 2.85f);
        double reach = 1.0 + (visualPower / 5.0);
        Vec3 start = Vec3.atCenterOf(worldPosition).add(nozzle.getStepX() * 0.5, nozzle.getStepY() * 0.5, nozzle.getStepZ() * 0.5);
        Vec3 end = start.add(nozzle.getStepX() * reach, nozzle.getStepY() * reach, nozzle.getStepZ() * reach);
        AABB damageArea = new AABB(start, end).inflate(0.5);

        List<LivingEntity> affectedEntities = level.getEntitiesOfClass(LivingEntity.class, damageArea);
        affectedEntities.forEach(entity -> {
            if (entity.isAlive()) {
                double pushStrength = (visualPower / 150.0);
                entity.push(nozzle.getStepX() * pushStrength, nozzle.getStepY() * pushStrength, nozzle.getStepZ() * pushStrength);
                if (!level.isClientSide) {
                    entity.hurtMarked = true;
                }
            }
        });

        if (!level.isClientSide && level.getGameTime() % 10 == 0) {
            for (int dist = 1; dist <= 3; dist++) {
                BlockPos targetPos = worldPosition.relative(nozzle, dist);
                BlockState targetState = level.getBlockState(targetPos);
                if (targetState.isAir()) continue;
                float hardness = targetState.getDestroySpeed(level, targetPos);
                if (hardness < 0 || hardness > 10.0f) break;
                if (level.random.nextInt(100) < (visualPower * 2)) {
                    if (targetState.is(Blocks.LAVA)) break;
                    if (targetState.is(Blocks.MAGMA_BLOCK)) {
                        level.setBlock(targetPos, Blocks.LAVA.defaultBlockState(), 3);
                    } else {
                        level.setBlock(targetPos, Blocks.MAGMA_BLOCK.defaultBlockState(), 3);
                    }
                }
                if (targetState.isCollisionShapeFullBlock(level, targetPos)) break;
            }

            affectedEntities.forEach(entity -> {
                if (entity.isAlive()) {
                    entity.hurt(level.damageSources().lava(), (float) (visualPower / 10.0));
                    entity.setRemainingFireTicks(entity.getRemainingFireTicks() + 40);
                }
            });
        }
    }

    @Override
    public void sable$physicsTick(ServerSubLevel serverSubLevel, RigidBodyHandle handle, double deltaTime) {
        if (!isActive()) return;
        Direction facing = getThrustDirection();
        Direction pushDirection = facing.getOpposite();
        double currentThrust = thrustPower.getValue() * 50.0;
        Vector3d thrustVector = new Vector3d(pushDirection.getStepX() * currentThrust, pushDirection.getStepY() * currentThrust, pushDirection.getStepZ() * currentThrust);

        Vector3d forcePos = new Vector3d(
            worldPosition.getX() + 0.5 + facing.getStepX() * 0.5,
            worldPosition.getY() + 0.5 + facing.getStepY() * 0.5,
            worldPosition.getZ() + 0.5 + facing.getStepZ() * 0.5
        );
        handle.applyImpulseAtPoint(forcePos, thrustVector.mul(deltaTime));
    }

    @Override
    public void remove() {
        super.remove();
        if (level != null && level.isClientSide) {
            ThrusterClientHelper.stopSound(this);
        }
    }

    private void updateSound() {
        if (level.isClientSide) {
            if (!isActive()) {
                ThrusterClientHelper.stopSound(this);
            } else if (soundInstance == null) {
                ThrusterClientHelper.startSound(this, this);
            }
        }
    }

    private boolean currentlyBurning = false;
    private boolean ignited = false;
    private boolean isSpent = false;
    private int fuelTicks = 0;
    private BlockPos cachedFuelPos = null;
    private long cachedFuelPosTick = Long.MIN_VALUE;
    private boolean fuelCacheValid = false;
    private boolean computerActive = false;

    public boolean isActive() {
        if (level != null && level.isClientSide) return currentlyBurning;

        if (isSpent) return false;

        if (ignited && fuelTicks > 0) return true;

        if (!ignited) {
            boolean redstonePowered = getBlockState().getValue(BoosterThrusterBlock.POWERED);
            if ((redstonePowered || computerActive) && hasSolidFuelBehind()) {
                ignited = true;
                consumeSolidFuelBlock();
                notifyUpdate();
                return true;
            }
            return false;
        }

        if (ignited && fuelTicks <= 0) {
            BlockPos nextFuel = findFuelPos();
            if (nextFuel != null) {
                consumeSolidFuelBlock();
                notifyUpdate();
                return true;
            } else {
                isSpent = true;
                notifyUpdate();
                return false;
            }
        }

        return false;
    }

    private boolean hasSolidFuelBehind() {
        return findFuelPos() != null;
    }

    private BlockPos findFuelPos() {
        if (level != null) {
            long gameTime = level.getGameTime();
            if (fuelCacheValid && gameTime - cachedFuelPosTick <= FUEL_SCAN_CACHE_TICKS) {
                return cachedFuelPos;
            }
        }

        BlockPos scannedFuelPos = scanFuelPos();
        cachedFuelPos = scannedFuelPos;
        fuelCacheValid = true;
        cachedFuelPosTick = level != null ? level.getGameTime() : Long.MIN_VALUE;
        return scannedFuelPos;
    }

    private BlockPos scanFuelPos() {
        Direction nozzle = getThrustDirection();
        Direction back = nozzle.getOpposite();

        Direction axis1;
        Direction axis2;

        if (back.getAxis() == Direction.Axis.Y) {
            axis1 = Direction.NORTH;
            axis2 = Direction.EAST;
        } else if (back.getAxis() == Direction.Axis.X) {
            axis1 = Direction.UP;
            axis2 = Direction.NORTH;
        } else {
            axis1 = Direction.UP;
            axis2 = Direction.EAST;
        }

        BlockPos furthestInChain = null;

        for (int dist = 1; dist <= 64; dist++) {
            BlockPos layerCenter = worldPosition.relative(back, dist);
            boolean foundInLayer = false;
            BlockPos lastFoundInLayer = null;

            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    BlockPos fuelPos = layerCenter.relative(axis1, i).relative(axis2, j);
                    BlockState state = level.getBlockState(fuelPos);

                    if (isBlockCombustible(state)) {
                        foundInLayer = true;
                        lastFoundInLayer = fuelPos;
                    }
                }
            }

            if (foundInLayer) {
                furthestInChain = lastFoundInLayer;
            } else {
                break;
            }
        }

        return furthestInChain;
    }

    private boolean isBlockCombustible(BlockState state) {
        return state.is(Blocks.COAL_BLOCK);
    }

    private void consumeSolidFuelBlock() {
        BlockPos fuelPos = findFuelPos();
        if (fuelPos != null) {
            level.setBlock(fuelPos, Blocks.AIR.defaultBlockState(), 3);
            fuelTicks = 200;
            invalidateFuelCache();
        }
    }

    private void invalidateFuelCache() {
        fuelCacheValid = false;
        cachedFuelPos = null;
        cachedFuelPosTick = Long.MIN_VALUE;
    }

    @Override
    protected void write(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.write(tag, registries, clientPacket);
        tag.putInt("Fuel", fuelTicks);
        tag.putBoolean("Burning", currentlyBurning);
        tag.putBoolean("Ignited", ignited);
        tag.putBoolean("Spent", isSpent);
        tag.putBoolean("ComputerActive", computerActive);
        tag.putInt("FuelTicks", fuelTicks);
    }

    @Override
    protected void read(CompoundTag tag, HolderLookup.Provider registries, boolean clientPacket) {
        super.read(tag, registries, clientPacket);
        fuelTicks = tag.getInt("Fuel");
        currentlyBurning = tag.getBoolean("Burning");
        ignited = tag.getBoolean("Ignited");
        isSpent = tag.getBoolean("Spent");
        computerActive = tag.getBoolean("ComputerActive");
        fuelTicks = tag.getInt("FuelTicks");
        invalidateFuelCache();
    }

    private void consumeFuel() {
        if (fuelTicks > 0) {
            fuelTicks--;
        }
    }

    public Direction getThrustDirection() {
        return getBlockState().getValue(RocketThrusterBlock.FACING);
    }

    @Override
    public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
        tooltip.add(Component.literal("    ").append(Component.translatable(getBlockState().getBlock().getDescriptionId()).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  Engine ID: ")
                .append(Component.literal(String.valueOf(getPeripheralId())).withStyle(net.minecraft.ChatFormatting.GOLD)));

        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.status")).append(": ")
                .append(isActive() ? Component.translatable("rocketnautics.goggles.active").withStyle(net.minecraft.ChatFormatting.GREEN) :
                        (isSpent ? Component.translatable("rocketnautics.goggles.spent").withStyle(net.minecraft.ChatFormatting.DARK_GRAY) :
                                   Component.translatable("rocketnautics.goggles.inactive").withStyle(net.minecraft.ChatFormatting.RED))));

        int power = thrustPower.getValue();
        tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.thrust")).append(": ")
                .append(Component.literal(power * 50 + " N").withStyle(net.minecraft.ChatFormatting.GOLD)));

        if (ignited && fuelTicks > 0) {
            tooltip.add(Component.literal("  ").append(Component.translatable("rocketnautics.goggles.burn_time")).append(": ")
                    .append(Component.literal((fuelTicks / 20) + "s").withStyle(net.minecraft.ChatFormatting.AQUA)));
        }

        return true;
    }

    public boolean isIgnited() { return ignited; }
    public boolean isSpent() { return isSpent; }
    public int getFuelTicks() { return fuelTicks; }

    @Override
    public void setActive(boolean active) {
        this.computerActive = active;
        notifyUpdate();
    }

    @Override
    public void setThrottle(float throttle) {
    }

    @Override
    public void setGimbal(double pitch, double yaw) {
    }

    @Override
    public void writeValue(String key, double value) {
        if ("throttle".equals(key) || "thrust".equals(key)) {
            setActive(value > 0);
        }
    }

    @Override
    public void writeValues(String key, double... values) {
    }

    @Override
    public float getFlow() {
        return isActive() ? 1.0f : 0.0f;
    }

    @Override
    public String getPeripheralType() {
        return "booster";
    }
}
