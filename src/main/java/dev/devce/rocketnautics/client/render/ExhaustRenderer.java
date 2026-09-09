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
        renderExhaustPlume(ms, buffer, throttle, ignitionTick, direction, false, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction, boolean isRCS) {
        renderExhaustPlume(ms, buffer, throttle, ignitionTick, direction, isRCS, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction, boolean isRCS, float scale) {
        renderExhaustPlume(ms, buffer, throttle, ignitionTick, direction, isRCS, scale, 1.0f, 1.0f, 1.0f);
    }

    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction, boolean isRCS, float scale, float r, float g, float b) {
        if (throttle <= 0.01f) return;

        float startupScale = isRCS ? 1.0f : (ignitionTick < 8 ? (1.75f - ignitionTick * 0.09f) : 1.0f);
        float activeThrottle = throttle * startupScale;

        long time = System.currentTimeMillis();
        float animTime = (time % 100000L) / 1000.0f;

        Matrix4f matrix = ms.last().pose();

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

        VertexConsumer consumer = buffer.getBuffer(isRCS ? getRcsRenderType() : getExhaustRenderType());

        if (isRCS) {
            float gasWidth = 0.22f * scale;
            float gasLength = 1.3f * activeThrottle * scale;
            drawExpandingPlumeLayer(consumer, matrix, 0.07f * scale, gasWidth, gasLength, (int)(r * 255), (int)(g * 255), (int)(b * 255), 120);
            return;
        }

        float redMult = r;
        float greenMult = g;
        float blueMult = b;
        
        if (Math.abs(r - 1.0f) < 0.01f && Math.abs(g - 1.0f) < 0.01f && Math.abs(b - 1.0f) < 0.01f) {
            redMult = 1.0f;
            greenMult = 0.5f;
            blueMult = 0.1f;
        }

        float outerStartWidth = 0.30f * scale;
        float outerBulgeWidth = 0.58f * scale;
        float outerBulgeY = -1.5f * activeThrottle * scale;
        float outerLength = 15.0f * activeThrottle * scale;
        drawSupersonicPlumeLayer(consumer, matrix, outerStartWidth, outerBulgeWidth, outerBulgeY, outerLength, (int)(redMult * 255), (int)(greenMult * 255), (int)(blueMult * 255), 90);

        float midStartWidth = 0.22f * scale;
        float midBulgeWidth = 0.42f * scale;
        float midBulgeY = -1.2f * activeThrottle * scale;
        float midLength = 9.0f * activeThrottle * scale;
        drawSupersonicPlumeLayer(consumer, matrix, midStartWidth, midBulgeWidth, midBulgeY, midLength, (int)(redMult * 255), (int)(greenMult * 255), (int)(blueMult * 255), 145);

        float coreStartWidth = 0.14f * scale;
        float coreBulgeWidth = 0.26f * scale;
        float coreBulgeY = -0.9f * activeThrottle * scale;
        float coreLength = 5.5f * activeThrottle * scale;
        drawSupersonicPlumeLayer(consumer, matrix, coreStartWidth, coreBulgeWidth, coreBulgeY, coreLength, 255, 255, 255, 220);

        Vector3f t1 = new Vector3f(-0.28f * scale, -0.01f, -0.28f * scale);
        Vector3f t2 = new Vector3f(0.28f * scale, -0.01f, -0.28f * scale);
        Vector3f t3 = new Vector3f(0.28f * scale, -0.01f, 0.28f * scale);
        Vector3f t4 = new Vector3f(-0.28f * scale, -0.01f, 0.28f * scale);
        addPlumeTriangle(consumer, matrix, t1, t2, t3, 0, 0, 0.5f, 0, 0.5f, 0.1f, 255, 255, 255, 240);
        addPlumeTriangle(consumer, matrix, t1, t3, t4, 0, 0, 0.5f, 0.1f, 0, 0.1f, 255, 255, 255, 240);
    }

    private static void drawSupersonicPlumeLayer(VertexConsumer consumer, Matrix4f matrix, 
                                                 float startWidth, float bulgeWidth, float bulgeY, float length, 
                                                 int r, int g, int b, int a) {
        Vector3f n1 = new Vector3f(-startWidth, 0.0f, -startWidth);
        Vector3f n2 = new Vector3f(startWidth, 0.0f, -startWidth);
        Vector3f n3 = new Vector3f(startWidth, 0.0f, startWidth);
        Vector3f n4 = new Vector3f(-startWidth, 0.0f, startWidth);

        Vector3f b1 = new Vector3f(-bulgeWidth, bulgeY, -bulgeWidth);
        Vector3f b2 = new Vector3f(bulgeWidth, bulgeY, -bulgeWidth);
        Vector3f b3 = new Vector3f(bulgeWidth, bulgeY, bulgeWidth);
        Vector3f b4 = new Vector3f(-bulgeWidth, bulgeY, bulgeWidth);

        Vector3f tip = new Vector3f(0.0f, -length, 0.0f);

        addPlumeTriangle(consumer, matrix, n1, n2, b2, 0.0f, 0.0f, 0.25f, 0.0f, 0.25f, 0.2f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, n1, b2, b1, 0.0f, 0.0f, 0.25f, 0.2f, 0.0f, 0.2f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, n2, n3, b3, 0.25f, 0.0f, 0.5f, 0.0f, 0.5f, 0.2f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, n2, b3, b2, 0.25f, 0.0f, 0.5f, 0.2f, 0.25f, 0.2f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, n3, n4, b4, 0.5f, 0.0f, 0.75f, 0.0f, 0.75f, 0.2f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, n3, b4, b3, 0.5f, 0.0f, 0.75f, 0.2f, 0.5f, 0.2f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, n4, n1, b1, 0.75f, 0.0f, 1.0f, 0.0f, 1.0f, 0.2f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, n4, b1, b4, 0.75f, 0.0f, 1.0f, 0.2f, 0.75f, 0.2f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, b1, b2, tip, 0.0f, 0.2f, 0.25f, 0.2f, 0.125f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, b2, b3, tip, 0.25f, 0.2f, 0.5f, 0.2f, 0.375f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, b3, b4, tip, 0.5f, 0.2f, 0.75f, 0.2f, 0.625f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, b4, b1, tip, 0.75f, 0.2f, 1.0f, 0.2f, 0.875f, 1.0f, r, g, b, a);
    }

    public static void renderIonPlume(PoseStack ms, MultiBufferSource buffer, float throttle, Direction direction) {
        if (throttle <= 0.01f) return;

        long time = System.currentTimeMillis();
        float animTime = (time % 100000L) / 1000.0f;
        Matrix4f matrix = ms.last().pose();

        if (rcsShader != null) {
            var uTime = rcsShader.getUniform("u_Time");
            if (uTime != null) uTime.set(animTime * 1.5f);
            var uThrottle = rcsShader.getUniform("u_Throttle");
            if (uThrottle != null) uThrottle.set(throttle);
        }

        VertexConsumer consumer = buffer.getBuffer(getRcsRenderType());

        float startNeck = 0.375f;
        float endWidth = 0.44f + (throttle * 0.12f);
        float length = 1.3f * throttle + 0.35f;

        drawExpandingPlumeLayer(consumer, matrix, startNeck, endWidth, length, 60, 220, 255, 140);
        drawExpandingPlumeLayer(consumer, matrix, 0.25f, endWidth * 0.70f, length * 0.85f, 220, 255, 255, 240);
    }

    private static void drawStandardPlumeLayer(VertexConsumer consumer, Matrix4f matrix, float halfWidth, float length, int r, int g, int b, int a) {
        Vector3f p1 = new Vector3f(-halfWidth, 0.0f, -halfWidth);
        Vector3f p2 = new Vector3f(halfWidth, 0.0f, -halfWidth);
        Vector3f p3 = new Vector3f(halfWidth, 0.0f, halfWidth);
        Vector3f p4 = new Vector3f(-halfWidth, 0.0f, halfWidth);

        Vector3f tip = new Vector3f(0.0f, -length, 0.0f);

        addPlumeTriangle(consumer, matrix, p1, p2, tip, 0.0f, 0.0f, 0.25f, 0.0f, 0.125f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p2, p3, tip, 0.25f, 0.0f, 0.5f, 0.0f, 0.375f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p3, p4, tip, 0.5f, 0.0f, 0.75f, 0.0f, 0.625f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p4, p1, tip, 0.75f, 0.0f, 1.0f, 0.0f, 0.875f, 1.0f, r, g, b, a);
    }

    private static void drawExpandingPlumeLayer(VertexConsumer consumer, Matrix4f matrix, float startHalfWidth, float maxHalfWidth, float length, int r, int g, int b, int a) {
        float startWidth = startHalfWidth;
        Vector3f p1 = new Vector3f(-startWidth, 0.0f, -startWidth);
        Vector3f p2 = new Vector3f(startWidth, 0.0f, -startWidth);
        Vector3f p3 = new Vector3f(startWidth, 0.0f, startWidth);
        Vector3f p4 = new Vector3f(-startWidth, 0.0f, startWidth);

        Vector3f t1 = new Vector3f(-maxHalfWidth, -length, -maxHalfWidth);
        Vector3f t2 = new Vector3f(maxHalfWidth, -length, -maxHalfWidth);
        Vector3f t3 = new Vector3f(maxHalfWidth, -length, maxHalfWidth);
        Vector3f t4 = new Vector3f(-maxHalfWidth, -length, maxHalfWidth);

        addPlumeTriangle(consumer, matrix, p1, p2, t2, 0.0f, 0.0f, 0.25f, 0.0f, 0.25f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p1, t2, t1, 0.0f, 0.0f, 0.25f, 1.0f, 0.0f, 1.0f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, p2, p3, t3, 0.25f, 0.0f, 0.5f, 0.0f, 0.5f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p2, t3, t2, 0.25f, 0.0f, 0.5f, 1.0f, 0.25f, 1.0f, r, g, b, a);

        addPlumeTriangle(consumer, matrix, p3, p4, t4, 0.5f, 0.0f, 0.75f, 0.0f, 0.75f, 1.0f, r, g, b, a);
        addPlumeTriangle(consumer, matrix, p3, t4, t3, 0.5f, 0.0f, 0.75f, 1.0f, 0.5f, 1.0f, r, g, b, a);

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
