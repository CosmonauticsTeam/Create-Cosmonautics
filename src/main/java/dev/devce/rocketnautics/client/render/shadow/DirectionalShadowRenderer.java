package dev.devce.rocketnautics.client.render.shadow;

import com.mojang.blaze3d.shaders.Uniform;
import dev.devce.rocketnautics.client.SableSubLevelLightingHandler;
import foundry.veil.api.client.render.MatrixStack;
import foundry.veil.api.client.render.VeilLevelPerspectiveRenderer;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.event.VeilRenderLevelStageEvent;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL30;

/**
 * Directional Shadow Map Renderer for Space SubLevels.
 * Renders an orthographic depth map looking from the Sun's position towards the ship,
 * enabling real-time self-shadowing (cast shadows from pillars, walls, roofs) in any sun angle.
 */
public class DirectionalShadowRenderer {

    public static final int SHADOW_RES = 2048;
    public static final float SHADOW_BOX_SIZE = 80.0f;
    public static final float SHADOW_DISTANCE = 120.0f;

    private static AdvancedFbo shadowFbo;
    private static final Matrix4f PROJECTION_MAT = new Matrix4f();
    private static final Matrix4f LIGHT_SPACE_MAT = new Matrix4f();
    private static final Vector3d SHADOW_CAM_POS = new Vector3d();
    private static final Quaternionf SHADOW_ORIENTATION = new Quaternionf();
    private static boolean isRenderingShadow = false;

    public static void init() {
        if (shadowFbo == null) {
            shadowFbo = AdvancedFbo.withSize(SHADOW_RES, SHADOW_RES)
                .addColorTextureBuffer()
                .setDepthTextureBuffer()
                .build(true);
        }
    }

    public static void renderShadowMap(
        final VeilRenderLevelStageEvent.Stage stage,
        final LevelRenderer levelRenderer,
        final MultiBufferSource.BufferSource bufferSource,
        final MatrixStack matrixStack,
        final Matrix4fc frustumMatrix,
        final Matrix4fc projectionMatrix,
        final int renderTick,
        final DeltaTracker deltaTracker,
        final Camera camera,
        final Frustum frustum
    ) {
        if (VeilLevelPerspectiveRenderer.isRenderingPerspective()) return;
        // Render shadow map at AFTER_SKY so it is available before chunk layers (solid, cutout, translucent) render
        if (stage != VeilRenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !dev.devce.rocketnautics.api.orbit.DeepSpaceHelper.isDeepSpace(mc.level)) {
            return;
        }
        if (!dev.devce.rocketnautics.RocketConfig.CLIENT.enableSpaceLighting.get() ||
            !dev.devce.rocketnautics.RocketConfig.CLIENT.enableSpaceShadowMaps.get()) {
            return;
        }

        init();
        if (shadowFbo == null) return;

        // Sun direction in world space
        float sunX = SableSubLevelLightingHandler.getSunX();
        float sunY = SableSubLevelLightingHandler.getSunY();
        float sunZ = SableSubLevelLightingHandler.getSunZ();
        float len = (float) Math.sqrt(sunX * sunX + sunY * sunY + sunZ * sunZ);
        if (len < 1e-4f) return;
        sunX /= len; sunY /= len; sunZ /= len;

        // Texel-snapped focus point in world coordinates to completely eliminate camera movement jitter
        Vec3 camPos = camera.getPosition();
        float texelSize = (SHADOW_BOX_SIZE * 2.0f) / 2048.0f;
        double focusX = Math.floor(camPos.x / texelSize) * texelSize;
        double focusY = Math.floor(camPos.y / texelSize) * texelSize;
        double focusZ = Math.floor(camPos.z / texelSize) * texelSize;

        float eyeX = (float) (focusX + sunX * SHADOW_DISTANCE);
        float eyeY = (float) (focusY + sunY * SHADOW_DISTANCE);
        float eyeZ = (float) (focusZ + sunZ * SHADOW_DISTANCE);
        SHADOW_CAM_POS.set(eyeX, eyeY, eyeZ);

        Vector3f up = Math.abs(sunY) > 0.95f ? new Vector3f(0, 0, 1) : new Vector3f(0, 1, 0);
        SHADOW_ORIENTATION.identity().lookAlong(-sunX, -sunY, -sunZ, up.x, up.y, up.z);

        // Orthographic projection matrix
        PROJECTION_MAT.identity().ortho(
            -SHADOW_BOX_SIZE, SHADOW_BOX_SIZE,
            -SHADOW_BOX_SIZE, SHADOW_BOX_SIZE,
            1.0f, SHADOW_DISTANCE * 2.0f
        );

        // Standard world-space Light View Matrix locked to snapped world grid
        Matrix4f lightViewWorld = new Matrix4f().lookAt(
            eyeX, eyeY, eyeZ,
            (float) focusX, (float) focusY, (float) focusZ,
            up.x, up.y, up.z
        );

        // Convert camera viewPos (ModelViewMat * pos) -> World Space -> Shadow Light Space
        Quaternionf camRot = camera.rotation();
        Matrix4f lightViewFromCamera = new Matrix4f(lightViewWorld)
            .translate((float) camPos.x, (float) camPos.y, (float) camPos.z)
            .rotate(camRot);

        // Bias matrix to map [-1, 1] clip space to [0, 1] texture coordinates
        Matrix4f bias = new Matrix4f()
            .translate(0.5f, 0.5f, 0.5f)
            .scale(0.5f, 0.5f, 0.5f);

        LIGHT_SPACE_MAT.set(bias).mul(PROJECTION_MAT).mul(lightViewFromCamera);

        // Render depth into shadow FBO
        shadowFbo.bind(true);
        GL30.glClearColor(1.0f, 1.0f, 1.0f, 0.0f);
        shadowFbo.clear();

        Matrix4f modelView = new Matrix4f();
        isRenderingShadow = true;
        VeilLevelPerspectiveRenderer.render(
            shadowFbo,
            modelView,
            PROJECTION_MAT,
            SHADOW_CAM_POS,
            SHADOW_ORIENTATION,
            SHADOW_BOX_SIZE / 8.0f,
            deltaTracker,
            false
        );
        isRenderingShadow = false;

        AdvancedFbo.unbind();

        // Push uniforms to chunk shaders
        updateChunkShaders();
    }

    public static void updateChunkShaders() {
        bindToShader(GameRenderer.getRendertypeSolidShader());
        bindToShader(GameRenderer.getRendertypeCutoutMippedShader());
        bindToShader(GameRenderer.getRendertypeCutoutShader());
        bindToShader(GameRenderer.getRendertypeTranslucentShader());
        bindToShader(GameRenderer.getRendertypeTripwireShader());
    }

    public static void bindToShader(ShaderInstance shader) {
        if (shader == null || shadowFbo == null) return;

        Uniform lightMat = shader.getUniform("LightSpaceMat");
        if (lightMat != null) {
            lightMat.set(LIGHT_SPACE_MAT);
        }

        shader.setSampler("SunShadowSampler", shadowFbo.getDepthTextureAttachment());
    }

    public static boolean isRenderingShadow() {
        return isRenderingShadow;
    }

    public static AdvancedFbo getShadowFbo() {
        return shadowFbo;
    }
}
