package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.List;

/**
 * High-Performance Cyberpunk/Sci-Fi Debug & FPS Monitor Widget.
 * Optimized to run with ZERO GC allocations during frame rendering.
 */
@EventBusSubscriber(value = Dist.CLIENT)
public class FpsMonitorOverlay {
    public static boolean enabled = false;

    private static final int MAX_SAMPLES = 60;
    private static final float[] fpsRing = new float[MAX_SAMPLES];
    private static int fpsRingHead = 0;
    private static int fpsRingCount = 0;

    private static long lastGcCount = -1;
    private static long lastGcTime = -1;
    private static float gcRate = 0.0f;

    private static long lastFrameTimeNano = System.nanoTime();
    private static float lastFrameMs = 16.6f;

    // Cached strings to avoid string allocations during render
    private static String cachedFpsStr = "0";
    private static String cachedFrameTimeStr = "16.6 ms";
    private static String cachedRamStr = "0MB / 0MB (0%)";
    private static String cachedGcStr = "0.0%";
    private static String cachedPosStr = "Pos: X:0 Y:0 Z:0";
    private static String cachedAltStr = "0m";
    private static String cachedVelStr = "0.00 m/s";
    private static String cachedSmaStr = "0m";
    private static String cachedEccStr = "0.0000";
    private static String cachedPlanetStr = "Solar Sphere";
    private static String cachedChunkStr = "0";
    private static String cachedEntitiesStr = "0";
    private static String cachedParticlesStr = "0";

    private static int updateTicker = 0;
    private static final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private static final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        if (!enabled) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        // Sample FPS every tick
        int currentFps = mc.getFps();
        fpsRing[fpsRingHead] = (float) currentFps;
        fpsRingHead = (fpsRingHead + 1) % MAX_SAMPLES;
        if (fpsRingCount < MAX_SAMPLES) fpsRingCount++;

        // Throttle heavier stats calculation (GC, RAM, Strings) to every 5 ticks (4 times per second)
        updateTicker++;
        if (updateTicker % 5 != 0) return;

        // 1. GC Stats
        long totalGcCount = 0;
        long totalGcTime = 0;
        for (int i = 0; i < gcBeans.size(); i++) {
            GarbageCollectorMXBean gc = gcBeans.get(i);
            long count = gc.getCollectionCount();
            long time = gc.getCollectionTime();
            if (count > 0) totalGcCount += count;
            if (time > 0) totalGcTime += time;
        }

        if (lastGcCount != -1) {
            long timeDiff = totalGcTime - lastGcTime;
            gcRate = Mth.clamp(timeDiff / 250.0f * 100.0f, 0.0f, 100.0f);
        }
        lastGcCount = totalGcCount;
        lastGcTime = totalGcTime;
        cachedGcStr = (int) gcRate + "." + (int) ((gcRate * 10) % 10) + "%";

        // 2. RAM Stats
        MemoryUsage heap = memBean.getHeapMemoryUsage();
        long usedMb = heap.getUsed() >> 20;
        long maxMb = heap.getMax() >> 20;
        int memPercent = maxMb > 0 ? (int) ((usedMb * 100) / maxMb) : 0;
        cachedRamStr = usedMb + "MB / " + maxMb + "MB (" + memPercent + "%)";

        // 3. Simple text string caches
        cachedFpsStr = Integer.toString(currentFps);

        Vec3 p = mc.player.position();
        cachedPosStr = "Pos: X:" + (int) p.x + " Y:" + (int) p.y + " Z:" + (int) p.z;

        double camY = mc.gameRenderer.getMainCamera().getPosition().y + SkyDataHandler.getHeightOffsetForLevel(mc.level.dimension());
        cachedAltStr = (int) camY + "m";

        cachedChunkStr = mc.level.getChunkSource().gatherStats();
        cachedEntitiesStr = mc.levelRenderer.getEntityStatistics();
        cachedParticlesStr = mc.particleEngine.countParticles();

        if (DeepSpaceHelper.isDeepSpace(mc.level) && DeepSpaceHandler.getUniverse() != null && DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dsPos = DeepSpaceHandler.getReceivedPosition();
            var orbit = dsPos.getCurrentOrbit();
            double vel = orbit.getPVCoordinates().getVelocity().getNorm();
            cachedVelStr = (int) vel + "." + (int) ((vel * 100) % 100) + " m/s";
            cachedSmaStr = (int) orbit.getA() + "m";

            double ecc = orbit.getE();
            cachedEccStr = Double.toString((double) Math.round(ecc * 10000.0) / 10000.0);

            CubePlanet nearestPlanet = DeepSpaceHandler.getUniverse().getPlanetByDimension(mc.level.dimension());
            cachedPlanetStr = nearestPlanet != null ? nearestPlanet.frame().getName() : "Solar Sphere";
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiLayerEvent.Post event) {
        if (!enabled) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.options.hideGui) return;

        // Calculate frame time
        long now = System.nanoTime();
        lastFrameMs = (now - lastFrameTimeNano) / 1_000_000.0f;
        lastFrameTimeNano = now;

        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        int width = 230;
        int x = 10;
        int y = 10;

        boolean inDeepSpace = DeepSpaceHelper.isDeepSpace(mc.level);
        int baseHeight = inDeepSpace ? 250 : 185;

        // Render Panel Backdrop
        g.fill(x, y, x + width, y + baseHeight, 0xDD0B0F19);
        g.fill(x, y, x + width, y + 2, 0xFF00E5FF);          // Neon Cyan Top
        g.fill(x, y + baseHeight - 1, x + width, y + baseHeight, 0x4400E5FF);
        g.fill(x, y, x + 1, y + baseHeight, 0x4400E5FF);
        g.fill(x + width - 1, y, x + width, y + baseHeight, 0x4400E5FF);

        int curY = y + 6;

        // Header
        g.drawString(font, "⚡ COSMONAUTICS FPS & TELEMETRY ⚡", x + 10, curY, 0xFF00E5FF, false);
        curY += 14;

        g.fill(x + 6, curY, x + width - 6, curY + 1, 0x33FFFFFF);
        curY += 6;

        // --- PERFORMANCE SECTION ---
        int currentFps = mc.getFps();
        int fpsColor = currentFps >= 60 ? 0xFF00FF66 : (currentFps >= 30 ? 0xFFFFCC00 : 0xFFFF3344);
        g.drawString(font, "FPS: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedFpsStr, x + 35, curY, fpsColor, true);

        g.drawString(font, "FrameTime: ", x + 90, curY, 0xFFAAAAAA, false);
        cachedFrameTimeStr = (int) lastFrameMs + "." + (int) ((lastFrameMs * 10) % 10) + " ms";
        g.drawString(font, cachedFrameTimeStr, x + 155, curY, 0xFF00E5FF, false);
        curY += 12;

        // Mini FPS Graph
        renderMiniGraph(g, x + 8, curY, width - 16, 25, 0, 144, 0xFF00E5FF);
        curY += 29;

        // RAM Heap
        g.drawString(font, "RAM Heap: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedRamStr, x + 65, curY, 0xFF00FF66, false);
        curY += 12;

        // GC & Stats
        g.drawString(font, "GC Rate: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedGcStr, x + 60, curY, gcRate > 15.0f ? 0xFFFF3344 : 0xFF88FF88, false);

        g.drawString(font, "Chunks: ", x + 115, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedChunkStr, x + 160, curY, 0xFFDDDDDD, false);
        curY += 12;

        g.drawString(font, "Entities: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedEntitiesStr, x + 60, curY, 0xFFDDDDDD, false);

        g.drawString(font, "Particles: ", x + 125, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedParticlesStr, x + 180, curY, 0xFF00FFCC, false);
        curY += 14;

        g.fill(x + 6, curY, x + width - 6, curY + 1, 0x33FFFFFF);
        curY += 6;

        // --- WORLD & ASTRO TELEMETRY ---
        g.drawString(font, "🌐 ASTRO-TELEMETRY", x + 10, curY, 0xFFFFCC00, false);
        curY += 12;

        g.drawString(font, "Dimension: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, mc.level.dimension().location().getPath(), x + 70, curY, 0xFF00FFCC, false);
        curY += 12;

        g.drawString(font, cachedPosStr, x + 8, curY, 0xFFCCCCCC, false);
        curY += 12;

        g.drawString(font, "Abs Altitude: ", x + 8, curY, 0xFFAAAAAA, false);
        g.drawString(font, cachedAltStr, x + 85, curY, 0xFF00E5FF, false);
        curY += 14;

        if (inDeepSpace) {
            g.fill(x + 6, curY, x + width - 6, curY + 1, 0x33FFFFFF);
            curY += 6;

            g.drawString(font, "🚀 DEEP SPACE KINEMATICS", x + 10, curY, 0xFFFF00FF, false);
            curY += 12;

            if (DeepSpaceHandler.getUniverse() != null && DeepSpaceHandler.hasReceivedPosition()) {
                g.drawString(font, "Orbit Speed: ", x + 8, curY, 0xFFAAAAAA, false);
                g.drawString(font, cachedVelStr, x + 80, curY, 0xFF00FF66, false);
                curY += 12;

                g.drawString(font, "Semi-Major Axis: ", x + 8, curY, 0xFFAAAAAA, false);
                g.drawString(font, cachedSmaStr, x + 105, curY, 0xFF00E5FF, false);
                curY += 12;

                g.drawString(font, "Eccentricity: ", x + 8, curY, 0xFFAAAAAA, false);
                g.drawString(font, cachedEccStr, x + 80, curY, 0xFFFFCC00, false);
                curY += 12;

                g.drawString(font, "Linked Body: ", x + 8, curY, 0xFFAAAAAA, false);
                g.drawString(font, cachedPlanetStr, x + 80, curY, 0xFF00FFCC, false);
            } else {
                g.drawString(font, "Awaiting orbital sync...", x + 8, curY, 0xFFFF3344, false);
            }
        }
    }

    private static void renderMiniGraph(GuiGraphics g, int x, int y, int w, int h, float minVal, float maxVal, int lineColor) {
        g.fill(x, y, x + w, y + h, 0xFF10141D);

        if (fpsRingCount < 2) return;

        // Draw graph directly using ring buffer indices
        for (int i = 0; i < fpsRingCount - 1; i++) {
            int idx1 = (fpsRingHead - fpsRingCount + i + MAX_SAMPLES) % MAX_SAMPLES;
            int idx2 = (fpsRingHead - fpsRingCount + i + 1 + MAX_SAMPLES) % MAX_SAMPLES;

            float v1 = Mth.clamp(fpsRing[idx1], minVal, maxVal);
            float v2 = Mth.clamp(fpsRing[idx2], minVal, maxVal);

            int x1 = x + (i * w) / MAX_SAMPLES;
            int x2 = x + ((i + 1) * w) / MAX_SAMPLES;

            int y1 = y + h - (int) (((v1 - minVal) / (maxVal - minVal)) * h);
            int y2 = y + h - (int) (((v2 - minVal) / (maxVal - minVal)) * h);

            y1 = Mth.clamp(y1, y, y + h);
            y2 = Mth.clamp(y2, y, y + h);

            g.fill(x1, Math.min(y1, y2), x2 + 1, Math.max(y1, y2) + 1, lineColor);
        }
    }
}
