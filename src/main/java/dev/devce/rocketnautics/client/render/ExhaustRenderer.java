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

    /** 
     * The loaded shader instance registered via {@link dev.devce.rocketnautics.client.ClientModEvents#onRegisterShaders}.
     * Minecraft manages compiling, reloading, and binding this shader program.
     */
    @Nullable
    public static ShaderInstance exhaustShader = null;

    /**
     * Custom RenderType for the exhaust plume.
     * Delegates rendering to the translucent pass of Minecraft, sorting it properly with clouds, water, and particles.
     */
    private static RenderType exhaustRenderType = null;

    public static RenderType getExhaustRenderType() {
        if (exhaustRenderType == null) {
            exhaustRenderType = RenderType.create(
                "exhaust_plume",
                DefaultVertexFormat.POSITION_TEX_COLOR,
                VertexFormat.Mode.TRIANGLES,
                256,
                false, // useDelegate
                true,  // needsSorting (Crucial for transparency sorting with clouds/water/weather)
                RenderType.CompositeState.builder()
                    .setShaderState(new RenderStateShard.ShaderStateShard(() -> exhaustShader != null ? exhaustShader : net.minecraft.client.renderer.GameRenderer.getPositionColorShader()))
                    .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY) // Standard alpha blending
                    .setWriteMaskState(RenderStateShard.COLOR_WRITE) // Color only, do not write depth to allow beautiful alpha blend of layers
                    .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST) // Test depth so it respects solid blocks
                    .setCullState(RenderStateShard.NO_CULL) // Double-sided
                    .createCompositeState(false)
            );
        }
        return exhaustRenderType;
    }

    /**
     * Renders a highly realistic multi-layered rocket exhaust plume shaped like a pyramid.
     * Uses custom GLSL shaders and draws into MultiBufferSource for correct transparency sorting.
     *
     * @param ms           The current PoseStack.
     * @param buffer       The MultiBufferSource.
     * @param throttle     The current engine throttle [0, 1].
     * @param ignitionTick Tick progress of the engine ignition/startup [0, 100].
     * @param direction    Direction the thruster is facing (exhaust direction is opposite).
     */
    public static void renderExhaustPlume(PoseStack ms, MultiBufferSource buffer, float throttle, float ignitionTick, Direction direction) {
        if (throttle <= 0.01f) return;

        // Startup ignition scale factor
        float startupScale = Mth.clamp(ignitionTick / 40.0f, 0.0f, 1.0f);
        float activeThrottle = throttle * startupScale;

        // Base dimensions of the engine nozzle face (0.5 block half-width)
        float baseHalfWidth = 0.38f;

        long time = System.currentTimeMillis();
        float animTime = (time % 100000L) / 1000.0f; // Seconds elapsed

        Matrix4f matrix = ms.last().pose();

        // Pass uniforms to shader if available
        if (exhaustShader != null) {
            var uTime = exhaustShader.getUniform("u_Time");
            if (uTime != null) {
                uTime.set(animTime);
            }
            var uThrottle = exhaustShader.getUniform("u_Throttle");
            if (uThrottle != null) {
                uThrottle.set(activeThrottle);
            }
        }

        // Draw into the MultiBufferSource using our custom RenderType.
        // This defers rendering to the proper translucent sorting pass of Minecraft.
        VertexConsumer consumer = buffer.getBuffer(getExhaustRenderType());

        // ================= MULTI-LAYERED EXHAUST PYRAMIDS =================
        // Order of drawing matters for alpha blending!
        // We render from OUTSIDE to INSIDE (Outer -> Intermediate -> Core) so that
        // the inner white-hot core is layered on top of the outer shell.

        // --- LAYER 1: OUTER PLUME (Longest, widest, most translucent, purple-pink) ---
        float l1Width = 0.42f;
        float l1Length = 7.8f * activeThrottle;
        drawPlumeLayer(consumer, matrix, l1Width, l1Length, 255, 200, 255, 75); // soft alpha

        // --- LAYER 2: INTERMEDIATE PLUME (Medium length, medium width, fiery orange) ---
        float l2Width = 0.35f;
        float l2Length = 6.0f * activeThrottle;
        drawPlumeLayer(consumer, matrix, l2Width, l2Length, 255, 150, 50, 130); // medium alpha

        // --- LAYER 3: CORE PLUME (Shortest, narrowest, bright white core) ---
        float l3Width = 0.28f;
        float l3Length = 4.5f * activeThrottle;
        drawPlumeLayer(consumer, matrix, l3Width, l3Length, 255, 255, 255, 220); // strong solid center
    }

    /**
     * Draws a single pyramid plume layer with UV coordinates.
     */
    private static void drawPlumeLayer(VertexConsumer consumer, Matrix4f matrix, float halfWidth, float length, int r, int g, int b, int a) {
        // Base corners at the nozzle (Y = 0)
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

    private static void addPlumeTriangle(VertexConsumer consumer, Matrix4f matrix, 
                                         Vector3f v1, Vector3f v2, Vector3f v3, 
                                         float u1, float v1_uv, float u2, float v2_uv, float u3, float v3_uv,
                                         int r, int g, int b, int a) {
        consumer.addVertex(matrix, v1.x, v1.y, v1.z).setUv(u1, v1_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v2.x, v2.y, v2.z).setUv(u2, v2_uv).setColor(r, g, b, a);
        consumer.addVertex(matrix, v3.x, v3.y, v3.z).setUv(u3, v3_uv).setColor(r, g, b, a);
    }
}
