package dev.devce.rocketnautics.client.ui;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.client.SkyHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.devce.rocketnautics.RocketConfig;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.orbits.PositionAngleType;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.PVCoordinates;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

/**
 * System Map Screen using native DeepSpace sky stars, infinite depth clipping, and dynamic vessel movement.
 */
public class SystemMapScreen extends Screen {

    private static final ResourceLocation VESSEL_ICON = ResourceLocation.fromNamespaceAndPath("rocketnautics", "textures/gui/vessel_icon.png");

    private float pitch = 30f;
    private float yaw = 45f;
    private double zoom = 1.0;

    private boolean dragging;

    private CubePlanet focusPlanet;
    private boolean focusPlayer;

    private record ManeuverData(Vector3D originPos, Vector3D targetPos, Vector3D encounterPos, Vector3D centerPos, Orbit transferOrbit, String targetName, double deltaV, double tofHours, double distanceKm) {}
    private ManeuverData activeManeuver = null;
    private String maneuverNotice = null;
    private long maneuverNoticeTime = 0;

    private boolean contextMenuOpen = false;
    private float contextMenuX = 0;
    private float contextMenuY = 0;
    private RenderTarget contextTarget = null;

    private record RenderTarget(float sx, float sy, float radiusPixels, CubePlanet planet, boolean isPlayer) {}
    private final List<RenderTarget> targets = new ArrayList<>();

    public SystemMapScreen() {
        super(Component.literal("System Map"));
    }

    @Override
    protected void init() {
        super.init();
        UniverseDefinition u = DeepSpaceHandler.getUniverse();
        focusPlayer = DeepSpaceHandler.hasReceivedPosition();
        if (!focusPlayer && u != null && !u.getPlanets().isEmpty()) {
            focusPlanet = u.getPlanets().iterator().next();
        }

        // Auto-calibrate initial zoom so the current context is immediately visible.
        double sma = 0;
        if (DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dp = DeepSpaceHandler.getReceivedPosition();
            if (dp.getOrbit() != null) {
                sma = Math.abs(dp.getOrbit().getA());
            }
        }
        // Fallback: use the first planet's orbital radius if no player position
        if ((sma <= 1e3 || !Double.isFinite(sma)) && u != null) {
            for (CubePlanet p : u.getPlanets()) {
                final Orbit[] oref = {null};
                p.frame().ifOrbit(o -> oref[0] = o);
                if (oref[0] != null) {
                    double candidateSma = Math.abs(oref[0].getA());
                    if (candidateSma > 1e3 && Double.isFinite(candidateSma)) {
                        sma = candidateSma;
                        break;
                    }
                }
            }
        }
        if (sma > 1e3 && Double.isFinite(sma)) {
            double targetPixels = Math.min(width, height) / 3.0;
            double targetScale = targetPixels / sma;
            // ×10: scale everything up so it fills the view comfortably
            zoom = Mth.clamp(targetScale / 1e-8 * 10.0, 1e-5, 1e7);
        }
    }

    @Override
    public void render(GuiGraphics gfx, int mx, int my, float dt) {
        targets.clear();

        // Dark background
        gfx.fill(0, 0, width, height, 0xFF000000);

        UniverseDefinition universe = DeepSpaceHandler.getUniverse();
        AbsoluteDate date = DeepSpaceHandler.getPredictedUniverseDate(dt);
        if (date == null) date = DeepSpaceHelper.EPOCH;
        final AbsoluteDate fd = date;

        Frame root = (universe != null && universe.getFrameByID(0).isPresent())
                ? universe.getFrameByID(0).get() : Frame.getRoot();

        Vector3D focusPos = getFocusWorldPos(fd, root);

        // 1. Render background dark canvas & stars
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        renderDeepSpaceSky(gfx, dt);

        // Scale factor for map display
        double scale = 1e-8 * zoom;

        // 2. Setup 3D Environment with depth testing enabled so closer planets occlude farther planets
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        GlStateManager._clear(1024, false);
        RenderSystem.enableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        gfx.pose().pushPose();
        // Translate to screen center at Z = 2000f (center of Minecraft GUI depth buffer)
        gfx.pose().translate(width / 2f, height / 2f, 2000f);
        // Uniform 3D scale with Y inverted for GUI screen space
        gfx.pose().scale((float) scale, -(float) scale, (float) scale);
        gfx.pose().mulPose(Axis.XP.rotationDegrees(pitch));
        gfx.pose().mulPose(Axis.YP.rotationDegrees(yaw));
        gfx.pose().translate(-(float) focusPos.getX(), -(float) focusPos.getY(), -(float) focusPos.getZ());

        // 3. KSP-style context-relative orbit rendering.
        //    Determine the "context frame" — the body the player (or focus planet) is currently orbiting.
        //    Only draw orbits that belong to this frame so the map stays readable at every scale.
        Frame contextFrame = root; // default: solar system root
        if (DeepSpaceHandler.hasReceivedPosition()) {
            contextFrame = DeepSpaceHandler.getReceivedPosition().getFrame();
        } else if (focusPlanet != null) {
            final Orbit[] oref = {null};
            focusPlanet.frame().ifOrbit(o -> oref[0] = o);
            if (oref[0] != null) contextFrame = oref[0].getFrame();
        }
        final Frame finalContextFrame = contextFrame;

        if (universe != null) {
            Collection<CubePlanet> planets = universe.getPlanets();
            for (CubePlanet p : planets) {
                final Orbit[] oref = {null};
                p.frame().ifOrbit(o -> oref[0] = o);
                if (oref[0] == null) continue;

                Orbit orbit = oref[0];
                Frame parentFrame = orbit.getFrame();
                Vector3D parentPos = parentFrame.getTransformTo(root, fd).transformPosition(Vector3D.ZERO);

                if (parentFrame == finalContextFrame) {
                    // Primary orbits: siblings in same SOI — bright, solid
                    renderOrbitRelative(gfx, orbit, parentPos, root, fd, 0.25f, 0.65f, 1.0f, 0.75f);
                } else if (p.orekitFrame() == finalContextFrame) {
                    // This planet IS the context body — show its own orbit around its parent dimly
                    // (gives "exit SOI" context, like KSP dashed parent orbit)
                    renderOrbitRelative(gfx, orbit, parentPos, root, fd, 0.15f, 0.35f, 0.6f, 0.3f);
                }
                // All other orbits (distant solar-scale etc.) are hidden
            }
        }

        // Render player vessel orbit in its own SOI frame (always visible, bright orange-yellow)
        if (DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dp = DeepSpaceHandler.getReceivedPosition();
            if (dp.getOrbit() != null) {
                Orbit orbit = dp.getOrbit();
                Frame parentFrame = orbit.getFrame();
                Vector3D parentPos = parentFrame.getTransformTo(root, fd).transformPosition(Vector3D.ZERO);

                renderOrbitRelative(gfx, orbit, parentPos, root, fd, 1.0f, 0.85f, 0.1f, 0.95f);
            }
        }

        // 3b. Holo Table Multi-Segment Trajectory Prediction & SOI Encounter Intersects
        int predSteps = RocketConfig.CLIENT.orbitPredictionSteps.getAsInt();
        if (predSteps > 0 && DeepSpaceHandler.hasReceivedPosition()) {
            Frame largestFrame = (focusPlanet != null) ? focusPlanet.orekitFrame()
                    : (focusPlayer && DeepSpaceHandler.hasReceivedPosition() ? DeepSpaceHandler.getReceivedPosition().getFrame() : root);

            Iterator<Vector3D> iter = DeepSpaceHandler.getPositionPrediction(largestFrame, predSteps);
            if (iter != null) {
                float[] cycle = new float[] {
                    (System.currentTimeMillis() * 0.05f) % 20f,
                    (System.currentTimeMillis() * 0.2f) % 80f
                };
                renderChainedPositions(gfx, iter, gfx.bufferSource(), v -> false, cycle, 0.5f, 0.3f, false);
                renderIntersects(gfx, gfx.bufferSource(), largestFrame, universe != null ? universe.getPlanets() : new ArrayList<>());
            }
        }

        // 3c. Render Active Transfer Maneuver Trajectory Arc in 3D (Dynamic live tracking parent body)
        if (activeManeuver != null && activeManeuver.transferOrbit() != null) {
            Frame parentFrame = activeManeuver.transferOrbit().getFrame();
            Vector3D liveCenterPos = parentFrame.getTransformTo(root, fd).transformPosition(Vector3D.ZERO);
            renderTransferArc(gfx, activeManeuver.transferOrbit(), liveCenterPos, 0.9f, 0.9f, 0.9f, 0.9f);
        }

        // 4. Render Planets using real models with compact scale (2.5x smaller) and 3D wireframe outlines
        Lighting.setupFor3DItems();
        if (universe != null) {
            for (CubePlanet p : universe.getPlanets()) {
                Vector3D pPos = p.getPosition(fd, root);
                Vector3f sc = projectToScreen(pPos, focusPos, scale);

                boolean sel = !focusPlayer && p == focusPlanet;
                boolean hov = Math.hypot(mx - sc.x, my - sc.y) <= 10.0;

                gfx.pose().pushPose();
                gfx.pose().translate((float) pPos.getX(), (float) pPos.getY(), (float) pPos.getZ());

                // Proportional world-space radius based on real planet radius.
                // Minimum 3px on screen guaranteed (KSP style: tiny dot far away, proper size when zoomed)
                double proportionalRadius = p.radius() * 0.15;
                double minVisualRadius = 3.0 / scale; // always at least 3px on screen
                double visualRadius = Math.max(minVisualRadius, proportionalRadius);
                double renderPixels = visualRadius * scale;
                double hitRadiusPixels = Math.max(5.0, renderPixels);
                double holoScale = p.radius() / visualRadius;

                boolean needsFallback = DeepSpaceHandler.renderHoloPlanet(
                        p, Vector3D.ZERO, gfx.pose(), fd, holoScale, gfx.bufferSource(), 0.9f, 0.9f, 1.0f, 0.95f
                );

                if (needsFallback) {
                    renderColoredCube(gfx, (float) visualRadius, p.extras().star() ? 0xFFFFCC00 : 0xFF3388FF);
                }

                // Wireframe highlight only when selected/hovered
                if (sel || hov) {
                    int highlightColor = sel ? 0xFFFFD700 : 0xFF00E5FF;
                    float shellSize = (float) (visualRadius * 1.05);
                    gfx.pose().pushPose();
                    gfx.pose().mulPose(DeepSpaceHelper.adapt(p.getRotationAtTime(fd)).get(new Quaternionf()));
                    renderHighlightWireframe(gfx, shellSize, highlightColor, 0.95f);
                    gfx.pose().popPose();
                }

                gfx.pose().popPose();

                targets.add(new RenderTarget(sc.x, sc.y, (float) hitRadiusPixels, p, false));
            }
        }

        // 5. Render Player Vessel — fixed world-space billboard, scales naturally with zoom
        if (DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dp = DeepSpaceHandler.getReceivedPosition();
            if (dp.getOrbit() != null) {
                Vector3D vPos = dp.getOrbit().getPosition(fd, root);
                Vector3f sc = projectToScreen(vPos, focusPos, scale);

                boolean sel = focusPlayer;
                boolean hov = Math.hypot(mx - sc.x, my - sc.y) <= 10.0;

                // Fixed world-space size — scales naturally with zoom, same as planets
                double vSize = 200_000.0; // 200 km display size — visible dot
                double vSizePixels = Math.max(4.0, vSize * scale); // for hit detection

                gfx.pose().pushPose();
                gfx.pose().translate((float) vPos.getX(), (float) vPos.getY(), (float) vPos.getZ());
                // Un-rotate to make the quad screen-aligned (billboard)
                gfx.pose().mulPose(Axis.YP.rotationDegrees(-yaw));
                gfx.pose().mulPose(Axis.XP.rotationDegrees(-pitch));
                // Y is inverted in our 3D setup
                gfx.pose().scale(1f, -1f, 1f);

                renderVesselBillboard(gfx, (float) vSize, sel || hov);

                gfx.pose().popPose();

                targets.add(new RenderTarget(sc.x, sc.y, (float) vSizePixels, null, true));
            }
        }

        gfx.bufferSource().endBatch();
        gfx.pose().popPose();

        Lighting.setupForFlatItems();
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();

        // 6. HUD Labels & Overlay & Context Menu
        renderHUD(gfx, mx, my);
    }

    private void renderDeepSpaceSky(GuiGraphics gfx, float dt) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        float celestialAngle = mc.level.getTimeOfDay(dt);
        SkyHandler.renderSystemMapStars(gfx, pitch, yaw, celestialAngle, width, height);
    }

    private void renderOrbitRelative(GuiGraphics gfx, Orbit orbit, Vector3D parentPos, Frame root, AbsoluteDate date, float r, float g, float b, float a) {
        if (orbit == null) return;
        double period = orbit.getKeplerianPeriod();
        if (Double.isInfinite(period)) return;

        if (orbit instanceof KeplerianOrbit kep) {
            MultiBufferSource.BufferSource bufferSource = gfx.bufferSource();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.lineStrip());

            gfx.pose().pushPose();
            gfx.pose().translate(parentPos.getX(), parentPos.getY(), parentPos.getZ());

            recurseRenderPlanetOrbit(buffer, gfx.pose(), kep, kep.getDate(), period, r, g, b, a);

            PVCoordinates coords = kep.getPVCoordinates(kep.getDate(), kep.getFrame());
            Vector3f norm = DeepSpaceHelper.adaptf(coords.getVelocity()).normalize();
            buffer.addVertex(gfx.pose().last(), DeepSpaceHelper.adaptf(coords.getPosition()))
                    .setColor(r, g, b, a)
                    .setNormal(gfx.pose().last(), norm.x(), norm.y(), norm.z());

            bufferSource.endBatch(RenderType.lineStrip());
            gfx.pose().popPose();
        }
    }

    private void recurseRenderPlanetOrbit(VertexConsumer buffer, com.mojang.blaze3d.vertex.PoseStack ms, KeplerianOrbit orbit, AbsoluteDate point, double len, float r, float g, float b, float a) {
        PVCoordinates coords = orbit.getPVCoordinates(point, orbit.getFrame());
        double ecc = orbit.getE();
        double correctedAngularVelocity = orbit.getEccentricAnomalyDot() / Mth.lerp((float)(ecc * ecc), 1.0f, (float)(1.0 - coords.getVelocity().normalize().crossProduct(coords.getAcceleration().normalize()).getNorm() / 2.0));

        double threshold = RocketConfig.CLIENT.orbitPredictionAngularThreshold.get();
        if (correctedAngularVelocity * len > Math.toRadians(threshold)) {
            recurseRenderPlanetOrbit(buffer, ms, orbit, point, len / 2.0, r, g, b, a);
            recurseRenderPlanetOrbit(buffer, ms, orbit, point.shiftedBy(len / 2.0), len / 2.0, r, g, b, a);
        } else {
            Vector3f norm = DeepSpaceHelper.adaptf(coords.getVelocity()).normalize();
            buffer.addVertex(ms.last(), DeepSpaceHelper.adaptf(coords.getPosition()))
                    .setColor(r, g, b, a)
                    .setNormal(ms.last(), norm.x(), norm.y(), norm.z());
        }
    }

    private void renderTransferArc(GuiGraphics gfx, Orbit transferOrbit, Vector3D centerPos, float r, float g, float b, float a) {
        if (transferOrbit == null) return;
        double period = transferOrbit.getKeplerianPeriod();
        double duration = Double.isInfinite(period) ? 2000.0 : period / 2.0;

        if (transferOrbit instanceof KeplerianOrbit kep) {
            MultiBufferSource.BufferSource bufferSource = gfx.bufferSource();
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.lineStrip());

            gfx.pose().pushPose();
            gfx.pose().translate(centerPos.getX(), centerPos.getY(), centerPos.getZ());

            recurseRenderPlanetOrbit(buffer, gfx.pose(), kep, kep.getDate(), duration, r, g, b, a);

            PVCoordinates coords = kep.getPVCoordinates(kep.getDate().shiftedBy(duration), kep.getFrame());
            Vector3f norm = DeepSpaceHelper.adaptf(coords.getVelocity()).normalize();
            buffer.addVertex(gfx.pose().last(), DeepSpaceHelper.adaptf(coords.getPosition()))
                    .setColor(r, g, b, a)
                    .setNormal(gfx.pose().last(), norm.x(), norm.y(), norm.z());

            bufferSource.endBatch(RenderType.lineStrip());
            gfx.pose().popPose();
        }
    }

    private int renderChainedPositions(GuiGraphics gfx, Iterator<Vector3D> iter, MultiBufferSource bufferSource, Predicate<Vector3D> stopCondition, float[] cycle, float s, float b, boolean onlyCycle) {
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.lineStrip());
        List<List<Vector3D>> cycling = new ArrayList<>(cycle.length);
        for (int i = 0; i < cycle.length; i++) {
            cycling.add(new ObjectArrayList<>());
        }
        int count = 0;
        if (iter != null && iter.hasNext()) {
            Vector3D v = iter.next();
            Vector3D norm = null;
            while (iter.hasNext()) {
                Vector3D vNext = iter.next();
                if (stopCondition.test(v)) break;
                for (int i = 0; i < cycle.length; i++) {
                    if (count == Math.ceil(cycle[i])) {
                        float partialCycle = cycle[i] % 1;
                        cycling.get(i).add(new Vector3D(partialCycle, vNext, 1 - partialCycle, v));
                    }
                }
                count++;
                if (!onlyCycle) {
                    Vector3D dif = vNext.subtract(v);
                    if (dif.getNormSq() > 1e-20) {
                        norm = dif.normalize();
                    } else if (norm == null) {
                        v = vNext;
                        continue;
                    }
                    buffer.addVertex(gfx.pose().last(), (float) v.getX(), (float) v.getY(), (float) v.getZ())
                            .setColor(0.8f, 0.8f, b, 0.8f)
                            .setNormal(gfx.pose().last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                }
                v = vNext;
            }
        }
        for (int i = 0; i < cycling.size(); i++) {
            List<Vector3D> points = cycling.get(i);
            float factor = 1 - 0.3f * i / cycle.length;
            for (Vector3D c : points) {
                gfx.pose().pushPose();
                gfx.pose().translate(c.getX(), c.getY(), c.getZ());
                DebugRenderer.renderFilledBox(gfx.pose(), bufferSource, -s, -s, -s, s, s, s, factor, factor, b, 1.0f);
                gfx.pose().popPose();
            }
        }
        return count;
    }

    private void renderIntersects(GuiGraphics gfx, MultiBufferSource bufferSource, Frame referenceFrame, Collection<CubePlanet> planets) {
        Iterator<Orbit> iter = DeepSpaceHandler.getPredictionOrbits();
        if (iter == null || !iter.hasNext()) return;
        Orbit prevOrbit = iter.next();
        float s = 0.5f;
        while (iter.hasNext()) {
            Orbit orbit = iter.next();
            gfx.pose().pushPose();
            Vector3D v = orbit.getPosition(referenceFrame);
            gfx.pose().translate(v.getX(), v.getY(), v.getZ());
            DebugRenderer.renderFilledBox(gfx.pose(), bufferSource, -s, -s, -s, s, s, s, 0f, 0.0f, 1.0f, 1.0f);
            gfx.pose().popPose();

            List<Vector3D> vPs = new ArrayList<>();
            for (CubePlanet planet : planets) {
                if (planet.orekitFrame() != prevOrbit.getFrame() && planet.orekitFrame() != orbit.getFrame()) continue;
                gfx.pose().pushPose();
                Vector3D vP = planet.getPosition(orbit.getDate(), referenceFrame);
                gfx.pose().translate(vP.getX(), vP.getY(), vP.getZ());
                DebugRenderer.renderFilledBox(gfx.pose(), bufferSource, -s, -s, -s, s, s, s, 0f, 0.0f, 1.0f, 1.0f);
                gfx.pose().popPose();
                vPs.add(vP);
            }

            if (!vPs.isEmpty()) {
                VertexConsumer buffer = bufferSource.getBuffer(RenderType.lines());
                for (Vector3D vP : vPs) {
                    Vector3D norm = v.subtract(vP).normalize();
                    buffer.addVertex(gfx.pose().last(), (float) vP.getX(), (float) vP.getY(), (float) vP.getZ())
                            .setColor(0f, 0.0f, 1.0f, 1.0f)
                            .setNormal(gfx.pose().last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                    buffer.addVertex(gfx.pose().last(), (float) v.getX(), (float) v.getY(), (float) v.getZ())
                            .setColor(0f, 0.0f, 1.0f, 1.0f)
                            .setNormal(gfx.pose().last(), (float) norm.getX(), (float) norm.getY(), (float) norm.getZ());
                }
            }
            prevOrbit = orbit;
        }
    }

    private void renderVesselBillboard(GuiGraphics gfx, float halfSize, boolean highlight) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShaderTexture(0, VESSEL_ICON);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f m = gfx.pose().last().pose();

        float s = halfSize;
        float r = highlight ? 1.0f : 0.95f;
        float g = highlight ? 0.95f : 0.9f;
        float b = highlight ? 0.9f : 1.0f;
        float a = 0.95f;

        // Screen-aligned quad (billboard): X right, Y up, Z toward camera
        buf.addVertex(m, -s,  s, 0).setUv(0, 0).setColor(r, g, b, a);
        buf.addVertex(m, -s, -s, 0).setUv(0, 1).setColor(r, g, b, a);
        buf.addVertex(m,  s, -s, 0).setUv(1, 1).setColor(r, g, b, a);
        buf.addVertex(m,  s,  s, 0).setUv(1, 0).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private void renderBillboardRing(GuiGraphics gfx, float radius, int color) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINE_STRIP, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = gfx.pose().last().pose();

        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = ((color >> 24) & 0xFF) / 255f;

        int segments = 24;
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float x = (float) (Math.cos(angle) * radius);
            float y = (float) (Math.sin(angle) * radius);
            buf.addVertex(m, x, y, 0).setColor(r, g, b, a);
        }

        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    private void renderColoredCube(GuiGraphics gfx, float halfSize, int color) {
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = gfx.pose().last().pose();

        float s = halfSize;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;
        float a = 0.95f;

        // 1. South (+Z)
        buf.addVertex(m, -s, -s, s).setColor(r, g, b, a);
        buf.addVertex(m, s, -s, s).setColor(r, g, b, a);
        buf.addVertex(m, s, s, s).setColor(r, g, b, a);
        buf.addVertex(m, -s, s, s).setColor(r, g, b, a);

        // 2. North (-Z)
        buf.addVertex(m, s, -s, -s).setColor(r * 0.7f, g * 0.7f, b * 0.7f, a);
        buf.addVertex(m, -s, -s, -s).setColor(r * 0.7f, g * 0.7f, b * 0.7f, a);
        buf.addVertex(m, -s, s, -s).setColor(r * 0.7f, g * 0.7f, b * 0.7f, a);
        buf.addVertex(m, s, s, -s).setColor(r * 0.7f, g * 0.7f, b * 0.7f, a);

        // 3. Top (+Y)
        buf.addVertex(m, -s, s, -s).setColor(r * 0.9f, g * 0.9f, b * 0.9f, a);
        buf.addVertex(m, s, s, -s).setColor(r * 0.9f, g * 0.9f, b * 0.9f, a);
        buf.addVertex(m, s, s, s).setColor(r * 0.9f, g * 0.9f, b * 0.9f, a);
        buf.addVertex(m, -s, s, s).setColor(r * 0.9f, g * 0.9f, b * 0.9f, a);

        // 4. Bottom (-Y)
        buf.addVertex(m, -s, -s, s).setColor(r * 0.5f, g * 0.5f, b * 0.5f, a);
        buf.addVertex(m, s, -s, s).setColor(r * 0.5f, g * 0.5f, b * 0.5f, a);
        buf.addVertex(m, s, -s, -s).setColor(r * 0.5f, g * 0.5f, b * 0.5f, a);
        buf.addVertex(m, -s, -s, -s).setColor(r * 0.5f, g * 0.5f, b * 0.5f, a);

        // 5. East (+X)
        buf.addVertex(m, s, -s, s).setColor(r * 0.8f, g * 0.8f, b * 0.8f, a);
        buf.addVertex(m, s, -s, -s).setColor(r * 0.8f, g * 0.8f, b * 0.8f, a);
        buf.addVertex(m, s, s, -s).setColor(r * 0.8f, g * 0.8f, b * 0.8f, a);
        buf.addVertex(m, s, s, s).setColor(r * 0.8f, g * 0.8f, b * 0.8f, a);

        // 6. West (-X)
        buf.addVertex(m, -s, -s, -s).setColor(r * 0.6f, g * 0.6f, b * 0.6f, a);
        buf.addVertex(m, -s, -s, s).setColor(r * 0.6f, g * 0.6f, b * 0.6f, a);
        buf.addVertex(m, -s, s, s).setColor(r * 0.6f, g * 0.6f, b * 0.6f, a);
        buf.addVertex(m, -s, s, -s).setColor(r * 0.6f, g * 0.6f, b * 0.6f, a);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private void renderHighlightWireframe(GuiGraphics gfx, float halfSize, int color, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Tesselator tess = Tesselator.getInstance();
        BufferBuilder buf = tess.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f m = gfx.pose().last().pose();

        float s = halfSize;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        // Bottom 4 edges
        buf.addVertex(m, -s, -s, -s).setColor(r, g, b, alpha); buf.addVertex(m, s, -s, -s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, -s, -s).setColor(r, g, b, alpha); buf.addVertex(m, s, -s, s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, -s, s).setColor(r, g, b, alpha); buf.addVertex(m, -s, -s, s).setColor(r, g, b, alpha);
        buf.addVertex(m, -s, -s, s).setColor(r, g, b, alpha); buf.addVertex(m, -s, -s, -s).setColor(r, g, b, alpha);

        // Top 4 edges
        buf.addVertex(m, -s, s, -s).setColor(r, g, b, alpha); buf.addVertex(m, s, s, -s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, s, -s).setColor(r, g, b, alpha); buf.addVertex(m, s, s, s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, s, s).setColor(r, g, b, alpha); buf.addVertex(m, -s, s, s).setColor(r, g, b, alpha);
        buf.addVertex(m, -s, s, s).setColor(r, g, b, alpha); buf.addVertex(m, -s, s, -s).setColor(r, g, b, alpha);

        // Vertical 4 edges
        buf.addVertex(m, -s, -s, -s).setColor(r, g, b, alpha); buf.addVertex(m, -s, s, -s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, -s, -s).setColor(r, g, b, alpha); buf.addVertex(m, s, s, -s).setColor(r, g, b, alpha);
        buf.addVertex(m, s, -s, s).setColor(r, g, b, alpha); buf.addVertex(m, s, s, s).setColor(r, g, b, alpha);
        buf.addVertex(m, -s, -s, s).setColor(r, g, b, alpha); buf.addVertex(m, -s, s, s).setColor(r, g, b, alpha);

        BufferUploader.drawWithShader(buf.buildOrThrow());
    }

    private Vector3f projectToScreen(Vector3D pos, Vector3D focusPos, double scale) {
        Vector3D rel = pos.subtract(focusPos).scalarMultiply(scale);

        double radY = Math.toRadians(yaw);
        double cosY = Math.cos(radY), sinY = Math.sin(radY);
        double rx = cosY * rel.getX() + sinY * rel.getZ();
        double ry = rel.getY();
        double rz = -sinY * rel.getX() + cosY * rel.getZ();

        double radP = Math.toRadians(pitch);
        double cosP = Math.cos(radP), sinP = Math.sin(radP);
        double ry2 = cosP * ry - sinP * rz;

        float sx = (float)(width / 2.0 + rx);
        float sy = (float)(height / 2.0 - ry2);
        return new Vector3f(sx, sy, 0);
    }

    private void renderHUD(GuiGraphics gfx, int mx, int my) {
        // 1. Planet & Vessel selection indicators (no text labels)
        for (RenderTarget t : targets) {
            boolean sel = t.isPlayer ? focusPlayer : (!focusPlayer && t.planet == focusPlanet);
            float dist = (float) Math.hypot(mx - t.sx, my - t.sy);
            float hitRadius = Math.max(t.radiusPixels + 6.0f, 12.0f);
            boolean hov = dist <= hitRadius;

            // Draw a small dot indicator below the planet when selected/hovered
            if (sel || hov) {
                int col = sel ? 0xFFFFD700 : 0xFF00E5FF;
                int dx = (int) t.sx;
                int dy = (int) (t.sy + t.radiusPixels + 4);
                gfx.fill(dx - 1, dy - 1, dx + 2, dy + 2, col);
            }
        }

        // 2. Active Maneuver Reference Markers (geometry only, no text)
        if (activeManeuver != null) {
            double scale = 1e-8 * zoom;
            AbsoluteDate date = DeepSpaceHandler.getPredictedUniverseDate(0);
            if (date == null) date = DeepSpaceHelper.EPOCH;
            UniverseDefinition universe = DeepSpaceHandler.getUniverse();
            Frame root = (universe != null && universe.getFrameByID(0).isPresent())
                    ? universe.getFrameByID(0).get() : Frame.getRoot();
            Vector3D focusPos = getFocusWorldPos(date, root);

            Orbit tOrb = activeManeuver.transferOrbit();
            Frame parentFrame = tOrb != null ? tOrb.getFrame() : root;
            Vector3D liveCenterPos = parentFrame.getTransformTo(root, date).transformPosition(Vector3D.ZERO);

            Vector3D liveOrigin;
            Vector3D liveEncounter;

            if (tOrb != null) {
                double period = tOrb.getKeplerianPeriod();
                double duration = Double.isInfinite(period) ? 2000.0 : period / 2.0;
                AbsoluteDate tStart = tOrb.getDate();
                AbsoluteDate tEnd = tStart.shiftedBy(duration);

                liveOrigin = liveCenterPos.add(tOrb.getPVCoordinates(tStart, parentFrame).getPosition());
                liveEncounter = liveCenterPos.add(tOrb.getPVCoordinates(tEnd, parentFrame).getPosition());
            } else {
                liveOrigin = liveCenterPos.add(activeManeuver.originPos().subtract(activeManeuver.centerPos()));
                liveEncounter = liveCenterPos.add(activeManeuver.encounterPos().subtract(activeManeuver.centerPos()));
            }

            Vector3f scTransfer = projectToScreen(liveOrigin, focusPos, scale);
            Vector3f scEncounter = projectToScreen(liveEncounter, focusPos, scale);

            // Transfer marker — white cross
            int tx = (int) scTransfer.x;
            int ty = (int) scTransfer.y;
            gfx.fill(tx - 3, ty - 1, tx + 4, ty + 2, 0xCCFFFFFF);
            gfx.fill(tx - 1, ty - 3, tx + 2, ty + 4, 0xCCFFFFFF);

            // Encounter marker — cyan diamond
            int ex = (int) scEncounter.x;
            int ey = (int) scEncounter.y;
            gfx.fill(ex - 3, ey - 1, ex + 4, ey + 2, 0xCC00E5FF);
            gfx.fill(ex - 1, ey - 3, ex + 2, ey + 4, 0xCC00E5FF);
        }

        // Maneuver Executed Notice Banner (geometry-only flash)
        if (maneuverNotice != null) {
            if (System.currentTimeMillis() - maneuverNoticeTime < 3500) {
                gfx.fill(width / 2 - 160, 32, width / 2 + 160, 52, 0xEE112211);
                gfx.renderOutline(width / 2 - 160, 32, 320, 20, 0xFF00FF66);
            } else {
                maneuverNotice = null;
            }
        }

        // Render Context Menu (icon-based, no text)
        if (contextMenuOpen && contextTarget != null) {
            int cx = (int) contextMenuX;
            int cy = (int) contextMenuY;

            int menuWidth = 52;
            int menuHeight = 52;

            // Clamp context menu inside screen bounds
            cx = Mth.clamp(cx, 10, width - menuWidth - 10);
            cy = Mth.clamp(cy, 10, height - menuHeight - 10);

            gfx.fill(cx, cy, cx + menuWidth, cy + menuHeight, 0xF0090C12);
            gfx.renderOutline(cx, cy, menuWidth, menuHeight, 0x8800E5FF);

            // Option 1: Focus Camera — target reticle icon (cyan square)
            boolean hovFocus = mx >= cx + 6 && mx <= cx + menuWidth - 6 && my >= cy + 6 && my <= cy + 22;
            gfx.fill(cx + 6, cy + 6, cx + menuWidth - 6, cy + 22, hovFocus ? 0xFF1E3A55 : 0xFF0F1E30);
            gfx.renderOutline(cx + 6, cy + 6, menuWidth - 12, 16, hovFocus ? 0xFF00E5FF : 0x5500E5FF);
            // Small reticle: two corner brackets
            int bx = cx + menuWidth / 2;
            int by = cy + 14;
            gfx.fill(bx - 4, by - 3, bx - 2, by - 1, 0xFF00E5FF);
            gfx.fill(bx + 2, by - 3, bx + 4, by - 1, 0xFF00E5FF);
            gfx.fill(bx - 4, by + 1, bx - 2, by + 3, 0xFF00E5FF);
            gfx.fill(bx + 2, by + 1, bx + 4, by + 3, 0xFF00E5FF);
            gfx.fill(bx - 1, by - 1, bx + 1, by + 1, 0xFF00E5FF);

            // Option 2: Transfer Maneuver — arrow/orbit icon (purple)
            boolean hovMan = mx >= cx + 6 && mx <= cx + menuWidth - 6 && my >= cy + 26 && my <= cy + 46;
            gfx.fill(cx + 6, cy + 26, cx + menuWidth - 6, cy + 46, hovMan ? 0xFF3A1055 : 0xFF1A0830);
            gfx.renderOutline(cx + 6, cy + 26, menuWidth - 12, 20, hovMan ? 0xFFE040FB : 0x55E040FB);
            // Small arrow icon
            int ax = cx + menuWidth / 2;
            int ay = cy + 36;
            gfx.fill(ax - 5, ay - 1, ax + 2, ay + 1, 0xFFE040FB);
            gfx.fill(ax + 1, ay - 3, ax + 5, ay + 1, 0xFFE040FB);
            gfx.fill(ax + 1, ay + 1, ax + 5, ay + 3, 0xFFE040FB);
        }
    }

    private String formatShortName(String rawName) {
        if (rawName == null || rawName.isEmpty()) return "Body";
        if (rawName.equalsIgnoreCase("overworld")) return "Earth";
        if (rawName.equalsIgnoreCase("moon")) return "Moon";
        if (rawName.equalsIgnoreCase("sun")) return "Sun";
        if (rawName.equalsIgnoreCase("player vessel") || rawName.equalsIgnoreCase("vessel")) return "Vessel";
        return rawName.substring(0, 1).toUpperCase() + rawName.substring(1).toLowerCase();
    }

    private Vector3D getFocusWorldPos(AbsoluteDate date, Frame root) {
        if (focusPlayer && DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dp = DeepSpaceHandler.getReceivedPosition();
            if (dp.getOrbit() != null) return dp.getOrbit().getPosition(date, root);
        }
        if (focusPlanet != null) return focusPlanet.getPosition(date, root);
        return Vector3D.ZERO;
    }

    private ManeuverData calculateManeuver(RenderTarget t, Vector3D focusPos, AbsoluteDate fd, Frame root) {
        Vector3D targetPos = t.isPlayer ? Vector3D.ZERO : t.planet.getPosition(fd, root);
        Vector3D originPos = focusPos;
        String name = t.isPlayer ? "Player Vessel" : t.planet.frame().getName();

        Vector3D centerPos = Vector3D.ZERO;
        final Frame[] parentFrameRef = {root};

        // Use player vessel's exact orbit position and parent frame if available
        if (DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dp = DeepSpaceHandler.getReceivedPosition();
            if (dp.getOrbit() != null) {
                originPos = dp.getOrbit().getPosition(fd, root);
                parentFrameRef[0] = dp.getOrbit().getFrame();
                centerPos = parentFrameRef[0].getTransformTo(root, fd).transformPosition(Vector3D.ZERO);
            }
        }

        if (centerPos.getNormSq() < 1e-6 && !t.isPlayer && t.planet != null) {
            final Orbit[] oref = {null};
            t.planet.frame().ifOrbit(o -> oref[0] = o);
            if (oref[0] != null) {
                parentFrameRef[0] = oref[0].getFrame();
                centerPos = parentFrameRef[0].getTransformTo(root, fd).transformPosition(Vector3D.ZERO);
            }
        }

        Vector3D rTargetRel = targetPos.subtract(centerPos);
        double dist2 = Math.max(1e5, rTargetRel.getNorm());
        double dist1 = Math.max(1e5, originPos.subtract(centerPos).getNorm());

        // Hohmann Transfer semi-major axis & time of flight
        double aTx = (dist1 + dist2) / 2.0;
        double mu = 3.986e14;
        double tofSec = Math.PI * Math.sqrt(Math.pow(aTx, 3) / mu);

        // Calculate phase angle to target at optimal burn time
        double omegaTarget = Math.sqrt(mu / Math.pow(dist2, 3));
        double targetMoveAngle = omegaTarget * tofSec;
        double phaseAngle = Math.PI - targetMoveAngle;

        // Orbital plane basis vectors
        Vector3D uTarget = dist2 > 1.0 ? rTargetRel.normalize() : Vector3D.PLUS_I;
        Vector3D originRel = originPos.subtract(centerPos);
        Vector3D normal = originRel.crossProduct(rTargetRel);
        if (normal.getNormSq() < 1e-6) normal = Vector3D.PLUS_K;
        else normal = normal.normalize();

        Vector3D vTarget = normal.crossProduct(uTarget).normalize();

        // Optimal burn position on origin orbit track for Phase Angle window
        Vector3D burnRel = uTarget.scalarMultiply(dist1 * Math.cos(-phaseAngle))
                                  .add(vTarget.scalarMultiply(dist1 * Math.sin(-phaseAngle)));
        Vector3D burnPos = centerPos.add(burnRel);

        // 180 degree encounter position on target orbit opposite to burn position
        Vector3D encounterPos = centerPos.subtract(burnRel.normalize().scalarMultiply(dist2));

        double vA = Math.sqrt(mu / dist1);
        double vB = Math.sqrt(mu / dist2);
        double vTx1 = Math.sqrt(Math.abs(mu * (2.0 / dist1 - 1.0 / aTx)));
        double vTx2 = Math.sqrt(Math.abs(mu * (2.0 / dist2 - 1.0 / aTx)));

        double dv1 = Math.abs(vTx1 - vA);
        double dv2 = Math.abs(vB - vTx2);
        double totalDv = Math.max(320.0, Math.min(2800.0, (dv1 + dv2) * 0.1));

        double tofHours = Math.max(0.5, Math.min(360.0, tofSec / 3600.0));
        double distanceKm = Math.max(120.0, burnPos.distance(encounterPos) / 1000.0);

        // Construct real Orekit Keplerian Orbit for the transfer trajectory
        double ecc = Math.abs(dist2 - dist1) / (dist1 + dist2);
        Orbit transferOrbit = new KeplerianOrbit(
            aTx, ecc, 0.0, 0.0, 0.0, 0.0,
            PositionAngleType.TRUE, parentFrameRef[0], fd, mu
        );

        return new ManeuverData(burnPos, targetPos, encounterPos, centerPos, transferOrbit, name, totalDv, tofHours, distanceKm);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int btn) {

        // Context Menu button clicks
        if (contextMenuOpen && contextTarget != null) {
            int menuWidth = 52;
            int menuHeight = 52;
            int cx = Mth.clamp((int) contextMenuX, 10, width - menuWidth - 10);
            int cy = Mth.clamp((int) contextMenuY, 10, height - menuHeight - 10);

            // Focus Camera (top slot)
            if (mx >= cx + 6 && mx <= cx + menuWidth - 6 && my >= cy + 6 && my <= cy + 22) {
                focusPlayer = contextTarget.isPlayer;
                focusPlanet = contextTarget.isPlayer ? null : contextTarget.planet;
                contextMenuOpen = false;
                return true;
            }

            // Transfer Maneuver (bottom slot)
            if (mx >= cx + 6 && mx <= cx + menuWidth - 6 && my >= cy + 26 && my <= cy + 46) {
                UniverseDefinition universe = DeepSpaceHandler.getUniverse();
                AbsoluteDate date = DeepSpaceHandler.getPredictedUniverseDate(0);
                if (date == null) date = DeepSpaceHelper.EPOCH;
                Frame root = (universe != null && universe.getFrameByID(0).isPresent())
                        ? universe.getFrameByID(0).get() : Frame.getRoot();
                Vector3D focusPos = getFocusWorldPos(date, root);

                activeManeuver = calculateManeuver(contextTarget, focusPos, date, root);
                contextMenuOpen = false;
                return true;
            }

            contextMenuOpen = false;
            return true;
        }

        // Left/Right click directly on 3D Target Model
        for (RenderTarget t : targets) {
            float hitRadius = Math.max(t.radiusPixels + 4.0f, 10.0f);
            if (Math.hypot(mx - t.sx, my - t.sy) <= hitRadius) {
                contextTarget = t;
                contextMenuOpen = true;
                contextMenuX = (float) mx;
                contextMenuY = (float) my;
                return true;
            }
        }

        contextMenuOpen = false;
        dragging = true;
        return super.mouseClicked(mx, my, btn);
    }

    @Override
    public boolean mouseReleased(double mx, double my, int btn) {
        dragging = false;
        return super.mouseReleased(mx, my, btn);
    }

    @Override
    public boolean mouseDragged(double mx, double my, int btn, double dx, double dy) {
        if (dragging) {
            yaw += (float)(dx * 0.4f);
            pitch = Mth.clamp(pitch + (float)(dy * 0.4f), -89f, 89f);
            return true;
        }
        return super.mouseDragged(mx, my, btn, dx, dy);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        zoom = Mth.clamp(zoom * Math.pow(1.25, sy), 0.0000001, 10000000.0);
        return true;
    }

    @Override
    public boolean isPauseScreen() { return false; }
}
