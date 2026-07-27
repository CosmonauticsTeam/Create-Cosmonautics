package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.*;
import dev.devce.rocketnautics.RocketNautics;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import dev.ryanhcode.sable.sublevel.SubLevel;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector3d;
import org.joml.Quaternionf;
import org.joml.Quaterniondc;
import org.jspecify.annotations.Nullable;

import java.util.*;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class ReentryClientRenderer {

    @Nullable
    public static ShaderInstance reentryShader = null;

    private static RenderType reentryRenderType = null;

    public record GreedyFaceQuad(
        Direction face,
        int layerCoord,
        int minU, int maxU,
        int minV, int maxV,
        float cosAngle
    ) {}

    public static RenderType getReentryRenderType() {
        if (reentryRenderType == null) {
            reentryRenderType = RenderType.create(
                "reentry_plasma",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> reentryShader != null ? reentryShader : net.minecraft.client.renderer.GameRenderer.getPositionColorShader()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
            );
        }
        return reentryRenderType;
    }

    public static void clearCache() {
        // No-op
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_WEATHER) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        SubLevelContainer container = SubLevelContainer.getContainer(mc.level);
        if (container == null) return;

        Map<Vec3, Float> heatMap = dev.devce.rocketnautics.client.HeatClientHandler.getHeatMap();
        boolean debug = dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler.reentryDebugEnabled;
        if (heatMap.isEmpty() && !debug) return;

        Camera camera = event.getCamera();
        Vec3 cameraPos = camera.getPosition();
        PoseStack ms = event.getPoseStack();

        if (debug) {
            for (SubLevel sl : container.getAllSubLevels()) {
                if (sl instanceof ClientSubLevel clientShip) {
                    renderShipReentry(ms, mc, clientShip, cameraPos, 1.0f);
                }
            }
        } else {
            for (Map.Entry<Vec3, Float> entry : heatMap.entrySet()) {
                Vec3 heatPos = entry.getKey();
                float intensity = entry.getValue();
                if (intensity <= 0.05f) continue;

                ClientSubLevel ship = null;
                double minDist = 45.0;
                for (SubLevel sl : container.getAllSubLevels()) {
                    if (sl instanceof ClientSubLevel clientShip) {
                        Vector3d pos = clientShip.logicalPose().position();
                        double dist = heatPos.distanceToSqr(pos.x, pos.y, pos.z);
                        if (dist < minDist) {
                            minDist = dist;
                            ship = clientShip;
                        }
                    }
                }

                if (ship != null) {
                    renderShipReentry(ms, mc, ship, cameraPos, intensity);
                }
            }
        }
    }

    private static void renderShipReentry(PoseStack ms, Minecraft mc, ClientSubLevel ship, Vec3 cameraPos, float intensity) {
        var plot = ship.getPlot();
        if (plot == null) return;
        var bounds = plot.getBoundingBox();
        if (bounds == null) return;

        dev.ryanhcode.sable.companion.math.Pose3dc renderPose = ship.renderPose();
        Quaterniondc worldRot = renderPose.orientation();
        Quaternionf invRot = new Quaternionf((float) worldRot.x(), (float) worldRot.y(), (float) worldRot.z(), (float) worldRot.w()).invert();
        Vector3f localFlowDir = new Vector3f(0.0f, 1.0f, 0.0f).rotate(invRot);

        net.minecraft.world.level.Level shipLevel = ship.getLevel();

        int minX = bounds.minX();
        int maxX = bounds.maxX();
        int minY = bounds.minY();
        int maxY = bounds.maxY();
        int minZ = bounds.minZ();
        int maxZ = bounds.maxZ();

        float cx = (minX + maxX + 1) * 0.5f;
        float cy = (minY + maxY + 1) * 0.5f;
        float cz = (minZ + maxZ + 1) * 0.5f;
        Vector3f shipCenter = new Vector3f(cx, cy, cz);
        float shipSize = Math.max(maxX - minX, Math.max(maxY - minY, maxZ - minZ));

        List<GreedyFaceQuad> greedyQuads = computeGreedyMeshes(shipLevel, minX, maxX, minY, maxY, minZ, maxZ, localFlowDir);
        if (greedyQuads.isEmpty()) return;

        // Transform local flow direction to world space for main-world terrain ray cast
        Quaternionf worldQuat = new Quaternionf((float) worldRot.x(), (float) worldRot.y(), (float) worldRot.z(), (float) worldRot.w());
        Vector3f worldFlowDir = new Vector3f(localFlowDir).rotate(worldQuat);
        var rawCenter = renderPose.position();
        Vector3d shipWorldCenter = new Vector3d(rawCenter.x(), rawCenter.y(), rawCenter.z());

        // Limit trail length to avoid clipping through main-world terrain
        float maxTrailLength = computeMaxTrailLength(mc.level, shipWorldCenter, worldFlowDir,
                shipSize * 0.5f, 32.0f);

        if (reentryShader != null) {
            var uTime = reentryShader.getUniform("u_Time");
            if (uTime != null) {
                uTime.set((float) ((System.currentTimeMillis() % 100000L) / 1000.0f));
            }
            var uIntensity = reentryShader.getUniform("u_Intensity");
            if (uIntensity != null) {
                uIntensity.set(intensity);
            }
        }

        VertexConsumer consumer = mc.renderBuffers().bufferSource().getBuffer(getReentryRenderType());

        for (GreedyFaceQuad quad : greedyQuads) {
            double midX = 0, midY = 0, midZ = 0;
            
            // Correct coordinate projection for the merged quad center
            switch (quad.face().getAxis()) {
                case Y: // DOWN, UP -> U=X, V=Z
                    midX = (quad.minU + quad.maxU + 1) * 0.5;
                    midY = quad.layerCoord + 0.5;
                    midZ = (quad.minV + quad.maxV + 1) * 0.5;
                    break;
                case Z: // NORTH, SOUTH -> U=X, V=Y
                    midX = (quad.minU + quad.maxU + 1) * 0.5;
                    midY = (quad.minV + quad.maxV + 1) * 0.5;
                    midZ = quad.layerCoord + 0.5;
                    break;
                case X: // WEST, EAST -> U=Z, V=Y
                    midX = quad.layerCoord + 0.5;
                    midY = (quad.minV + quad.maxV + 1) * 0.5;
                    midZ = (quad.minU + quad.maxU + 1) * 0.5;
                    break;
            }

            Vector3d localCoord = new Vector3d(midX, midY, midZ);
            Vector3d projectedWorldPos = renderPose.transformPosition(new Vector3d(localCoord), new Vector3d());

            ms.pushPose();
            double relX = projectedWorldPos.x - cameraPos.x;
            double relY = projectedWorldPos.y - cameraPos.y;
            double relZ = projectedWorldPos.z - cameraPos.z;
            ms.translate(relX, relY, relZ);

            ms.mulPose(new Quaternionf((float) worldRot.x(), (float) worldRot.y(), (float) worldRot.z(), (float) worldRot.w()));

            renderSingleGreedyQuad(consumer, ms.last().pose(), shipLevel, quad, localFlowDir, intensity, localCoord, shipCenter, shipSize, maxTrailLength);

            ms.popPose();
        }

        // Explicitly flush the buffer to force rendering IMMEDIATELY at this stage (AFTER_WEATHER)
        // This ensures the plasma is drawn on top of translucent particles that rendered in earlier stages.
        mc.renderBuffers().bufferSource().endBatch(getReentryRenderType());
    }

    private static List<GreedyFaceQuad> computeGreedyMeshes(net.minecraft.world.level.Level shipLevel, 
                                                            int minX, int maxX, int minY, int maxY, int minZ, int maxZ, 
                                                            Vector3f localFlowDir) {
        List<GreedyFaceQuad> result = new ArrayList<>();

        for (Direction dir : Direction.values()) {
            Vector3f faceNormal = new Vector3f(dir.step());
            float cosAngle = -faceNormal.dot(localFlowDir);
            if (cosAngle <= 0.1f) continue;

            Direction.Axis axis = dir.getAxis();

            int minL, maxL, minU_b, maxU_b, minV_b, maxV_b;
            switch (axis) {
                case Y: // DOWN, UP -> U=X, V=Z
                    minL = minY; maxL = maxY;
                    minU_b = minX; maxU_b = maxX;
                    minV_b = minZ; maxV_b = maxZ;
                    break;
                case Z: // NORTH, SOUTH -> U=X, V=Y
                    minL = minZ; maxL = maxZ;
                    minU_b = minX; maxU_b = maxX;
                    minV_b = minY; maxV_b = maxY;
                    break;
                case X: // WEST, EAST -> U=Z, V=Y
                default:
                    minL = minX; maxL = maxX;
                    minU_b = minZ; maxU_b = maxZ;
                    minV_b = minY; maxV_b = maxY;
                    break;
            }

            int dimU = maxU_b - minU_b + 1;
            int dimV = maxV_b - minV_b + 1;

            for (int layer = minL; layer <= maxL; layer++) {
                boolean[][] mask = new boolean[dimU][dimV];
                boolean hasAny = false;

                for (int u = 0; u < dimU; u++) {
                    for (int v = 0; v < dimV; v++) {
                        int gx = (axis == Direction.Axis.X) ? layer : (axis == Direction.Axis.Y ? minU_b + u : minU_b + u);
                        int gy = (axis == Direction.Axis.Y) ? layer : (axis == Direction.Axis.Z ? minV_b + v : minV_b + v);
                        int gz = (axis == Direction.Axis.Z) ? layer : (axis == Direction.Axis.Y ? minV_b + v : minU_b + u);
                        
                        // Correct coordinate mapping based on projection:
                        if (axis == Direction.Axis.Y) { gx = minU_b + u; gy = layer; gz = minV_b + v; }
                        else if (axis == Direction.Axis.Z) { gx = minU_b + u; gy = minV_b + v; gz = layer; }
                        else if (axis == Direction.Axis.X) { gx = layer; gy = minV_b + v; gz = minU_b + u; }

                        BlockPos pos = new BlockPos(gx, gy, gz);
                        BlockState state = shipLevel.getBlockState(pos);
                        if (!state.isAir()) {
                            BlockPos adj = pos.relative(dir);
                            if (shipLevel.getBlockState(adj).isAir()) {
                                if (!isShadowed(shipLevel, adj, localFlowDir, minX, maxX, minY, maxY, minZ, maxZ)) {
                                    mask[u][v] = true;
                                    hasAny = true;
                                }
                            }
                        }
                    }
                }

                if (!hasAny) continue;

                for (int v = 0; v < dimV; v++) {
                    for (int u = 0; u < dimU; u++) {
                        if (mask[u][v]) {
                            int width = 1;
                            while (u + width < dimU && mask[u + width][v]) {
                                width++;
                            }

                            int height = 1;
                            boolean canExpandV = true;
                            while (v + height < dimV && canExpandV) {
                                for (int k = 0; k < width; k++) {
                                    if (!mask[u + k][v + height]) {
                                        canExpandV = false;
                                        break;
                                    }
                                }
                                if (canExpandV) height++;
                            }

                            for (int dv = 0; dv < height; dv++) {
                                for (int du = 0; du < width; du++) {
                                    mask[u + du][v + dv] = false;
                                }
                            }

                            int realMinU = minU_b + u;
                            int realMaxU = minU_b + u + width - 1;
                            int realMinV = minV_b + v;
                            int realMaxV = minV_b + v + height - 1;

                            result.add(new GreedyFaceQuad(dir, layer, realMinU, realMaxU, realMinV, realMaxV, cosAngle));
                        }
                    }
                }
            }
        }

        return result;
    }

    private static boolean isShadowed(net.minecraft.world.level.Level shipLevel, BlockPos startAirPos, Vector3f localFlowDir, 
                                      int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        // Wind flows ALONG localFlowDir, so the source of the wind is in -localFlowDir direction.
        Vector3f rayDir = new Vector3f(-localFlowDir.x, -localFlowDir.y, -localFlowDir.z);
        if (rayDir.lengthSquared() < 0.01f) return false;
        rayDir.normalize();

        double x = startAirPos.getX() + 0.5;
        double y = startAirPos.getY() + 0.5;
        double z = startAirPos.getZ() + 0.5;
        
        double step = 0.4;
        double dx = rayDir.x * step;
        double dy = rayDir.y * step;
        double dz = rayDir.z * step;
        
        int dimX = maxX - minX;
        int dimY = maxY - minY;
        int dimZ = maxZ - minZ;
        int maxSteps = (int) (Math.max(dimX, Math.max(dimY, dimZ)) / step) + 5;
        
        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
        
        for (int i = 1; i <= maxSteps; i++) {
            x += dx;
            y += dy;
            z += dz;
            
            // Check if ray exited the ship's bounding box
            if (x < minX || x > maxX + 1 || 
                y < minY || y > maxY + 1 || 
                z < minZ || z > maxZ + 1) {
                return false; 
            }
            
            mpos.set(Math.floor(x), Math.floor(y), Math.floor(z));
            if (mpos.equals(startAirPos)) continue;
            
            BlockState state = shipLevel.getBlockState(mpos);
            // If the ray hits a non-air block that can occlude wind, the face is shadowed!
            if (!state.isAir() && state.isSolidRender(shipLevel, mpos)) { 
                return true;
            }
        }
        return false;
    }

    /**
     * Ray-casts along worldFlowDir from shipWorldCenter in mc.level to find the first solid block.
     * Returns a safe maximum trail length so the plasma tail doesn't clip into terrain.
     */
    private static float computeMaxTrailLength(net.minecraft.world.level.Level mainLevel,
                                               Vector3d shipWorldCenter, Vector3f worldFlowDir,
                                               float startOffset, float absoluteMax) {
        float stepSize = 0.5f;
        int maxSteps = (int) ((absoluteMax + startOffset) / stepSize) + 4;

        double x = shipWorldCenter.x + worldFlowDir.x * startOffset;
        double y = shipWorldCenter.y + worldFlowDir.y * startOffset;
        double z = shipWorldCenter.z + worldFlowDir.z * startOffset;

        BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();

        for (int i = 0; i < maxSteps; i++) {
            x += worldFlowDir.x * stepSize;
            y += worldFlowDir.y * stepSize;
            z += worldFlowDir.z * stepSize;

            mpos.set(x, y, z);
            net.minecraft.world.level.block.state.BlockState state = mainLevel.getBlockState(mpos);
            if (!state.isAir() && state.isSolidRender(mainLevel, mpos)) {
                // Leave 1 block clearance before solid surface so trail fades out just before it
                return Math.max(0.5f, i * stepSize - 1.0f);
            }
        }
        return absoluteMax;
    }

    private static void renderSingleGreedyQuad(VertexConsumer consumer, Matrix4f matrix, 
                                               net.minecraft.world.level.Level level, GreedyFaceQuad quad, 
                                               Vector3f localFlowDir, float intensity,
                                               Vector3d quadCenter, Vector3f shipCenter, float shipSize,
                                               float maxTrailLength) {
        Direction face = quad.face();
        float cosAngle = quad.cosAngle();

        float halfU = (quad.maxU - quad.minU + 1) * 0.5f;
        float halfV = (quad.maxV - quad.minV + 1) * 0.5f;
        float offset = 0.505f; // Increased slightly to definitively prevent Z-fighting with blocks

        Vector3f p1, p2, p3, p4;

        switch (face) {
            case DOWN:
            default:
                p1 = new Vector3f(-halfU, -offset, -halfV);
                p2 = new Vector3f(halfU, -offset, -halfV);
                p3 = new Vector3f(halfU, -offset, halfV);
                p4 = new Vector3f(-halfU, -offset, halfV);
                break;
            case UP:
                p1 = new Vector3f(-halfU, offset, halfV);
                p2 = new Vector3f(halfU, offset, halfV);
                p3 = new Vector3f(halfU, offset, -halfV);
                p4 = new Vector3f(-halfU, offset, -halfV);
                break;
            case NORTH:
                p1 = new Vector3f(halfU, -halfV, -offset);
                p2 = new Vector3f(-halfU, -halfV, -offset);
                p3 = new Vector3f(-halfU, halfV, -offset);
                p4 = new Vector3f(halfU, halfV, -offset);
                break;
            case SOUTH:
                p1 = new Vector3f(-halfU, -halfV, offset);
                p2 = new Vector3f(halfU, -halfV, offset);
                p3 = new Vector3f(halfU, halfV, offset);
                p4 = new Vector3f(-halfU, halfV, offset);
                break;
            case WEST:
                p1 = new Vector3f(-offset, -halfV, -halfU);
                p2 = new Vector3f(-offset, -halfV, halfU);
                p3 = new Vector3f(-offset, halfV, halfU);
                p4 = new Vector3f(-offset, halfV, -halfU);
                break;
            case EAST:
                p1 = new Vector3f(offset, -halfV, halfU);
                p2 = new Vector3f(offset, -halfV, -halfU);
                p3 = new Vector3f(offset, halfV, -halfU);
                p4 = new Vector3f(offset, halfV, halfU);
                break;
        }

        // Smooth alpha fading for slipstreams based on incidence angle
        // Fully visible at cosAngle = 0.5, invisible at cosAngle = 0.1
        float alphaFactor = Math.max(0.0f, Math.min(1.0f, (cosAngle - 0.1f) / 0.4f));
        int alpha = (int) (255 * alphaFactor);

        // 1. MONOLITHIC SHOCK WAVE SHIELD (Frontal Impact)
        // Smoothly fade in the shockwave shield between 0.3 and 0.5 cosAngle
        if (cosAngle >= 0.30f) {
            float shieldAlphaFactor = Math.max(0.0f, Math.min(1.0f, (cosAngle - 0.3f) / 0.2f));
            int shieldAlpha = (int) (255 * shieldAlphaFactor);

            addPlasmaTriangle(consumer, matrix, p1, p2, p3, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 255, 255, 255, shieldAlpha);
            addPlasmaTriangle(consumer, matrix, p1, p3, p4, 0.0f, 0.0f, 1.0f, 0.0f, 0.0f, 0.0f, 255, 255, 255, shieldAlpha);
            
            // Double-sided to ensure visibility from all internal camera angles
            addPlasmaTriangle(consumer, matrix, p1, p3, p2, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 0.0f, 255, 255, 255, shieldAlpha);
            addPlasmaTriangle(consumer, matrix, p1, p4, p3, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f, 255, 255, 255, shieldAlpha);
        }

        // 2. CONTINUOUS AERODYNAMIC SLIPSTREAM FLAME
        // Trail stretches across the ship's length but is capped so it doesn't become absurdly long on huge ships
        // Also capped by maxTrailLength derived from terrain ray cast to prevent clipping into main-world blocks
        float scaledLength = Math.min(40.0f, Math.max(14.0f, shipSize * 0.6f + 10.0f));
        float trailLength = Math.min(scaledLength * intensity * Math.max(0.2f, alphaFactor), maxTrailLength);

        Vector3f faceNormal = new Vector3f(face.step());
        float normalDot = faceNormal.dot(localFlowDir);
        
        // dynamicPush dictates how far the plasma trail is pushed OUTWARDS from the hull
        float dynamicPush = 0.2f + Math.max(0.0f, trailLength * 0.08f);

        // Vector of trail flow
        Vector3f flowVec = new Vector3f(localFlowDir).mul(trailLength);

        // Base trail ends (parallel to flow)
        Vector3f t1 = new Vector3f(p1).add(flowVec);
        Vector3f t2 = new Vector3f(p2).add(flowVec);
        Vector3f t3 = new Vector3f(p3).add(flowVec);
        Vector3f t4 = new Vector3f(p4).add(flowVec);

        // Radial expansion from the central flight axis of the ship.
        // This ensures all adjacent quads, regardless of their face normals, expand seamlessly together,
        // eliminating seams, scales, or gaps at the corners!
        Vector3f out1 = getAxisRadialPush(p1, quadCenter, shipCenter, localFlowDir, faceNormal, dynamicPush);
        Vector3f out2 = getAxisRadialPush(p2, quadCenter, shipCenter, localFlowDir, faceNormal, dynamicPush);
        Vector3f out3 = getAxisRadialPush(p3, quadCenter, shipCenter, localFlowDir, faceNormal, dynamicPush);
        Vector3f out4 = getAxisRadialPush(p4, quadCenter, shipCenter, localFlowDir, faceNormal, dynamicPush);
        
        t1.add(out1);
        t2.add(out2);
        t3.add(out3);
        t4.add(out4);

        addPlasmaQuadTwoSided(consumer, matrix, p1, p2, t2, t1, alpha);
        addPlasmaQuadTwoSided(consumer, matrix, p2, p3, t3, t2, alpha);
        addPlasmaQuadTwoSided(consumer, matrix, p3, p4, t4, t3, alpha);
        addPlasmaQuadTwoSided(consumer, matrix, p4, p1, t1, t4, alpha);
    }

    private static Vector3f getAxisRadialPush(Vector3f p, Vector3d quadCenter, Vector3f shipCenter, Vector3f localFlowDir, Vector3f faceNormal, float dynamicPush) {
        Vector3f absolutePos = new Vector3f((float)quadCenter.x + p.x, (float)quadCenter.y + p.y, (float)quadCenter.z + p.z);
        Vector3f fromCenter = absolutePos.sub(shipCenter);
        // Project onto the plane perpendicular to the flow direction (creating a cylindrical expansion)
        float dotFlow = fromCenter.dot(localFlowDir);
        fromCenter.sub(new Vector3f(localFlowDir).mul(dotFlow));
        
        if (fromCenter.lengthSquared() > 0.0001f) {
            fromCenter.normalize();
        } else {
            fromCenter.set(faceNormal);
        }
        return fromCenter.mul(dynamicPush);
    }

    private static void addPlasmaQuadTwoSided(VertexConsumer consumer, Matrix4f matrix, Vector3f a, Vector3f b, Vector3f bTrail, Vector3f aTrail, int alpha) {
        addPlasmaTriangle(consumer, matrix, a, b, bTrail, 0.0f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, 255, 255, 255, alpha);
        addPlasmaTriangle(consumer, matrix, a, bTrail, aTrail, 0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f, 255, 255, 255, alpha);

        addPlasmaTriangle(consumer, matrix, a, bTrail, b, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f, 255, 255, 255, alpha);
        addPlasmaTriangle(consumer, matrix, a, aTrail, bTrail, 0.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 255, 255, 255, alpha);
    }

    private static void addPlasmaTriangle(VertexConsumer consumer, Matrix4f matrix, 
                                          Vector3f v1, Vector3f v2, Vector3f v3, 
                                          float u1, float v1_uv, float u2, float v2_uv, float u3, float v3_uv,
                                          int r, int g, int b, int a) {
        consumer.addVertex(matrix, v1.x, v1.y, v1.z).setUv(u1, v1_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z).setUv(u2, v2_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z).setUv(u3, v3_uv).setColor(r, g, b, a);
    }
}
