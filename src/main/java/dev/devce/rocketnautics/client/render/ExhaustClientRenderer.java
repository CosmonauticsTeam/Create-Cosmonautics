package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.blocks.AbstractThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.EngineNozzleBlockEntity;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.ThrustBehaviour;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3d;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class ExhaustClientRenderer {

    private static final Map<PlumeKey, PlumeInfo> ACTIVE_PLUMES = new ConcurrentHashMap<>();

    public static class PlumeKey {
        public final Level level;
        public final BlockPos pos;

        public PlumeKey(Level level, BlockPos pos) {
            this.level = level;
            this.pos = pos;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PlumeKey plumeKey = (PlumeKey) o;
            return Objects.equals(level, plumeKey.level) && Objects.equals(pos, plumeKey.pos);
        }

        @Override
        public int hashCode() {
            return Objects.hash(level, pos);
        }
    }

    public static class PlumeInfo {
        public final Level level;
        public final BlockPos pos;
        public Vec3 offset;
        public Vec3 exhaustDir;
        public float targetThrottle;
        public float currentThrottle;
        public float ignitionTicks;
        public final boolean isRCS;
        public final boolean isIon;
        public long lastUpdateTime;

        public PlumeInfo(Level level, BlockPos pos, Vec3 offset, Vec3 exhaustDir, float targetThrottle, float ignitionTicks, boolean isRCS, boolean isIon) {
            this.level = level;
            this.pos = pos;
            this.offset = offset;
            this.exhaustDir = exhaustDir;
            this.targetThrottle = targetThrottle;
            this.currentThrottle = isRCS ? 0.0f : targetThrottle;
            this.ignitionTicks = ignitionTicks;
            this.isRCS = isRCS;
            this.isIon = isIon;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        public PlumeInfo(Level level, BlockPos pos, Vec3 offset, Vec3 exhaustDir, float targetThrottle, float ignitionTicks, boolean isRCS) {
            this(level, pos, offset, exhaustDir, targetThrottle, ignitionTicks, isRCS, false);
        }

        public void update(Vec3 offset, Vec3 exhaustDir, float targetThrottle, float ignitionTicks) {
            this.offset = offset;
            this.exhaustDir = exhaustDir;
            this.targetThrottle = targetThrottle;
            this.ignitionTicks = ignitionTicks;
        }
    }

    public static class RenderablePlume {
        public final PlumeInfo plume;
        public final Vec3 worldPos;
        public final Vec3 worldExhaustDir;
        public final double distanceSq;

        public RenderablePlume(PlumeInfo plume, Vec3 worldPos, Vec3 worldExhaustDir, double distanceSq) {
            this.plume = plume;
            this.worldPos = worldPos;
            this.worldExhaustDir = worldExhaustDir;
            this.distanceSq = distanceSq;
        }
    }

    /**
     * Registers or updates an active engine plume to be rendered in the level translucent stage.
     */
    public static void registerPlume(Level level, BlockPos pos, Vec3 offset, Vec3 exhaustDir, float throttle, float ignitionTicks, boolean isRCS, boolean isIon) {
        if (level == null || pos == null) return;
        PlumeKey key = new PlumeKey(level, pos);
        PlumeInfo existing = ACTIVE_PLUMES.get(key);
        if (existing != null) {
            existing.update(offset, exhaustDir, throttle, ignitionTicks);
        } else {
            ACTIVE_PLUMES.put(key, new PlumeInfo(level, pos, offset, exhaustDir, throttle, ignitionTicks, isRCS, isIon));
        }
    }

    public static void registerPlume(Level level, BlockPos pos, Vec3 offset, Vec3 exhaustDir, float throttle, float ignitionTicks, boolean isRCS) {
        registerPlume(level, pos, offset, exhaustDir, throttle, ignitionTicks, isRCS, false);
    }

    /**
     * Removes an engine plume from the active rendering list.
     */
    public static void removePlume(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        PlumeKey key = new PlumeKey(level, pos);
        PlumeInfo existing = ACTIVE_PLUMES.get(key);
        if (existing != null) {
            if (existing.isRCS) {
                existing.targetThrottle = 0.0f;
            } else {
                ACTIVE_PLUMES.remove(key);
            }
        }
    }

    /**
     * Clears all tracked plumes.
     */
    public static void clear() {
        ACTIVE_PLUMES.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE_PLUMES.isEmpty()) return;

        long currentTime = System.currentTimeMillis();
        Iterator<Map.Entry<PlumeKey, PlumeInfo>> iterator = ACTIVE_PLUMES.entrySet().iterator();

        while (iterator.hasNext()) {
            Map.Entry<PlumeKey, PlumeInfo> entry = iterator.next();
            PlumeInfo plume = entry.getValue();

            BlockEntity be = plume.level.getBlockEntity(plume.pos);
            if (be == null) {
                iterator.remove();
                continue;
            }

            float dt = Math.min((currentTime - plume.lastUpdateTime) / 1000.0f, 0.1f);
            plume.lastUpdateTime = currentTime;

            if (plume.isRCS) {
                if (plume.currentThrottle < plume.targetThrottle) {
                    plume.currentThrottle = Math.min(plume.targetThrottle, plume.currentThrottle + dt * 8.5f);
                } else if (plume.currentThrottle > plume.targetThrottle) {
                    plume.currentThrottle = Math.max(0.0f, plume.currentThrottle - dt * 4.5f);
                }

                if (plume.targetThrottle <= 0.001f && plume.currentThrottle <= 0.001f) {
                    iterator.remove();
                    continue;
                }
            } else {
                plume.currentThrottle = plume.targetThrottle;
            }
        }

        if (ACTIVE_PLUMES.isEmpty()) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        double camX = cameraPos.x;
        double camY = cameraPos.y;
        double camZ = cameraPos.z;

        PoseStack ms = event.getPoseStack();

        // 1. Gather all active plumes and compute their absolute world positions
        List<RenderablePlume> rawList = new ArrayList<>();

        for (Map.Entry<PlumeKey, PlumeInfo> entry : ACTIVE_PLUMES.entrySet()) {
            PlumeKey key = entry.getKey();
            PlumeInfo plume = entry.getValue();

            BlockEntity be = plume.level.getBlockEntity(plume.pos);
            if (be == null) {
                ACTIVE_PLUMES.remove(key);
                continue;
            }

            Vec3 worldPos;
            Vec3 worldExhaustDir;

            // Offset adjustment to fit the physical model nozzle exit planes
            Vec3 adjustedOffset = plume.offset;
            if (plume.isRCS) {
                // Shift the starting point 0.18 blocks back (inward) to sit exactly on the RCS nozzle face
                adjustedOffset = plume.offset.subtract(plume.exhaustDir.scale(0.18));
            }

            dev.ryanhcode.sable.sublevel.ClientSubLevel subLevel = dev.ryanhcode.sable.Sable.HELPER.getContainingClient(be);
            if (subLevel != null) {
                // Engine is inside a moving Sable ship
                dev.ryanhcode.sable.companion.math.Pose3dc renderPose = subLevel.renderPose();

                // Transform local coordinate to absolute world space
                Vector3d localOffsetPos = new Vector3d(
                    plume.pos.getX() + adjustedOffset.x, 
                    plume.pos.getY() + adjustedOffset.y, 
                    plume.pos.getZ() + adjustedOffset.z
                );
                Vector3d projectedWorldPos = renderPose.transformPosition(localOffsetPos, new Vector3d());
                worldPos = new Vec3(projectedWorldPos.x, projectedWorldPos.y, projectedWorldPos.z);

                // Transform normal direction to absolute world space
                Vector3d localDir = new Vector3d(plume.exhaustDir.x, plume.exhaustDir.y, plume.exhaustDir.z);
                Vector3d projectedDir = renderPose.transformNormal(localDir, new Vector3d());
                worldExhaustDir = new Vec3(projectedDir.x, projectedDir.y, projectedDir.z);
            } else {
                // Static engine in main world
                worldPos = new Vec3(plume.pos.getX() + adjustedOffset.x, plume.pos.getY() + adjustedOffset.y, plume.pos.getZ() + adjustedOffset.z);
                worldExhaustDir = plume.exhaustDir;
            }

            rawList.add(new RenderablePlume(plume, worldPos, worldExhaustDir, 0));
        }

        // 2. Cluster nearby plumes together
        List<PlumeCluster> clusters = new ArrayList<>();
        boolean mergeEnabled = dev.devce.rocketnautics.RocketConfig.CLIENT.enablePlumeMerging.get();
        double mergeRadius = dev.devce.rocketnautics.RocketConfig.CLIENT.plumeMergeRadius.get();
        double mergeRadiusSqr = mergeRadius * mergeRadius;

        for (RenderablePlume plume : rawList) {
            boolean added = false;
            if (mergeEnabled && !plume.plume.isIon && !plume.plume.isRCS) {
                for (PlumeCluster cluster : clusters) {
                    if (!cluster.isRCS && !cluster.isIon) {
                        // Ensure they point in roughly the same direction
                        if (cluster.worldExhaustDir.dot(plume.worldExhaustDir) > 0.95) {
                            // If within config-defined radius
                            if (cluster.getCenter().distanceToSqr(plume.worldPos) < mergeRadiusSqr) {
                                cluster.add(plume);
                                added = true;
                                break;
                            }
                        }
                    }
                }
            }
            if (!added) {
                clusters.add(new PlumeCluster(plume));
            }
        }

        // 3. Prepare final clusters for rendering
        List<RenderableCluster> renderList = new ArrayList<>();
        for (PlumeCluster cluster : clusters) {
            Vec3 center = cluster.getCenter();
            double distSq = center.distanceToSqr(cameraPos);
            float avgThrottle = cluster.totalThrottle / cluster.count;
            
            float weightSum = cluster.count;
            float r = cluster.totalR / weightSum;
            float g = cluster.totalG / weightSum;
            float b = cluster.totalB / weightSum;
            
            float maxVal = Math.max(r, Math.max(g, b));
            if (maxVal > 1.0f) {
                r /= maxVal;
                g /= maxVal;
                b /= maxVal;
            }
            
            renderList.add(new RenderableCluster(center, cluster.worldExhaustDir, avgThrottle, cluster.maxIgnitionTicks, cluster.isRCS(), cluster.isIon, r, g, b, cluster.count, distSq));
        }

        // 4. Sort clusters by distance (furthest first) for proper transparency
        renderList.sort((c1, c2) -> Double.compare(c2.distanceSq, c1.distanceSq));

        // 5. Render the merged clusters
        for (RenderableCluster cluster : renderList) {
            ms.pushPose();

            // Translate matrix to absolute world position relative to the main camera
            double relX = cluster.worldPos.x - camX;
            double relY = cluster.worldPos.y - camY;
            double relZ = cluster.worldPos.z - camZ;
            ms.translate(relX, relY, relZ);

            // ── VECTOR THRUSTER / DIRECTION ROTATION ────────────────────────────
            Quaternionf rot = getPlumeRotation(cluster.worldExhaustDir);
            ms.mulPose(rot);

            Direction direction = Direction.getNearest(cluster.worldExhaustDir.x, cluster.worldExhaustDir.y, cluster.worldExhaustDir.z);
            
            if (cluster.isIon) {
                ExhaustRenderer.renderIonPlume(ms, mc.renderBuffers().bufferSource(), cluster.throttle, direction);
            } else {
                // The scale of the flame increases based on the number of merged engines (square root prevents it from getting too insanely huge)
                float scale = (float) Math.sqrt(cluster.count);
                ExhaustRenderer.renderExhaustPlume(ms, mc.renderBuffers().bufferSource(), cluster.throttle, cluster.ignitionTicks, direction, cluster.isRCS, scale, cluster.r, cluster.g, cluster.b);
            }

            ms.popPose();
        }

        // Explicitly flush buffers to force rendering immediately and override particle depth ordering
        mc.renderBuffers().bufferSource().endBatch(ExhaustRenderer.getExhaustRenderType());
        mc.renderBuffers().bufferSource().endBatch(ExhaustRenderer.getRcsRenderType());
    }

    public static Quaternionf getPlumeRotation(Vec3 exhaustDir) {
        Direction dir = Direction.getNearest(exhaustDir.x, exhaustDir.y, exhaustDir.z);
        Quaternionf baseRot = new Quaternionf();
        switch (dir) {
            case DOWN -> baseRot.identity();
            case UP -> baseRot.rotationX((float) Math.PI);
            case NORTH -> baseRot.rotationX((float) (Math.PI / 2.0));
            case SOUTH -> baseRot.rotationX((float) (-Math.PI / 2.0));
            case WEST -> baseRot.rotationZ((float) (-Math.PI / 2.0));
            case EAST -> baseRot.rotationZ((float) (Math.PI / 2.0));
        }

        Vector3f cardinalStep = new Vector3f(dir.getStepX(), dir.getStepY(), dir.getStepZ());
        Vector3f target = new Vector3f((float) exhaustDir.x, (float) exhaustDir.y, (float) exhaustDir.z).normalize();
        if (cardinalStep.dot(target) < 0.9999f) {
            Quaternionf tilt = new Quaternionf().rotationTo(cardinalStep, target);
            tilt.mul(baseRot);
            return tilt;
        }
        return baseRot;
    }

    public static Vector3f getEngineColor(BlockEntity be) {
        if (be instanceof dev.devce.rocketnautics.content.blocks.BoosterThrusterBlockEntity) {
            return new Vector3f(0.3f, 0.65f, 1.0f); // Sky Blue (like the screenshot)
        } else if (be instanceof dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity) {
            return new Vector3f(0.82f, 0.92f, 1.0f); // White-blue (mostly white, slightly blue)
        } else if (be instanceof dev.devce.rocketnautics.content.blocks.RCSThrusterBlockEntity) {
            return new Vector3f(0.5f, 0.8f, 1.0f); // Ice Blue
        } else if (be instanceof dev.devce.rocketnautics.content.blocks.ion.IonEngineBlockEntity) {
            return new Vector3f(0.18f, 0.95f, 1.0f); // Electric Cyan
        } else if (be instanceof dev.devce.rocketnautics.content.blocks.CreativeThrusterBlockEntity) {
            return new Vector3f(0.9f, 0.1f, 0.9f); // Vibrant Purple/Magenta
        } else if (be instanceof dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity) {
            return new Vector3f(1.0f, 1.0f, 1.0f); // Original (No multiplier tint)
        } else {
            return new Vector3f(1.0f, 1.0f, 1.0f); // Default to original
        }
    }

    /**
     * Ticks the client thrusters to populate the ACTIVE_PLUMES rendering list.
     */
    public static void tickClientThruster(BlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide) return;

        if (be instanceof EngineNozzleBlockEntity nozzle) {
            if (nozzle.smoothedHeat > 0.05f) {
                Direction facing = nozzle.getBlockState().getValue(dev.devce.rocketnautics.content.blocks.EngineNozzleBlock.FACING);
                // ThrusterMount stores the nozzle's FACING as the direction away from the mount.
                Vec3 exhaustDir = new Vec3(facing.step());
                float throttle = Mth.clamp(nozzle.smoothedHeat / 1.5f, 0f, 1f);
                registerPlume(nozzle.getLevel(), nozzle.getBlockPos(), new Vec3(0.5, 0.1, 0.5), exhaustDir, throttle, throttle * 40.0f, false);
            } else {
                removePlume(nozzle.getLevel(), nozzle.getBlockPos());
            }
        }
    }

    public static class PlumeCluster {
        public Vec3 worldPosSum = Vec3.ZERO;
        public final Vec3 worldExhaustDir;
        public final boolean isRCS;
        public final boolean isIon;
        public float totalThrottle = 0;
        public float maxIgnitionTicks = 0;
        public float totalR = 0;
        public float totalG = 0;
        public float totalB = 0;
        public int count = 0;

        public PlumeCluster(RenderablePlume initial) {
            this.worldExhaustDir = initial.worldExhaustDir;
            this.isRCS = initial.plume.isRCS;
            this.isIon = initial.plume.isIon;
            add(initial);
        }

        public void add(RenderablePlume plume) {
            worldPosSum = worldPosSum.add(plume.worldPos);
            totalThrottle += plume.plume.currentThrottle;
            maxIgnitionTicks = Math.max(maxIgnitionTicks, plume.plume.ignitionTicks);
            
            BlockEntity be = plume.plume.level.getBlockEntity(plume.plume.pos);
            Vector3f col = getEngineColor(be);
            
            float weight = 1.0f;
            totalR += col.x * weight;
            totalG += col.y * weight;
            totalB += col.z * weight;
            
            count++;
        }

        public Vec3 getCenter() {
            return worldPosSum.scale(1.0 / count);
        }
        
        public boolean isRCS() {
            return isRCS;
        }
    }

    public static class RenderableCluster {
        public final Vec3 worldPos;
        public final Vec3 worldExhaustDir;
        public final float throttle;
        public final float ignitionTicks;
        public final boolean isRCS;
        public final boolean isIon;
        public final float r, g, b;
        public final int count;
        public final double distanceSq;

        public RenderableCluster(Vec3 worldPos, Vec3 worldExhaustDir, float throttle, float ignitionTicks, boolean isRCS, boolean isIon, float r, float g, float b, int count, double distanceSq) {
            this.worldPos = worldPos;
            this.worldExhaustDir = worldExhaustDir;
            this.throttle = throttle;
            this.ignitionTicks = ignitionTicks;
            this.isRCS = isRCS;
            this.isIon = isIon;
            this.r = r;
            this.g = g;
            this.b = b;
            this.count = count;
            this.distanceSq = distanceSq;
        }
    }
}
