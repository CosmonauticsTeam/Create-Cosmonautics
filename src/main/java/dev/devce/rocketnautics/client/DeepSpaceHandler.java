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
    private static final Int2BooleanAVLTreeMap AWAITING_SERVER = new Int2BooleanAVLTreeMap();

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
        });
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

    public static void receiveRenderData(int id, Either<ColorPalette, ResourceLocation> data, int powerScale) {
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

        // 1. Render custom cosmic nebula and HD space stars first
        // TODO level time is fixed in deep space, figure out a better solution. Position in absolute frame, then have sol moving in the absolute frame?
        float celestialAngle = mc.level.getTimeOfDay(deltaTick);
        boolean isOverworld = mc.level.dimension() == net.minecraft.world.level.Level.OVERWORLD;
        float spaceVis = isOverworld ? mc.level.getStarBrightness(deltaTick) : 1.0f;
        SkyHandler.renderCosmicNebula(poseStack, camera, celestialAngle, spaceVis);
        SkyHandler.renderSpaceStars(poseStack, spaceVis, camera, celestialAngle);

        // 2.  celestial bodies / planets rendering
        IntList needRenderData = new IntArrayList();
        poseStack.pushPose();
        if (isOverworld) {
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(celestialAngle * 360.0f));
        }

        Iterator<Pair<Vector3D, CubePlanet>> iter = UNIVERSE.getPlanets().stream()
                .map(planet -> {
                    Vector3D planetPos;
                    if (isOverworld && planet.frame().getName().equals("sol")) {
                        planetPos = new Vector3D(0, -4000000000.0, 0);
                    } else if (isOverworld && planet.frame().getName().equals("moon")) {
                        planetPos = new Vector3D(0, 12000000.0, 0);
                    } else {
                        planetPos = planet.posInMyFrame(renderDate, pos, posFrame);
                    }
                    return Pair.of(planetPos, planet);
                })
                .sorted(Comparator.comparingDouble(p -> -p.left().getNormSq())).iterator(); // sort descending, we want to render furthest away first.
        while (iter.hasNext()) {
            Pair<Vector3D, CubePlanet> planet = iter.next();
            if (planet.right() == exclude) continue;
            poseStack.pushPose();
            if (renderPlanet(planet.right(), planet.left(), poseStack, renderDate, celestialAngle, partialTick)) {
                if (!AWAITING_SERVER.put(planet.right().id(), true)) {
                    needRenderData.add(planet.right().id());
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
        IntObjectPair<PreparedTexture> render = KNOWN_RENDER_DATA.get(planet.id());
        if (render == null || render.leftInt() != SkyHandler.getMaximumScale() || render.right() == null) {
            return true;
        }
        float parallaxFactor = (float) (SkyHandler.SKYBOX_DISTANCE / Math.max(1, ourPosInPlanetFrame.getNorm()));
        poseStack.translate(-ourPosInPlanetFrame.getX() * parallaxFactor, -ourPosInPlanetFrame.getY() * parallaxFactor, -ourPosInPlanetFrame.getZ() * parallaxFactor);
        poseStack.pushPose();
        poseStack.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));
        float size = (float) (planet.radius() * parallaxFactor);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        if (planet.extras().star()) {
            SkyHandler.ensureStarPlasmaTexture();
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            } else {
                render.right().setShaderTexture();
            }
        } else {
            render.right().setShaderTexture();
        }

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder bufferbuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        Matrix4f matrix = poseStack.last().pose();

        boolean isSphere = dev.devce.rocketnautics.RocketConfig.SERVER.planetShape.get() == dev.devce.rocketnautics.RocketConfig.PlanetShape.SPHERE;
        renderCubeOrSphere(bufferbuilder, matrix, size, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, isSphere);
        BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());

        if (planet.extras().star() && isSphere) {
            // Render 3 extra rotating, pulsating plasma layers for a highly turbulent, volumetric 3D solar storm!
            // This prevents the "flat cube" look and creates amazing swirling interference patterns.
            long tick = mc.level.getGameTime();
            float baseTime = tick + partialTicks;
            
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
            if (SkyHandler.STAR_PLASMA_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.STAR_PLASMA_TEXTURE_ID);
            }
            
            for (int layer = 1; layer <= 3; layer++) {
                poseStack.pushPose();
                
                // Pulsate slightly differently for each layer
                float pulseFreq = 0.05f + layer * 0.02f;
                float pulseAmp = 0.005f + layer * 0.004f;
                float scale = 1.0f;
                float layerSize = size * (1.0f + layer * 0.006f) * scale;
                
                // Rotate layers in different directions and speeds
                float rotX = baseTime * (0.012f * (layer == 2 ? -1.5f : 1.0f));
                float rotY = baseTime * (0.018f * (layer == 1 ? -1.2f : 1.5f));
                float rotZ = baseTime * (0.015f * (layer == 3 ? -1.0f : 1.3f));
                
                poseStack.mulPose(new Quaternionf().rotateXYZ(rotX, rotY, rotZ));
                
                // Draw the textured cube faces with additive opacity
                float alpha = 0.25f - (layer * 0.05f); // inner layer is more opaque, outer layer is softer
                BufferBuilder layerBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                
                // Render textured cube with color using our existing renderCubeFaces helper
                renderCubeOrSphere(layerBuilder, poseStack.last().pose(), layerSize, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, alpha, isSphere);
                BufferUploader.drawWithShader(layerBuilder.buildOrThrow());
                
                poseStack.popPose();
            }
        }

        if (planet.extras().clouds()) {
            SkyHandler.ensureCloudTexture();
            if (SkyHandler.CLOUD_TEXTURE_ID != null) {
                RenderSystem.setShaderTexture(0, SkyHandler.CLOUD_TEXTURE_ID);
                long factor = 1000L; // MUCH slower
                float timeOffset = (float) ((date.durationFrom(DeepSpaceHelper.EPOCH) % (20L * factor)) / (float) factor);
                
                // Cloud Shadows
                double theta = 2.0 * Math.PI * celestialAngle;
                float lx = (float) -Math.sin(theta);
                float shadowShift = lx * size * 0.08f;
                BufferBuilder shadowBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float sr = 0.01f, sg = 0.02f, sb = 0.08f, sa = 0.48f;
                // Draw slightly larger cube for clouds
                float shadowSize = size * 1.01f;
                matrix.translate(shadowShift, 0, 0); // shift matrix for shadow
                renderCubeOrSphere(shadowBuilder, matrix, shadowSize, timeOffset, 0.0f, sr, sg, sb, sa, isSphere);
                BufferUploader.drawWithShader(shadowBuilder.buildOrThrow());
                matrix.translate(-shadowShift, 0, 0); // un-shift matrix

                // Scrolling Clouds
                BufferBuilder cloudBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float cloudSize = size * 1.02f;
                renderCubeOrSphere(cloudBuilder, matrix, cloudSize, timeOffset, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, isSphere);
                BufferUploader.drawWithShader(cloudBuilder.buildOrThrow());

                // Scrolling Clouds Layer 2 (Upper, faster, different scroll offsets for parallax volumetric effect)
                BufferBuilder cloudBuilder2 = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
                float cloudSize2 = size * 1.032f;
                float uOffset2 = -timeOffset * 1.4f;
                float vOffset2 = timeOffset * 0.5f;
                renderCubeOrSphere(cloudBuilder2, matrix, cloudSize2, uOffset2, vOffset2, 1.0f, 1.0f, 1.0f, 0.55f, isSphere); // Soft, beautiful overlay
                BufferUploader.drawWithShader(cloudBuilder2.buildOrThrow());
            }

        }

        // Render gorgeous 3D dynamic, subdivided, cell-shaded and dithered shadow overlay for all non-star planets!
        if (planet.extras().renderShadow()) {
            Vector3D lightSourcePosInOurFrame = UNIVERSE.getFrameByID(planet.extras().shadowLightSourceID()).map(sourceFrame -> {
                try {
                    return sourceFrame.getStaticTransformTo(planet.orekitFrame(), date).transformPosition(Vector3D.ZERO);
                } catch (Exception e) {
                    return Vector3D.ZERO;
                }
            }).orElse(Vector3D.ZERO);

            Vector3D L = lightSourcePosInOurFrame.getNormSq() > 1e-6 ? lightSourcePosInOurFrame.normalize() : new Vector3D(1, 0, 0);

            L = planet.getRotationAtTime(date).applyInverseTo(L);
            
            float shadowSize = planet.extras().clouds() ? size * 1.035f : size * 1.002f;
            
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            
            BufferBuilder shadowCubeBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            
            FaceDefinition[] shadowFaces = {
                // TOP
                new FaceDefinition(new Vector3D(0, shadowSize, 0), new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, shadowSize)),
                // BOTTOM
                new FaceDefinition(new Vector3D(0, -shadowSize, 0), new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, shadowSize)),
                // NORTH
                new FaceDefinition(new Vector3D(0, 0, -shadowSize), new Vector3D(-shadowSize, 0, 0), new Vector3D(0, -shadowSize, 0)),
                // SOUTH
                new FaceDefinition(new Vector3D(0, 0, shadowSize), new Vector3D(shadowSize, 0, 0), new Vector3D(0, -shadowSize, 0)),
                // WEST
                new FaceDefinition(new Vector3D(-shadowSize, 0, 0), new Vector3D(0, 0, shadowSize), new Vector3D(0, -shadowSize, 0)),
                // EAST
                new FaceDefinition(new Vector3D(shadowSize, 0, 0), new Vector3D(0, 0, -shadowSize), new Vector3D(0, -shadowSize, 0))
            };
            
            int G = 16;
            for (FaceDefinition face : shadowFaces) {
                for (int gv = 0; gv < G; gv++) {
                    for (int gu = 0; gu < G; gu++) {
                        Vector3D p1 = getPoint(face, gu, gv, G, shadowSize);
                        Vector3D p2 = getPoint(face, gu, gv + 1, G, shadowSize);
                        Vector3D p3 = getPoint(face, gu + 1, gv + 1, G, shadowSize);
                        Vector3D p4 = getPoint(face, gu + 1, gv, G, shadowSize);

                        // for some reason the bottom face and only the bottom face has an incorrect normal
                        if (face == shadowFaces[1]) {
                            Vector3D temp = p2;
                            p2 = p4;
                            p4 = temp;
                        }
                        
                        long c1 = computeColor(p1, L, gu, gv);
                        long c2 = computeColor(p2, L, gu, gv + 1);
                        long c3 = computeColor(p3, L, gu + 1, gv + 1);
                        long c4 = computeColor(p4, L, gu + 1, gv);
                        
                        shadowCubeBuilder.addVertex(matrix, (float)p1.getX(), (float)p1.getY(), (float)p1.getZ())
                                         .setColor((int)((c1 >> 24) & 255), (int)((c1 >> 16) & 255), (int)((c1 >> 8) & 255), (int)(c1 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p2.getX(), (float)p2.getY(), (float)p2.getZ())
                                         .setColor((int)((c2 >> 24) & 255), (int)((c2 >> 16) & 255), (int)((c2 >> 8) & 255), (int)(c2 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p3.getX(), (float)p3.getY(), (float)p3.getZ())
                                         .setColor((int)((c3 >> 24) & 255), (int)((c3 >> 16) & 255), (int)((c3 >> 8) & 255), (int)(c3 & 255));
                        shadowCubeBuilder.addVertex(matrix, (float)p4.getX(), (float)p4.getY(), (float)p4.getZ())
                                         .setColor((int)((c4 >> 24) & 255), (int)((c4 >> 16) & 255), (int)((c4 >> 8) & 255), (int)(c4 & 255));
                    }
                }
            }
            
            BufferUploader.drawWithShader(shadowCubeBuilder.buildOrThrow());
        }

        if (planet.extras().diffuseLayers()) {
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.disableCull();

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

                // Spectacular fiery orange sunset/sunrise halo effect at the terminator line!
                float terminatorFactor = Math.max(0.0f, 1.0f - (Math.abs(sunIntensity) / 0.45f));
                terminatorFactor = terminatorFactor * terminatorFactor * (3.0f - 2.0f * terminatorFactor);
                ar = net.minecraft.util.Mth.lerp(terminatorFactor, ar, 1.00f);
                ag = net.minecraft.util.Mth.lerp(terminatorFactor, ag, 0.48f);
                ab = net.minecraft.util.Mth.lerp(terminatorFactor, ab, 0.12f);
            }

            for (int i = 0; i < layers; i++) {
                float progress = i / (float) (layers - 1);
                float s;
                float aa;
                float lr = ar, lg = ag, lb = ab;

                if (planet.extras().star()) {
                    // Solar corona: much wider glow, extremely beautiful color gradient:
                    // From white-hot yellow (inner) to golden-orange to fiery-red to violet/magenta (outer "veil" flare!)
                    s = size * (1.005f + (float)Math.pow(progress, 1.5f) * 0.7f); // wider glow
                    
                    // Opacity curve: fade out smoothly
                    aa = (0.28f * (float)Math.pow(1.0f - progress, 2.5f));
                    
                    // Color gradient from progress 0 (inner) to 1 (outer)
                    if (progress < 0.2f) {
                        // White-hot yellow-gold
                        float t = progress / 0.2f;
                        lr = 1.0f;
                        lg = net.minecraft.util.Mth.lerp(t, 0.95f, 0.8f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.8f, 0.1f);
                    } else if (progress < 0.5f) {
                        // Golden orange to fiery red
                        float t = (progress - 0.2f) / 0.3f;
                        lr = 1.0f;
                        lg = net.minecraft.util.Mth.lerp(t, 0.8f, 0.2f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.1f, 0.0f);
                    } else if (progress < 0.8f) {
                        // Fiery red to deep violet/magenta (magical cosmic veil!)
                        float t = (progress - 0.5f) / 0.3f;
                        lr = net.minecraft.util.Mth.lerp(t, 1.0f, 0.7f);
                        lg = net.minecraft.util.Mth.lerp(t, 0.2f, 0.0f);
                        lb = net.minecraft.util.Mth.lerp(t, 0.0f, 0.6f);
                    } else {
                        // Outer corona fadeout to cosmic space violet
                        float t = (progress - 0.8f) / 0.2f;
                        lr = net.minecraft.util.Mth.lerp(t, 0.7f, 0.1f);
                        lg = 0.0f;
                        lb = net.minecraft.util.Mth.lerp(t, 0.6f, 0.2f);
                    }
                } else {
                    s = size * (1.01f + (float)Math.pow(progress, 1.2f) * 0.4f);
                    aa = (0.05f * (float)Math.pow(1.0f - progress, 2.0f)) * 1.0f;
                }

                BufferBuilder atmBuilder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
                renderCubeOrSphere(atmBuilder, matrix, s, 0.0f, 0.0f, lr, lg, lb, aa, isSphere);
                BufferUploader.drawWithShader(atmBuilder.buildOrThrow());
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

    private static void renderCubeOrSphere(VertexConsumer bufferbuilder, Matrix4f matrix, float size, float uOffset, float vOffset, float r, float g, float b, float a, boolean isSphere) {
        int G = isSphere ? 16 : 1;
        
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
                        p0.normalize().mul(size);
                        p1.normalize().mul(size);
                        p2.normalize().mul(size);
                        p3.normalize().mul(size);
                    } else {
                        p0.mul(size);
                        p1.mul(size);
                        p2.mul(size);
                        p3.mul(size);
                    }

                    float texUA = Mth.lerp((float) uA, u0, u1) + uOffset;
                    float texUB = Mth.lerp((float) uB, u0, u1) + uOffset;
                    float texVA = Mth.lerp((float) vA, v0, v1) + vOffset;
                    float texVB = Mth.lerp((float) vB, v0, v1) + vOffset;

                    bufferbuilder.addVertex(matrix, (float)p0.x, (float)p0.y, (float)p0.z).setColor(r, g, b, a).setUv(texUA, texVA);
                    bufferbuilder.addVertex(matrix, (float)p1.x, (float)p1.y, (float)p1.z).setColor(r, g, b, a).setUv(texUA, texVB);
                    bufferbuilder.addVertex(matrix, (float)p2.x, (float)p2.y, (float)p2.z).setColor(r, g, b, a).setUv(texUB, texVB);
                    bufferbuilder.addVertex(matrix, (float)p3.x, (float)p3.y, (float)p3.z).setColor(r, g, b, a).setUv(texUB, texVA);
                }
            }
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

    private static long computeColor(Vector3D P, Vector3D L, int gx, int gy) {
        double d = P.normalize().dotProduct(L);
        
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
