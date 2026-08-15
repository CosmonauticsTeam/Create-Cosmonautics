package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.datafixers.util.Either;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.ColorPalette;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.network.PlanetRenderRequestPayload;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.SimpleTexture;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Stream;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public final class DeepSpaceHandler {

    static @Nullable UniverseDefinition UNIVERSE;
    private static final Int2ObjectAVLTreeMap<IntObjectPair<PreparedTexture>> KNOWN_RENDER_DATA = new Int2ObjectAVLTreeMap<>();
    public static net.minecraft.client.renderer.ShaderInstance planetNormalShader = null;
    public static int debugMoonPhaseOverride = -1;
    private static final Int2BooleanAVLTreeMap AWAITING_SERVER = new Int2BooleanAVLTreeMap();

    // --- VBO: planet meshes built once, drawn each frame with a scaled matrix ---
    private static VertexBuffer SPHERE_VBO;        // unit-sphere (POSITION_TEX_COLOR)
    private static VertexBuffer CUBE_VBO;          // unit-cube   (POSITION_TEX_COLOR)
    private static VertexBuffer SPHERE_ATM_VBO;    // unit-sphere (POSITION_COLOR) for atmosphere/shadow
    private static VertexBuffer CUBE_ATM_VBO;      // unit-cube   (POSITION_COLOR) for atmosphere/shadow
    private static boolean vbosBuilt = false;

    // Shadow dirty-flag: only rebuild per-planet shadow VBO when light direction changes
    // Key: planet id, Value: float[3] last L vector for that planet
    private static final java.util.Map<Integer, VertexBuffer> PLANET_SHADOW_VBOS = new java.util.HashMap<>();
    private static final java.util.Map<Integer, float[]> PLANET_SHADOW_L = new java.util.HashMap<>();
    private static final float SHADOW_L_THRESHOLD = 0.005f;

    private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private static final float FORCEFIELD_DIST = 8;

    private static long receivedUniverseDateTick = -1;
    private static AbsoluteDate receivedUniverseDate;
    private static float receivedUniverseTickrate;

    private static long receivedPositionTick = -1;
    private static final DeepSpacePosition receivedPosition = new DeepSpacePosition();

    private static final ArrayListDeque<Pair<AbsoluteDate, Orbit>> positionPredictions = new ArrayListDeque<>(100);
    private static final DeepSpacePosition nextPrediction = new DeepSpacePosition();
    
    private static int getLocalMinecraftTicks() {
        return Minecraft.getInstance().levelRenderer.getTicks();
    }

    public static void receiveUniverse(UniverseDefinition definition) {
        UNIVERSE = definition;
        receivedPosition.reset();
        nextPrediction.reset();
        receivedPositionTick = -1;
        receivedUniverseDateTick = -1;
        KNOWN_RENDER_DATA.values().forEach(p -> p.right().retire());
        KNOWN_RENDER_DATA.clear();
    }

    public static void clearRenderCache() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        mc.execute(() -> {
            KNOWN_RENDER_DATA.values().forEach(p -> p.right().retire());
            KNOWN_RENDER_DATA.clear();
            AWAITING_SERVER.clear();
            // Close per-planet shadow VBOs so they get rebuilt on next render
            PLANET_SHADOW_VBOS.values().forEach(VertexBuffer::close);
            PLANET_SHADOW_VBOS.clear();
            PLANET_SHADOW_L.clear();
        });
    }

    /** Build (or rebuild) the static planet VBOs. Called once on first render. */
    private static void ensureVBOs() {
        if (vbosBuilt) return;
        SPHERE_VBO     = uploadVBO(SPHERE_VERTICES,      VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        CUBE_VBO       = uploadVBO(CUBE_VERTICES,        VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        SPHERE_ATM_VBO = uploadColorVBO(SPHERE_VERTICES, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        CUBE_ATM_VBO   = uploadColorVBO(CUBE_VERTICES,   VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        vbosBuilt = true;
    }

    /** Upload a CachedVertex[] array (POSITION_TEX_COLOR) into a new VertexBuffer. */
    private static VertexBuffer uploadVBO(CachedVertex[] verts, VertexFormat.Mode mode, VertexFormat format) {
        try {
            var builder = Tesselator.getInstance().begin(mode, format);
            for (CachedVertex v : verts) {
                builder.addVertex(v.x, v.y, v.z).setColor(1f,1f,1f,1f).setUv(v.u, v.v);
            }
            var buf = builder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(buf);
            VertexBuffer.unbind();
            return vbo;
        } catch (Exception e) {
            return null;
        }
    }

    /** Upload a CachedVertex[] as POSITION_COLOR (no UV, white color baked in for ATM/shadow use). */
    private static VertexBuffer uploadColorVBO(CachedVertex[] verts, VertexFormat.Mode mode, VertexFormat format) {
        try {
            var builder = Tesselator.getInstance().begin(mode, format);
            for (CachedVertex v : verts) {
                builder.addVertex(v.x, v.y, v.z).setColor(1f,1f,1f,1f);
            }
            var buf = builder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.STATIC);
            vbo.bind();
            vbo.upload(buf);
            VertexBuffer.unbind();
            return vbo;
        } catch (Exception e) {
            return null;
        }
    }

    /** Upload a shadow mesh (POSITION_COLOR with per-vertex light color) into a VertexBuffer. */
    private static VertexBuffer uploadShadowVBO(CachedShadowVertex[] verts, float shadowScale, Vector3D L) {
        try {
            var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            for (CachedShadowVertex sv : verts) {
                long c = computeColor(sv.nx, sv.ny, sv.nz, L, sv.gx, sv.gy);
                builder.addVertex(sv.ux * shadowScale, sv.uy * shadowScale, sv.uz * shadowScale)
                       .setColor((int)((c >> 24) & 255), (int)((c >> 16) & 255), (int)((c >> 8) & 255), (int)(c & 255));
            }
            var buf = builder.buildOrThrow();
            VertexBuffer vbo = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
            vbo.bind();
            vbo.upload(buf);
            VertexBuffer.unbind();
            return vbo;
        } catch (Exception e) {
            return null;
        }
    }

    /** Draw a VBO with the given model matrix (scale + transform) and shader. */
    private static void drawVBO(VertexBuffer vbo, Matrix4f modelMatrix, net.minecraft.client.renderer.ShaderInstance shader) {
        if (vbo == null || shader == null) return;
        vbo.bind();
        vbo.drawWithShader(modelMatrix, RenderSystem.getProjectionMatrix(), shader);
        VertexBuffer.unbind();
    }

    /** Draw an already-bound VBO with the given model matrix and shader (no bind/unbind overhead). */
    private static void drawBoundVBO(VertexBuffer vbo, Matrix4f modelMatrix, net.minecraft.client.renderer.ShaderInstance shader) {
        if (vbo == null || shader == null) return;
        vbo.drawWithShader(modelMatrix, RenderSystem.getProjectionMatrix(), shader);
    }

    private static class PlanetDistanceEntry {
        CubePlanet planet;
        Vector3D pos;
        double distSq;
    }

    private static final List<PlanetDistanceEntry> PLANET_ENTRIES = new ArrayList<>();
    private static final Comparator<PlanetDistanceEntry> PLANET_COMPARATOR = (a, b) -> Double.compare(b.distSq, a.distSq);

    /** Close all static VBOs (called on resource reload / game close). */
    public static void closeVBOs() {
        if (SPHERE_VBO     != null) { SPHERE_VBO.close();     SPHERE_VBO     = null; }
        if (CUBE_VBO       != null) { CUBE_VBO.close();       CUBE_VBO       = null; }
        if (SPHERE_ATM_VBO != null) { SPHERE_ATM_VBO.close(); SPHERE_ATM_VBO = null; }
        if (CUBE_ATM_VBO   != null) { CUBE_ATM_VBO.close();   CUBE_ATM_VBO   = null; }
        PLANET_SHADOW_VBOS.values().forEach(VertexBuffer::close);
        PLANET_SHADOW_VBOS.clear();
        PLANET_SHADOW_L.clear();
        vbosBuilt = false;
    }
    
    public static void receiveUniverseTime(long universeTicks, float serverTickRate) {
        receivedUniverseDateTick = getLocalMinecraftTicks();
        receivedUniverseDate = DeepSpaceHelper.getDateByTicks(universeTicks);
        receivedUniverseTickrate = serverTickRate;
    }

    public static @Nullable AbsoluteDate getPredictedUniverseDate(float partial) {
        if (receivedUniverseDateTick == -1) return null;
        return new AbsoluteDate(receivedUniverseDate, (getLocalMinecraftTicks() - receivedUniverseDateTick + partial) * receivedUniverseTickrate / 400f);
    }

    public static boolean hasReceivedPosition() {
        return receivedPositionTick != -1;
    }

    public static void receivePosition(FriendlyByteBuf buf) {
        if (UNIVERSE != null) {
            receivedPositionTick = getLocalMinecraftTicks();
            receivedPosition.read(buf, UNIVERSE);
            positionPredictions.clear();
            receivedPosition.copyTo(nextPrediction);
        }
    }

    public static @Nullable UniverseDefinition getUniverse() {
        return UNIVERSE;
    }

    public static DeepSpacePosition getReceivedPosition() {
        return receivedPosition;
    }

    public static AbsoluteDate getRenderDate(float partial) {
        return getRenderDate(getLocalMinecraftTicks(), partial);
    }

    public static AbsoluteDate getRenderDate(long ticksSince, float partial) {
        return receivedPosition.getLocalUniverseTime().shiftedBy(receivedPosition.getTimescale() * ((double) partial + (ticksSince - receivedPositionTick)) / 20);
    }

    public static Iterator<Vector3D> getPositionPrediction(Frame frame, int upTo) {
        if (UNIVERSE == null) return Collections.emptyIterator();
        AbsoluteDate renderDate = getRenderDate(0);
//        while (positionPredictions.size() > 1 && positionPredictions.get(1).left().isBefore(renderDate)) {
//            positionPredictions.removeFirst();
//        }
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < upTo && index < 10000;
            }

            @Override
            public Vector3D next() {
                while (positionPredictions.size() <= index) {
                    KeplerianOrbit orbit = nextPrediction.getCurrentOrbit();
                    TimeStampedPVCoordinates coords = orbit.getPVCoordinates();
                    if (coords.getDate().isAfterOrEqualTo(renderDate)) {
                        positionPredictions.addLast(Pair.of(coords.getDate(), nextPrediction.getOrbit()));
                    }
                    double correctedAngularVelocity = orbit.getEccentricAnomalyDot() / Mth.lerp(orbit.getE() * orbit.getE(), 1, 1 - coords.getVelocity().normalize().crossProduct(coords.getAcceleration().normalize()).getNorm() / 2);
                    int lookaheadTicks = (int) (20 * Math.toRadians(RocketConfig.CLIENT.orbitPredictionAngularThreshold.get()) / correctedAngularVelocity);
                    nextPrediction.setTimescale(Math.max(1, lookaheadTicks));
                    nextPrediction.propagate(UNIVERSE);
                }
                Pair<AbsoluteDate, Orbit> pair = positionPredictions.get(index);
                index++;
                return pair.right().getPosition(pair.left(), frame);
            }
        };
    }

    public static Stream<AbsoluteDate> getPredictionDates(int maximum) {
        return positionPredictions.stream().map(Pair::left).limit(maximum);
    }

    public static Iterator<Orbit> getPredictionOrbits() {
        return new Iterator<>() {
            int index = 0;
            Orbit previous = null;
            Orbit foundNext = null;

            private void ensureNext() {
                while (foundNext == null && index < positionPredictions.size()) {
                    Orbit find = positionPredictions.get(index).right();
                    if (find != previous) {
                        foundNext = find;
                        previous = find;
                    }
                    index++;
                }
            }

            @Override
            public boolean hasNext() {
                ensureNext();
                return foundNext != null;
            }

            @Override
            public Orbit next() {
                ensureNext();
                Orbit ret = foundNext;
                foundNext = null;
                return ret;
            }
        };
    }

    private static final int MAX_CACHED_PLANET_TEXTURES = 32;

    public static void receiveRenderData(int id, Either<ColorPalette, ResourceLocation> data, int powerScale) {
        // Evict oldest texture if cache capacity exceeded to prevent VRAM accumulation
        if (KNOWN_RENDER_DATA.size() >= MAX_CACHED_PLANET_TEXTURES && !KNOWN_RENDER_DATA.containsKey(id)) {
            int firstKey = KNOWN_RENDER_DATA.firstIntKey();
            IntObjectPair<PreparedTexture> evicted = KNOWN_RENDER_DATA.remove(firstKey);
            if (evicted != null && evicted.right() != null) {
                evicted.right().retire();
            }
        }

        KNOWN_RENDER_DATA.put(id, IntObjectPair.of(powerScale, data.<PreparedTexture>map(arr -> DeepSpaceTexture.construct(id, arr), res -> {
            Minecraft.getInstance().getTextureManager().register(res, new SimpleTexture(res));
            return new PreparedTexture() {
                @Override
                public ResourceLocation getId() {
                    return res;
                }
                @Override
                public void retire() {
                    // Do not unregister static shared assets
                }
            };
        })));
        AWAITING_SERVER.remove(id);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!DeepSpaceHelper.isDeepSpace()) return;
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            if (receivedPositionTick == -1 || UNIVERSE == null) return;
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            poseStack.mulPose(event.getModelViewMatrix()); // after sky is so early that the pose stack does not have the view rotation applied
            Vec3 position = event.getCamera().getPosition();
            VoxelShape box = DeepSpaceData.getBoxForPosition(position);
            if (box.bounds().contains(position)) {
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(true);
                AbsoluteDate currentDate = getRenderDate(partial);
                renderUniverse(null, poseStack, null, event.getPartialTick().getGameTimeDeltaTicks(),
                        partial, currentDate, receivedPosition.getPosition(currentDate), receivedPosition.getFrame(), event.getCamera());
            }
            poseStack.popPose();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
            PoseStack poseStack = event.getPoseStack();
            poseStack.pushPose();
            Vec3 position = event.getCamera().getPosition();
            renderInstanceBox(poseStack, position);
            poseStack.popPose();
        }
    }

    private static boolean renderInstanceBox(PoseStack poseStack, Vec3 position) {
        VoxelShape box = DeepSpaceData.getBoxForPosition(position);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        RenderSystem.enablePolygonOffset();
        RenderSystem.disableCull();
        float maxX = (float) box.max(Direction.Axis.X);
        float minX = (float) box.min(Direction.Axis.X);
        float maxY = (float) box.max(Direction.Axis.Y);
        float minY = (float) box.min(Direction.Axis.Y);
        float maxZ = (float) box.max(Direction.Axis.Z);
        float minZ = (float) box.min(Direction.Axis.Z);
        float s = (float) Math.min(Minecraft.getInstance().gameRenderer.getDepthFar(), box.bounds().getXsize());
        float s2 = s / 2;
        float clampedX = (float) Mth.clamp(position.x(), minX + s2, maxX - s2);
        float properX = (float) (clampedX - position.x());
        float clampedY = (float) Mth.clamp(position.y(), minY + s2, maxY - s2);
        float properY = (float) (clampedY - position.y());
        float clampedZ = (float) Mth.clamp(position.z(), minZ + s2, maxZ - s2);
        float properZ = (float) (clampedZ - position.z());

        if (position.x() > maxX - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxX - position.x());
            double frac = Math.min(1, 1 - d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), d, properY + s2, properZ - s2).setUv(0 + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY + s2, properZ + s2).setUv(s + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY - s2, properZ + s2).setUv(s + clampedZ, s - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY - s2, properZ - s2).setUv(0 + clampedZ, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (position.x() < minX + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minX - position.x());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), d, properY + s2, properZ - s2).setUv(0 + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY + s2, properZ + s2).setUv(s + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY - s2, properZ + s2).setUv(s + clampedZ, s - clampedY);
            bufferbuilder.addVertex(poseStack.last(), d, properY - s2, properZ - s2).setUv(0 + clampedZ, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (position.y() > maxY - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxY - position.y());
            double frac = Math.min(1, 1 - d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), properX + s2, d, properZ - s2).setUv(0 + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, d, properZ + s2).setUv(s + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, d, properZ + s2).setUv(s + clampedZ, s - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, d, properZ - s2).setUv(0 + clampedZ, s - clampedX);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (position.y() < minY + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minY - position.y());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), properX + s2, d, properZ - s2).setUv(0 + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, d, properZ + s2).setUv(s + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, d, properZ + s2).setUv(s + clampedZ, s - clampedX);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, d, properZ - s2).setUv(0 + clampedZ, s - clampedX);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (position.z() > maxZ - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxZ - position.z());
            double frac = Math.min(1, 1 - (maxZ - position.z()) / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), properX - s2, properY + s2, d).setUv(0 + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, properY + s2, d).setUv(s + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, properY - s2, d).setUv(s + clampedX, s - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, properY - s2, d).setUv(0 + clampedX, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (position.z() < minZ + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minZ - position.z());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(poseStack.last(), properX - s2, properY + s2, d).setUv(0 + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, properY + s2, d).setUv(s + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX + s2, properY - s2, d).setUv(s + clampedX, s - clampedY);
            bufferbuilder.addVertex(poseStack.last(), properX - s2, properY - s2, d).setUv(0 + clampedX, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        return false;
    }

    private static void renderUniverse(@Nullable CubePlanet exclude, PoseStack poseStack, @Nullable Quaternionf rotation, float deltaTick, float partialTick, AbsoluteDate renderDate, Vector3D pos, Frame posFrame, Camera camera) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        poseStack.pushPose();

        // 1. Render custom cosmic nebula and HD space stars first (only when custom sky is enabled)
        // TODO level time is fixed in deep space, figure out a better solution. Position in absolute frame, then have sol moving in the absolute frame?
        float celestialAngle = mc.level.getTimeOfDay(deltaTick);
        boolean isOverworld = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        float spaceVis = 1.0f;
        if (isOverworld) {
            double camY = camera.getPosition().y + dev.devce.rocketnautics.SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
            float altitudeVis = (float) Mth.clamp((camY - 1000.0) / 500.0, 0.0, 1.0);
            spaceVis = Math.max(mc.level.getStarBrightness(deltaTick), altitudeVis);
        }
        boolean isLegacy = dev.devce.rocketnautics.RocketConfig.CLIENT.skyRenderingSystem.get() == dev.devce.rocketnautics.RocketConfig.SkyRenderingSystem.LEGACY;
        if (isLegacy) {
            SkyHandler.renderCosmicNebula(poseStack, camera, celestialAngle, spaceVis);
            SkyHandler.renderSpaceStars(poseStack, spaceVis, camera, celestialAngle);
        } else {
            if (DeepSpaceHelper.isDeepSpace(mc.level)) {
                SkyHandler.renderSkybox(poseStack, spaceVis, camera, celestialAngle);
            }
        }

        // 2. Ensure star plasma texture is ready ONCE before iterating planets (avoid per-planet overhead)
        SkyHandler.ensureStarPlasmaTexture();

        // 3. Ensure planet VBOs are uploaded to GPU (one-time, on first render)
        ensureVBOs();

        // 3. Celestial bodies / planets rendering
        IntList needRenderData = new IntArrayList();
        poseStack.pushPose();
        if (isOverworld) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(celestialAngle * 360.0f));
        }

        Collection<CubePlanet> universePlanets = UNIVERSE.getPlanets();
        while (PLANET_ENTRIES.size() < universePlanets.size()) {
            PLANET_ENTRIES.add(new PlanetDistanceEntry());
        }
        int entryCount = 0;
        for (CubePlanet planet : universePlanets) {
            if (planet == exclude) continue;
            Vector3D planetPos;
            if (isOverworld && planet.frame().getName().equals("sol")) {
                planetPos = new Vector3D(0, -4000000000.0, 0);
            } else if (isOverworld && planet.frame().getName().equals("moon")) {
                planetPos = new Vector3D(0, 12000000.0, 0);
            } else {
                planetPos = planet.posInMyFrame(renderDate, pos, posFrame);
            }
            PlanetDistanceEntry entry = PLANET_ENTRIES.get(entryCount++);
            entry.planet = planet;
            entry.pos = planetPos;
            entry.distSq = planetPos.getNormSq();
        }
        if (entryCount > 1) {
            PLANET_ENTRIES.subList(0, entryCount).sort(PLANET_COMPARATOR);
        }

        boolean isDeepSpace = DeepSpaceHelper.isDeepSpace(mc.level);
        for (int i = 0; i < entryCount; i++) {
            PlanetDistanceEntry entry = PLANET_ENTRIES.get(i);
            if (!isDeepSpace && !RocketConfig.CLIENT.enableCustomSky.get()) continue;
            poseStack.pushPose();
            if (renderPlanet(entry.planet, entry.pos, poseStack, renderDate, celestialAngle, partialTick)) {
                if (!AWAITING_SERVER.put(entry.planet.id(), true)) {
                    needRenderData.add(entry.planet.id());
                }
            }
            poseStack.popPose();
        }
        if (!needRenderData.isEmpty()) PacketDistributor.sendToServer(new PlanetRenderRequestPayload(needRenderData.toIntArray(), SkyHandler.getMaximumScale()));
        poseStack.popPose();
        poseStack.popPose();
    }

    private static boolean renderPlanet(CubePlanet planet, Vector3D ourPosInPlanetFrame, PoseStack poseStack, AbsoluteDate date, float celestialAngle, float partialTicks) {
        assert UNIVERSE != null;
        Minecraft mc = Minecraft.getInstance();
        boolean isModern = dev.devce.rocketnautics.RocketConfig.CLIENT.skyRenderingSystem.get() == dev.devce.rocketnautics.RocketConfig.SkyRenderingSystem.MODERN;
        IntObjectPair<PreparedTexture> render = KNOWN_RENDER_DATA.get(planet.id());
        if (render != null && render.right() != null) {
            boolean isCachedTextureModern = !(render.right() instanceof DeepSpaceTexture);
            if (isModern != isCachedTextureModern) {
                render.right().retire();
                KNOWN_RENDER_DATA.remove(planet.id());
                render = null;
            }
        }
        if (isModern && (render == null || render.right() == null)) {
            ResourceLocation bakedTex = SkyHandler.loadBakedPlanetTexture(planet.frame().getName(), planet.id());
            ResourceLocation bakedNormalTex = SkyHandler.loadBakedPlanetNormalTexture(planet.frame().getName(), planet.id());
            if (bakedTex != null) {
                PreparedTexture prepared = new PreparedTexture() {
                    @Override
                    public ResourceLocation getId() {
                        return bakedTex;
                    }
                    @Override
                    public ResourceLocation getNormalId() {
                        return bakedNormalTex;
                    }
                    @Override
                    public void retire() {
                        Minecraft.getInstance().getTextureManager().release(bakedTex);
                        if (bakedNormalTex != null) {
                            Minecraft.getInstance().getTextureManager().release(bakedNormalTex);
                        }
                    }
                };
                render = IntObjectPair.of(SkyHandler.getMaximumScale(), prepared);
                KNOWN_RENDER_DATA.put(planet.id(), render);
            }
        }
        if (render == null || render.leftInt() != SkyHandler.getMaximumScale() || render.right() == null) {
            return true;
        }
        float parallaxFactor = (float) (SkyHandler.SKYBOX_DISTANCE / Math.max(1, ourPosInPlanetFrame.getNorm()));
        poseStack.translate(-ourPosInPlanetFrame.getX() * parallaxFactor, -ourPosInPlanetFrame.getY() * parallaxFactor, -ourPosInPlanetFrame.getZ() * parallaxFactor);
        poseStack.pushPose();
        boolean isOverworld = mc.level != null && mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        if (isOverworld) {
            if (planet.frame().getName().equals("sol")) {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(-90.0f));
            } else {
                poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90.0f));
            }
        } else {
            poseStack.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));
        }
        float size = (float) (planet.radius() * parallaxFactor);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        Vector3D L;
        if (isOverworld) {
            if (planet.frame().getName().equals("moon")) {
                int phase = debugMoonPhaseOverride >= 0 ? debugMoonPhaseOverride : mc.level.getMoonPhase();
                float phaseAngle = (float) (phase * 2.0 * Math.PI / 8.0);
                L = new Vector3D(Math.sin(phaseAngle), 0, Math.cos(phaseAngle));
            } else {
                L = new Vector3D(0, 0, 1);
            }
        } else {
            Vector3D lightSourcePosInOurFrame = UNIVERSE.getFrameByID(planet.extras().shadowLightSourceID()).map(sourceFrame -> {
                try {
                    return sourceFrame.getStaticTransformTo(planet.orekitFrame(), date).transformPosition(Vector3D.ZERO);
                } catch (Exception e) {
                    return Vector3D.ZERO;
                }
            }).orElse(Vector3D.ZERO);
            L = lightSourcePosInOurFrame.getNormSq() > 1e-6 ? lightSourcePosInOurFrame.normalize() : new Vector3D(1, 0, 0);
            L = planet.getRotationAtTime(date).applyInverseTo(L);
        }

        if (isModern && !planet.extras().star()) {
            if (planetNormalShader != null) {
                if (planetNormalShader.safeGetUniform("LightDir") != null) {
                    planetNormalShader.safeGetUniform("LightDir").set((float) L.getX(), (float) L.getY(), (float) L.getZ());
                }
                RenderSystem.setShader(() -> planetNormalShader);
            } else {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            }
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        }

        // Note: ensureStarPlasmaTexture() is called once before the planet loop in renderUniverse.
        if (planet.extras().star()) {
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            } else {
                render.right().setShaderTexture();
            }
        } else {
            render.right().setShaderTexture();
            if (isModern && render.right().getNormalId() != null) {
                RenderSystem.setShaderTexture(1, render.right().getNormalId());
            }
        }

        Matrix4f matrix = poseStack.last().pose();
        boolean isSphere = dev.devce.rocketnautics.RocketConfig.SERVER.planetShape.get() == dev.devce.rocketnautics.RocketConfig.PlanetShape.SPHERE;

        // --- Main planet albedo mesh: draw VBO with a scaled model matrix (no CPU upload) ---
        VertexBuffer mainVBO = isSphere ? SPHERE_VBO : CUBE_VBO;
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f scaledMatrix = new Matrix4f(matrix).scale(size);
        net.minecraft.client.renderer.ShaderInstance activeShader;
        if (isModern && !planet.extras().star() && planetNormalShader != null) {
            activeShader = planetNormalShader;
        } else {
            activeShader = RenderSystem.getShader();
        }
        drawVBO(mainVBO, scaledMatrix, activeShader);

        if (planet.extras().star() && isSphere) {
            // Render 3 extra rotating, pulsating plasma layers for a highly turbulent, volumetric 3D solar storm!
            long tick = mc.level.getGameTime();
            float baseTime = tick + partialTicks;

            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            }

            // Draw 3 plasma layers as VBO with per-layer rotated+scaled matrix (batched without unbind thrashing)
            net.minecraft.client.renderer.ShaderInstance plasmaShader = RenderSystem.getShader();
            SPHERE_VBO.bind();
            Matrix4f layerMatrix = new Matrix4f();
            Quaternionf layerRot = new Quaternionf();
            for (int layer = 1; layer <= 3; layer++) {
                float layerSize = size * (1.0f + layer * 0.006f);
                float rotX = baseTime * (0.012f * (layer == 2 ? -1.5f : 1.0f));
                float rotY = baseTime * (0.018f * (layer == 1 ? -1.2f : 1.5f));
                float rotZ = baseTime * (0.015f * (layer == 3 ? -1.0f : 1.3f));
                float alpha = 0.25f - (layer * 0.05f);
                RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
                layerRot.identity().rotateXYZ(rotX, rotY, rotZ);
                layerMatrix.set(matrix).rotate(layerRot).scale(layerSize);
                drawBoundVBO(SPHERE_VBO, layerMatrix, plasmaShader);
            }
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        if (planet.extras().clouds()) {
            net.minecraft.resources.ResourceLocation cloudTexture = SkyHandler.getCloudTextureId();
            if (cloudTexture != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, cloudTexture);
                net.minecraft.client.renderer.ShaderInstance cloudShader = RenderSystem.getShader();

                if (!isModern) {
                    // Cloud Shadows — shifted + tinted draw
                    double theta = 2.0 * Math.PI * celestialAngle;
                    float lx = (float) -Math.sin(theta);
                    float shadowShift = lx * size * 0.08f;
                    float shadowSize = size * 1.01f;
                    Matrix4f shadowMatrix = new Matrix4f(matrix).translate(shadowShift, 0, 0).scale(shadowSize);
                    RenderSystem.setShaderColor(0.01f, 0.02f, 0.08f, 0.48f);
                    drawVBO(mainVBO, shadowMatrix, cloudShader);
                    RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
                }

                // Single semi-transparent cloud layer — drawn via GPU VBO (zero CPU vertex overhead)
                Matrix4f cloudMatrix = new Matrix4f(matrix).scale(size * 1.015f);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.45f);
                drawVBO(mainVBO, cloudMatrix, cloudShader);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        // Shadow overlay: rebuild shadow VBO only when light direction changes significantly
        if (!isModern && planet.extras().renderShadow()) {
            float shadowScale = planet.extras().clouds() ? size * 1.035f : size * 1.002f;

            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);

            org.joml.Vector3f Lvec = new org.joml.Vector3f((float) L.getX(), (float) L.getY(), (float) L.getZ());
            VertexBuffer shadowVBO = PLANET_SHADOW_VBOS.get(planet.id());
            float[] lastL = PLANET_SHADOW_L.get(planet.id());
            boolean needRebuild = shadowVBO == null || lastL == null ||
                Math.abs(Lvec.x - lastL[0]) > SHADOW_L_THRESHOLD ||
                Math.abs(Lvec.y - lastL[1]) > SHADOW_L_THRESHOLD ||
                Math.abs(Lvec.z - lastL[2]) > SHADOW_L_THRESHOLD;

            if (needRebuild) {
                if (shadowVBO != null) shadowVBO.close();
                CachedShadowVertex[] shadowVerts = isSphere ? SPHERE_SHADOW_VERTICES : CUBE_SHADOW_VERTICES;
                shadowVBO = uploadShadowVBO(shadowVerts, 1.0f, L); // unit-scale, size applied via matrix
                PLANET_SHADOW_VBOS.put(planet.id(), shadowVBO);
                PLANET_SHADOW_L.put(planet.id(), new float[]{Lvec.x, Lvec.y, Lvec.z});
            }

            Matrix4f shadowMatrix = new Matrix4f(matrix).scale(shadowScale);
            drawVBO(shadowVBO, shadowMatrix, RenderSystem.getShader());
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        }

        if ((!isModern || planet.extras().star()) && planet.extras().diffuseLayers()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.enableCull();

            int layers = planet.extras().diffuseLayerCount();

            float ar = 1.0f, ag = 1.0f, ab = 1.0f;
            if (!planet.extras().star()) {
                double tAngle = celestialAngle * 2.0 * Math.PI;
                float sunIntensity = (float) Math.cos(tAngle);
                float sideIntensity = (float) Math.abs(Math.sin(tAngle));
                if (sunIntensity > 0) {
                    float t = sideIntensity;
                    ar = net.minecraft.util.Mth.lerp(t, 0.40f, 1.00f);
                    ag = net.minecraft.util.Mth.lerp(t, 0.70f, 0.42f);
                    ab = net.minecraft.util.Mth.lerp(t, 1.00f, 0.15f);
                } else {
                    float t = -sunIntensity;
                    ar = net.minecraft.util.Mth.lerp(t, 1.00f, 0.18f);
                    ag = net.minecraft.util.Mth.lerp(t, 0.42f, 0.08f);
                    ab = net.minecraft.util.Mth.lerp(t, 0.15f, 0.45f);
                }

                float terminatorFactor = Math.max(0.0f, 1.0f - (Math.abs(sunIntensity) / 0.45f));
                terminatorFactor = terminatorFactor * terminatorFactor * (3.0f - 2.0f * terminatorFactor);
                ar = net.minecraft.util.Mth.lerp(terminatorFactor, ar, 1.00f);
                ag = net.minecraft.util.Mth.lerp(terminatorFactor, ag, 0.48f);
                ab = net.minecraft.util.Mth.lerp(terminatorFactor, ab, 0.12f);
            }

            VertexBuffer atmVBO = isSphere ? SPHERE_ATM_VBO : CUBE_ATM_VBO;
            if (atmVBO != null) {
                net.minecraft.client.renderer.ShaderInstance atmShader = RenderSystem.getShader();
                atmVBO.bind();
                Matrix4f layerMatrix = new Matrix4f();
                for (int i = 0; i < layers; i++) {
                    float progress = i / (float) (layers - 1);
                    float s;
                    float aa;
                    float lr = ar, lg = ag, lb = ab;

                    if (planet.extras().star()) {
                        s = size * (1.005f + (float)Math.pow(progress, 1.5f) * 0.7f); // wider glow
                        aa = (0.28f * (float)Math.pow(1.0f - progress, 2.5f));

                        if (progress < 0.2f) {
                            float t = progress / 0.2f;
                            lr = 1.0f;
                            lg = net.minecraft.util.Mth.lerp(t, 0.95f, 0.8f);
                            lb = net.minecraft.util.Mth.lerp(t, 0.8f, 0.1f);
                        } else if (progress < 0.5f) {
                            float t = (progress - 0.2f) / 0.3f;
                            lr = 1.0f;
                            lg = net.minecraft.util.Mth.lerp(t, 0.8f, 0.2f);
                            lb = net.minecraft.util.Mth.lerp(t, 0.1f, 0.0f);
                        } else if (progress < 0.8f) {
                            float t = (progress - 0.5f) / 0.3f;
                            lr = net.minecraft.util.Mth.lerp(t, 1.0f, 0.7f);
                            lg = net.minecraft.util.Mth.lerp(t, 0.2f, 0.0f);
                            lb = net.minecraft.util.Mth.lerp(t, 0.0f, 0.6f);
                        } else {
                            float t = (progress - 0.8f) / 0.2f;
                            lr = net.minecraft.util.Mth.lerp(t, 0.7f, 0.1f);
                            lg = 0.0f;
                            lb = net.minecraft.util.Mth.lerp(t, 0.6f, 0.2f);
                        }
                    } else {
                        s = size * (1.01f + (float)Math.pow(progress, 1.2f) * 0.4f);
                        aa = (0.05f * (float)Math.pow(1.0f - progress, 2.0f)) * 1.0f;
                    }

                    RenderSystem.setShaderColor(lr, lg, lb, aa);
                    layerMatrix.set(matrix).scale(s);
                    drawBoundVBO(atmVBO, layerMatrix, atmShader);
                }
                VertexBuffer.unbind();
                RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        poseStack.popPose();
        return false;
    }

    public static Pair<Frame, List<CubePlanet>> renderHologram(Vector3D posInFrame, Frame posFrame, UniverseDefinition universe, AbsoluteDate date, double scale, double scaleTest, PoseStack poseStack, MultiBufferSource source) {
        Frame retFrame = posFrame;
        List<CubePlanet> renderedPlanets = new ArrayList<>();
        IntList needRenderData = new IntArrayList();
        Iterator<Pair<Vector3D, CubePlanet>> iter = universe.getPlanets().stream()
                .map(planet -> Pair.of(planet.posInMyFrame(date, posInFrame, posFrame), planet))
                .filter(pair -> pair.left().getNormSq() < scaleTest * scaleTest)
                .filter(pair -> pair.right().radius() < scaleTest).iterator();
        while (iter.hasNext()) {
            Pair<Vector3D, CubePlanet> planet = iter.next();
            if (planet.right().orekitFrame().getDepth() < retFrame.getDepth()) {
                retFrame = planet.right().orekitFrame();
            }
            poseStack.pushPose();
            if (renderHoloPlanet(planet.right(), planet.left(), poseStack, date, scale, source, 0.9f, 0.9f, 1.0f, 0.9f)) {
                if (!AWAITING_SERVER.put(planet.right().id(), true)) {
                    needRenderData.add(planet.right().id());
                }
            } else {
                renderedPlanets.add(planet.right());
            }
            poseStack.popPose();
        }
        if (!needRenderData.isEmpty()) PacketDistributor.sendToServer(new PlanetRenderRequestPayload(needRenderData.toIntArray(), SkyHandler.getMaximumScale()));
        return Pair.of(retFrame, renderedPlanets);
    }

    public static boolean renderHoloPlanet(CubePlanet planet, Vector3D ourPosInPlanetFrame, PoseStack poseStack, AbsoluteDate date, double holoScale, MultiBufferSource source, float r, float g, float b, float a) {
        IntObjectPair<PreparedTexture> render = KNOWN_RENDER_DATA.get(planet.id());
        if (render == null || render.leftInt() != SkyHandler.getMaximumScale() || render.right() == null) {
            return true;
        }
        float scaleFactor = (float) (1 / holoScale);
        poseStack.translate(-ourPosInPlanetFrame.getX() * scaleFactor, -ourPosInPlanetFrame.getY() * scaleFactor, -ourPosInPlanetFrame.getZ() * scaleFactor);
        poseStack.pushPose();
        poseStack.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));
        float size = (float) (planet.radius() * scaleFactor);

        VertexConsumer bufferbuilder = source.getBuffer(render.right().attachType(HOLOGRAM_TYPE));

        Matrix4f matrix = poseStack.last().pose();

        // Draw base planet map
        boolean isSphere = dev.devce.rocketnautics.RocketConfig.SERVER.planetShape.get() == dev.devce.rocketnautics.RocketConfig.PlanetShape.SPHERE;
        renderCubeOrSphere(bufferbuilder, matrix, size, 0.0f, 0.0f, r, g, b, a, isSphere);

        // no clouds, they look really odd on the hologram

        // since we're constantly updating the linked texture, we need to draw it right now.
        if (source instanceof MultiBufferSource.BufferSource buf) {
            buf.endBatch();
        }

        poseStack.popPose();
        return false;
    }

    private static final Function<ResourceLocation, RenderType> HOLOGRAM_TYPE = Util.memoize(DeepSpaceHandler::getHologramType);

    private static RenderType getHologramType(ResourceLocation tex) {
        RenderType.CompositeState rendertype$state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(GameRenderer::getPositionTexColorShader))
                .setTextureState(new RenderStateShard.TextureStateShard(tex, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setLightmapState(RenderStateShard.NO_LIGHTMAP)
                .createCompositeState(false);
        return RenderType.create("rocketnautics_hologram", DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.QUADS, 256, false, true, rendertype$state);
    }


    private static final Vector3d[][] FACE_CORNERS = {
        // TOP
        { new Vector3d(-1, 1, -1), new Vector3d(-1, 1, 1), new Vector3d(1, 1, 1), new Vector3d(1, 1, -1) },
        // BOTTOM
        { new Vector3d(-1, -1, -1), new Vector3d(1, -1, -1), new Vector3d(1, -1, 1), new Vector3d(-1, -1, 1) },
        // NORTH
        { new Vector3d(1, 1, -1), new Vector3d(1, -1, -1), new Vector3d(-1, -1, -1), new Vector3d(-1, 1, -1) },
        // SOUTH
        { new Vector3d(-1, 1, 1), new Vector3d(-1, -1, 1), new Vector3d(1, -1, 1), new Vector3d(1, 1, 1) },
        // WEST
        { new Vector3d(-1, 1, -1), new Vector3d(-1, -1, -1), new Vector3d(-1, -1, 1), new Vector3d(-1, 1, 1) },
        // EAST
        { new Vector3d(1, 1, 1), new Vector3d(1, -1, 1), new Vector3d(1, -1, -1), new Vector3d(1, 1, -1) }
    };

    private static class CachedVertex {
        final float x, y, z;
        final float u, v;
        CachedVertex(float x, float y, float z, float u, float v) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = u;
            this.v = v;
        }
    }

    private static class CachedShadowVertex {
        final float ux, uy, uz;
        final float nx, ny, nz;
        final int gx, gy;
        CachedShadowVertex(Vector3D p, int gx, int gy, boolean isSphere) {
            this.ux = (float) p.getX();
            this.uy = (float) p.getY();
            this.uz = (float) p.getZ();
            Vector3D norm = p.normalize();
            this.nx = (float) norm.getX();
            this.ny = (float) norm.getY();
            this.nz = (float) norm.getZ();
            this.gx = gx;
            this.gy = gy;
        }
    }

    private static final CachedVertex[] SPHERE_VERTICES;
    private static final CachedVertex[] CUBE_VERTICES;
    private static final CachedShadowVertex[] SPHERE_SHADOW_VERTICES;
    private static final CachedShadowVertex[] CUBE_SHADOW_VERTICES;

    static {
        SPHERE_VERTICES = precomputeVertices(true);
        CUBE_VERTICES = precomputeVertices(false);
        SPHERE_SHADOW_VERTICES = precomputeShadowVertices(true);
        CUBE_SHADOW_VERTICES = precomputeShadowVertices(false);
    }

    private static CachedVertex[] precomputeVertices(boolean isSphere) {
        int G = isSphere ? 16 : 1;
        List<CachedVertex> list = new ArrayList<>();
        for (int face = 0; face < 6; face++) {
            float u0 = 0.0f, u1 = 1.0f, v0 = 0.0f, v1 = 1.0f;
            Vector3d c00 = FACE_CORNERS[face][0];
            Vector3d c01 = FACE_CORNERS[face][1];
            Vector3d c11 = FACE_CORNERS[face][2];
            Vector3d c10 = FACE_CORNERS[face][3];
            for (int gv = 0; gv < G; gv++) {
                for (int gu = 0; gu < G; gu++) {
                    double uA = (double) gu / G;
                    double uB = (double) (gu + 1) / G;
                    double vA = (double) gv / G;
                    double vB = (double) (gv + 1) / G;

                    Vector3d p0 = getBilinearPoint(c00, c01, c11, c10, uA, vA);
                    Vector3d p1 = getBilinearPoint(c00, c01, c11, c10, uA, vB);
                    Vector3d p2 = getBilinearPoint(c00, c01, c11, c10, uB, vB);
                    Vector3d p3 = getBilinearPoint(c00, c01, c11, c10, uB, vA);

                    if (isSphere) {
                        p0.normalize();
                        p1.normalize();
                        p2.normalize();
                        p3.normalize();
                    }

                    float texUA = Mth.lerp((float) uA, u0, u1) / 6.0f + (face / 6.0f);
                    float texUB = Mth.lerp((float) uB, u0, u1) / 6.0f + (face / 6.0f);
                    float texVA = Mth.lerp((float) vA, v0, v1);
                    float texVB = Mth.lerp((float) vB, v0, v1);

                    list.add(new CachedVertex((float)p0.x, (float)p0.y, (float)p0.z, texUA, texVA));
                    list.add(new CachedVertex((float)p1.x, (float)p1.y, (float)p1.z, texUA, texVB));
                    list.add(new CachedVertex((float)p2.x, (float)p2.y, (float)p2.z, texUB, texVB));
                    list.add(new CachedVertex((float)p3.x, (float)p3.y, (float)p3.z, texUB, texVA));
                }
            }
        }
        return list.toArray(new CachedVertex[0]);
    }

    private static CachedShadowVertex[] precomputeShadowVertices(boolean isSphere) {
        int G = 16;
        Vector3D[] unitFacesCenter = {
            new Vector3D(0, 1, 0),
            new Vector3D(0, -1, 0),
            new Vector3D(0, 0, -1),
            new Vector3D(0, 0, 1),
            new Vector3D(-1, 0, 0),
            new Vector3D(1, 0, 0)
        };
        Vector3D[] unitFacesU = {
            new Vector3D(1, 0, 0),
            new Vector3D(1, 0, 0),
            new Vector3D(-1, 0, 0),
            new Vector3D(1, 0, 0),
            new Vector3D(0, 0, 1),
            new Vector3D(0, 0, -1)
        };
        Vector3D[] unitFacesV = {
            new Vector3D(0, 0, 1),
            new Vector3D(0, 0, 1),
            new Vector3D(0, -1, 0),
            new Vector3D(0, -1, 0),
            new Vector3D(0, -1, 0),
            new Vector3D(0, -1, 0)
        };

        List<CachedShadowVertex> list = new ArrayList<>();
        for (int faceIndex = 0; faceIndex < 6; faceIndex++) {
            FaceDefinition face = new FaceDefinition(unitFacesCenter[faceIndex], unitFacesU[faceIndex], unitFacesV[faceIndex]);
            for (int gv = 0; gv < G; gv++) {
                for (int gu = 0; gu < G; gu++) {
                    Vector3D p1 = getPointPrecompute(face, gu, gv, G, 1.0, isSphere);
                    Vector3D p2 = getPointPrecompute(face, gu, gv + 1, G, 1.0, isSphere);
                    Vector3D p3 = getPointPrecompute(face, gu + 1, gv + 1, G, 1.0, isSphere);
                    Vector3D p4 = getPointPrecompute(face, gu + 1, gv, G, 1.0, isSphere);

                    int g1x = gu, g1y = gv;
                    int g2x = gu, g2y = gv + 1;
                    int g3x = gu + 1, g3y = gv + 1;
                    int g4x = gu + 1, g4y = gv;
                    
                    // Bottom face normal swap:
                    if (faceIndex == 1) {
                        Vector3D temp = p2;
                        p2 = p4;
                        p4 = temp;
                        
                        g2x = gu + 1; g2y = gv;
                        g4x = gu;     g4y = gv + 1;
                    }
                    
                    list.add(new CachedShadowVertex(p1, g1x, g1y, isSphere));
                    list.add(new CachedShadowVertex(p2, g2x, g2y, isSphere));
                    list.add(new CachedShadowVertex(p3, g3x, g3y, isSphere));
                    list.add(new CachedShadowVertex(p4, g4x, g4y, isSphere));
                }
            }
        }
        return list.toArray(new CachedShadowVertex[0]);
    }

    private static Vector3D getPointPrecompute(FaceDefinition face, int gu, int gv, int G, double shadowSize, boolean isSphere) {
        double u = -1.0 + 2.0 * gu / G;
        double v = -1.0 + 2.0 * gv / G;
        Vector3D p = face.center.add(face.U.scalarMultiply(u)).add(face.V.scalarMultiply(v));
        if (isSphere) {
            return p.normalize().scalarMultiply(shadowSize);
        }
        return p;
    }

    private static void renderCubeOrSphere(VertexConsumer bufferbuilder, Matrix4f matrix, float size, float uOffset, float vOffset, float r, float g, float b, float a, boolean isSphere) {
        CachedVertex[] vertices = isSphere ? SPHERE_VERTICES : CUBE_VERTICES;
        for (int i = 0; i < vertices.length; i++) {
            CachedVertex v = vertices[i];
            bufferbuilder.addVertex(matrix, v.x * size, v.y * size, v.z * size)
                         .setColor(r, g, b, a)
                         .setUv(v.u + uOffset, v.v + vOffset);
        }
    }

    private static Vector3d getBilinearPoint(Vector3d c00, Vector3d c01, Vector3d c11, Vector3d c10, double u, double v) {
        Vector3d p0 = c00.lerp(c10, u, new Vector3d());
        Vector3d p1 = c01.lerp(c11, u, new Vector3d());
        return p0.lerp(p1, v, new Vector3d());
    }

    private static class FaceDefinition {
        final Vector3D center;
        final Vector3D U;
        final Vector3D V;
        
        FaceDefinition(Vector3D center, Vector3D U, Vector3D V) {
            this.center = center;
            this.U = U;
            this.V = V;
        }
    }

    private static Vector3D getPoint(FaceDefinition face, int gu, int gv, int G, double shadowSize) {
        double u = -1.0 + 2.0 * gu / G;
        double v = -1.0 + 2.0 * gv / G;
        Vector3D p = face.center.add(face.U.scalarMultiply(u)).add(face.V.scalarMultiply(v));
        if (dev.devce.rocketnautics.RocketConfig.SERVER.planetShape.get() == dev.devce.rocketnautics.RocketConfig.PlanetShape.SPHERE) {
            return p.normalize().scalarMultiply(shadowSize);
        }
        return p;
    }

    private static long computeColor(float nx, float ny, float nz, Vector3D L, int gx, int gy) {
        double d = nx * L.getX() + ny * L.getY() + nz * L.getZ();
        
        int r = 4, g = 5, b = 18, a = 220;
        
        if (d > 0.05) {
            // Lit side: no dark shadow, but soft sunlight highlight on the bright side
            r = 255; g = 245; b = 200;
            if (d > 0.45) {
                // Smooth highlight transition
                double factor = Math.min(1.0, (d - 0.45) / 0.20);
                factor = factor * factor * (3.0 - 2.0 * factor); // smoothstep
                a = (int) (factor * 45);
            } else {
                a = 0;
            }
        } else if (d > -0.12) {
            // Smoothly transition shadow alpha and color in the terminator zone
            double factor = (d - (-0.12)) / 0.17; // 0 at d=-0.12, 1 at d=0.05
            factor = factor * factor * (3.0 - 2.0 * factor); // smoothstep
            
            r = (int) net.minecraft.util.Mth.lerp(factor, 4, 0);
            g = (int) net.minecraft.util.Mth.lerp(factor, 5, 0);
            b = (int) net.minecraft.util.Mth.lerp(factor, 18, 0);
            a = (int) net.minecraft.util.Mth.lerp(factor, 220, 0);
        } else {
            // Dark side: full shadow
            r = 4; g = 5; b = 18; a = 220;
        }

        // Apply dither pattern to the transition zones to keep the retro shader look!
        if (d > -0.12 && d < 0.05) {
            int dither = ((gx + gy) % 2 == 0) ? 12 : -12;
            a = Math.max(0, Math.min(220, a + dither));
        } else if (d > 0.45 && d < 0.65) {
            int dither = ((gx + gy) % 2 == 0) ? 8 : -8;
            a = Math.max(0, Math.min(45, a + dither));
        }
        
        return ((long)r << 24) | ((long)g << 16) | ((long)b << 8) | a;
    }

    public static boolean shouldRenderPlanetBeneath(Level level) {
        if (DeepSpaceHelper.isDeepSpace(level)) return false;
        return UNIVERSE != null && UNIVERSE.getPlanetByDimension(level.dimension()) != null;
    }
    
    public static void renderUniverseForLevel(Level level, Vec3 position, PoseStack poseStack, float partialDelta, float partialTick, Camera camera) {
        if (UNIVERSE == null) return;
        CubePlanet planet = UNIVERSE.getPlanetByDimension(level.dimension());
        if (planet == null || planet.linkedDimension() == null || !planet.linkedDimension().renderUniverseInDimension()) return;
        poseStack.pushPose();
        AbsoluteDate date = getPredictedUniverseDate(partialTick);
        if (date == null || !DeepSpaceHelper.shouldOverrideLevelTime(level)) {
            double elapsedSeconds = (level.getDayTime() + partialTick) * 0.05;
            date = new AbsoluteDate(DeepSpaceHelper.EPOCH, elapsedSeconds);
        }
        var globalCoords = DeepSpaceHelper.localPositionToGlobalPositionAndRotation(position.toVector3f().get(new Vector3d()),  null, level, planet, date);
        if (level.dimension() != net.minecraft.world.level.Level.OVERWORLD) {
            poseStack.mulPose(DeepSpaceHelper.adapt(globalCoords.second()).get(new Quaternionf()).conjugate());
        }
        renderUniverse(planet, poseStack, null, partialDelta, partialTick, date, globalCoords.first().getPosition(), planet.orekitFrame(), camera);
        poseStack.popPose();
    }
}
