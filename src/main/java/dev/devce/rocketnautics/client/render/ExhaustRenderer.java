package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

@OnlyIn(Dist.CLIENT)
public class ExhaustRenderer {

    @Nullable
    public static ShaderInstance exhaustShader = null;

    @Nullable
    public static ShaderInstance rcsShader = null;

    private static RenderType exhaustRenderType = null;
    private static RenderType rcsRenderType = null;

    public static RenderType getExhaustRenderType() {
        if (exhaustRenderType == null) {
            exhaustRenderType = RenderType.create(
                "exhaust_plume",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> exhaustShader != null ? exhaustShader : net.minecraft.client.renderer.GameRenderer.getPositionColorShader()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
            );
        }
        return exhaustRenderType;
    }

    public static RenderType getRcsRenderType() {
        if (rcsRenderType == null) {
            rcsRenderType = RenderType.create(
                "rcs_plume",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false,
                true,
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> rcsShader != null ? rcsShader : net.minecraft.client.renderer.GameRenderer.getPositionColorShader()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                    .setCullState(RenderStateShard.NO_CULL)
                    .createCompositeState(false)
            );
        }
        return rcsRenderType;
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction) {
        renderExhaustPlume(ms, buffer, throttle, ignitionTick, direction, false, 1.0f);
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction, boolean isRCS) {
        renderExhaustPlume(ms, buffer, throttle, ignitionTick, direction, isRCS, 1.0f);
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction, boolean isRCS, float scale) {
        if (throttle <= 0.01f) return;

        float startupScale = isRCS ? 1.0f : Mth.clamp(ignitionTick / 40.0f, 0.0f, 1.0f);
        float activeThrottle = throttle * startupScale;

        long time = System.currentTimeMillis();
        float animTime = (time % 100000L) / 1000.0f;

        Matrix4f matrix = ms.last().pose();

        // 1. Bind appropriate shader uniforms
        ShaderInstance activeShader = isRCS ? rcsShader : exhaustShader;
        if (activeShader != null) {
            var uTime = activeShader.getUniform("u_Time");
            if (uTime != null) {
                uTime.set(animTime);
            }
            var uThrottle = activeShader.getUniform("u_Throttle");
            if (uThrottle != null) {
                uThrottle.set(activeThrottle);
            }
        }

        // 2. Draw using the correct shader RenderType
        VertexConsumer consumer = buffer.getBuffer(isRCS ? getRcsRenderType() : getExhaustRenderType());

        if (isRCS) {
            // ── RCS COLD GAS PLUME ──────────────────────────────────────────
            // Wide flaring gas jet representing expanding gas in space.
            // Renders with rcsShader (icy colors) and flares out from the nozzle.
            float gasWidth = 0.22f * scale; // Max expanded tail half-width
            float gasLength = 1.3f * activeThrottle * scale;
            
            // Draw expanding/flaring gas jet
            // Start half-width set to 0.07f to match the physical diameter of the RCS block nozzle perfectly.
            drawExpandingPlumeLayer(consumer, matrix, 0.07f * scale, gasWidth, gasLength, 255, 255, 255, 120);
            return;
        }

        // ── MAIN ENGINE FLAME PLUME ─────────────────────────────────────
        // Order of drawing matters: Outer -> Intermediate -> Core
        // Renders as a standard converging pyramid to cover the nozzle exit properly.
        
        // --- LAYER 1: OUTER PLUME (Longest, widest, purple-pink) ---
        float l1Width = 0.42f * scale;
        float l1Length = 17.8f * activeThrottle * scale;
        drawStandardPlumeLayer(consumer, matrix, l1Width, l1Length, 255, 200, 255, 75);

        // --- LAYER 2: INTERMEDIATE PLUME (Medium length, fiery orange) ---
        float l2Width = 0.35f * scale;
        float l2Length = 16.0f * activeThrottle * scale;
        drawStandardPlumeLayer(consumer, matrix, l2Width, l2Length, 255, 150, 50, 130);

        // --- LAYER 3: CORE PLUME (Shortest, bright white core) ---
        float l3Width = 0.28f * scale;
        float l3Length = 14.5f * activeThrottle * scale;
        drawStandardPlumeLayer(consumer, matrix, l3Width, l3Length, 255, 255, 255, 220);
    }

    /**
     * Draws a single standard pyramid plume layer for main engine flames.
     * Starts wide at the nozzle (Y=0) and converges to a point at the tail (Y=-length).
     */
    private static void drawStandardPlumeLayer(VertexConsumer consumer, Matrix4f matrix, float halfWidth, float length, int r, int g, int b, int a) {
        Vector3f p1 = new Vector3f(-halfWidth, 0.0f, -halfWidth);
        Vector3f p2 = new Vector3f(halfWidth, 0.0f, -halfWidth);
        Vector3f p3 = new Vector3f(halfWidth, 0.0f, halfWidth);
        Vector3f p4 = new Vector3f(-halfWidth, 0.0f, halfWidth);

        // Tip of the plume (Y = -length)
        Vector3f tip = new Vector3f(0.0f, -length, 0.0f);

        // Side 1: (p1 -> p2 -> tip)
        addPlumeTriangle(consumer, matrix, p1, p2, tip, 0.0f, 0.0f, 0.25f, 0.0f, 0.125f, 1.0f, r, g, b, a);
        // Side 2: (p2 -> p3 -> tip)
        addPlumeTriangle(consumer, matrix, p2, p3, tip, 0.25f, 0.0f, 0.5f, 0.0f, 0.375f, 1.0f, r, g, b, a);
        // Side 3: (p3 -> p4 -> tip)
        addPlumeTriangle(consumer, matrix, p3, p4, tip, 0.5f, 0.0f, 0.75f, 0.0f, 0.625f, 1.0f, r, g, b, a);
        // Side 4: (p4 -> p1 -> tip)
        addPlumeTriangle(consumer, matrix, p4, p1, tip, 0.75f, 0.0f, 1.0f, 0.0f, 0.875f, 1.0f, r, g, b, a);
    }

    /**
     * Draws a single expanding pyramid plume layer for RCS cold-gas jets.
     * The nozzle end (Y=0) matches startHalfWidth, and the tail (Y=-length) matches maxHalfWidth.
     */
    private static void drawExpandingPlumeLayer(VertexConsumer consumer, Matrix4f matrix, float startHalfWidth, float maxHalfWidth, float length, int r, int g, int b, int a) {
        // Throat/neck at the engine nozzle face (Y = 0)
        float startWidth = startHalfWidth;
        Vector3f p1 = new Vector3f(-startWidth, 0.0f, -startWidth);
        Vector3f p2 = new Vector3f(startWidth, 0.0f, -startWidth);
        Vector3f p3 = new Vector3f(startWidth, 0.0f, startWidth);
        Vector3f p4 = new Vector3f(-startWidth, 0.0f, startWidth);

        // Wide expanded base at the end of the plume (Y = -length)
        Vector3f t1 = new Vector3f(-maxHalfWidth, -length, -maxHalfWidth);
        Vector3f t2 = new Vector3f(maxHalfWidth, -length, -maxHalfWidth);
        Vector3f t3 = new Vector3f(maxHalfWidth, -length, maxHalfWidth);
        Vector3f t4 = new Vector3f(-maxHalfWidth, -length, maxHalfWidth);

        // Side 1: p1 -> p2 -> t2 -> t1
        addPlumeTriangle(consumer, matrix, p1, p2, t2, 0.0f, 0.0f, 0.25f, 0.0f, 0.25f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p1, t2, t1, 0.0f, 0.0f, 0.25f, 1.0f, 0.0f, 1.0f, r, g, b, a);

        // Side 2: p2 -> p3 -> t3 -> t2
        addPlumeTriangle(consumer, matrix, p2, p3, t3, 0.25f, 0.0f, 0.5f, 0.0f, 0.5f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p2, t3, t2, 0.25f, 0.0f, 0.5f, 1.0f, 0.25f, 1.0f, r, g, b, a);

        // Side 3: p3 -> p4 -> t4 -> t3
        addPlumeTriangle(consumer, matrix, p3, p4, t4, 0.5f, 0.0f, 0.75f, 0.0f, 0.75f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p3, t4, t3, 0.5f, 0.0f, 0.75f, 1.0f, 0.5f, 1.0f, r, g, b, a);

        // Side 4: p4 -> p1 -> t1 -> t4
        addPlumeTriangle(consumer, matrix, p4, p1, t1, 0.75f, 0.0f, 1.0f, 0.0f, 1.0f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p4, t1, t4, 0.75f, 0.0f, 1.0f, 1.0f, 0.75f, 1.0f, r, g, b, a);
    }

    private static void addPlumeTriangle(VertexConsumer consumer, Matrix4f matrix, 
                                         Vector3f v1, Vector3f v2, Vector3f v3, 
                                         float u1, float v1_uv, float u2, float v2_uv, float u3, float v3_uv,
                                         int r, int g, int b, int a) {
        consumer.addVertex(matrix, v1.x, v1.y, v1.z).setUv(u1, v1_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z).setUv(u2, v2_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z).setUv(u3, v3_uv).setColor(r, g, b, a);
    }
}
