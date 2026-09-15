package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

public class SkyboxRenderer {
    public static void render(PoseStack ps, Camera camera, float visibility, float celestialAngle) {
        boolean highExposure = dev.devce.rocketnautics.RocketConfig.CLIENT.skyboxExposure.get() == dev.devce.rocketnautics.RocketConfig.SkyboxExposure.HIGH;
        ResourceLocation textureId = highExposure ? SKYBOX_HIGH_TEXTURE_ID : SKYBOX_TEXTURE_ID;

        if (visibility <= 0) return;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, textureId);

        ps.pushPose();
        ps.mulPose(com.mojang.math.Axis.XP.rotationDegrees(celestialAngle * 360.0f));

        Matrix4f matrix = ps.last().pose();
        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        float size = SKYBOX_DISTANCE;
        float alpha = visibility;

        // 4x3 Cubemap layout mapping
        float uScale = 0.25f;
        float vScale = 1.0f / 3.0f;

        // Front (Col 1, Row 1)
        float fUMin = 1.0f * uScale, fUMax = 2.0f * uScale;
        float fVMin = 1.0f * vScale, fVMax = 2.0f * vScale;
        buffer.addVertex(matrix, -size, size, -size).setUv(fUMin, fVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, -size).setUv(fUMin, fVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(fUMax, fVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, -size).setUv(fUMax, fVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Back (Col 3, Row 1)
        float bUMin = 3.0f * uScale, bUMax = 4.0f * uScale;
        float bVMin = 1.0f * vScale, bVMax = 2.0f * vScale;
        buffer.addVertex(matrix, size, size, size).setUv(bUMin, bVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(bUMin, bVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(bUMax, bVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, size).setUv(bUMax, bVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Left (Col 0, Row 1)
        float lUMin = 0.0f * uScale, lUMax = 1.0f * uScale;
        float lVMin = 1.0f * vScale, lVMax = 2.0f * vScale;
        buffer.addVertex(matrix, -size, size, size).setUv(lUMin, lVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(lUMin, lVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, -size).setUv(lUMax, lVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, -size).setUv(lUMax, lVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Right (Col 2, Row 1)
        float rUMin = 2.0f * uScale, rUMax = 3.0f * uScale;
        float rVMin = 1.0f * vScale, rVMax = 2.0f * vScale;
        buffer.addVertex(matrix, size, size, -size).setUv(rUMin, rVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(rUMin, rVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(rUMax, rVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, size).setUv(rUMax, rVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Top (Col 1, Row 0)
        float tUMin = 1.0f * uScale, tUMax = 2.0f * uScale;
        float tVMin = 0.0f * vScale, tVMax = 1.0f * vScale;
        buffer.addVertex(matrix, -size, size, size).setUv(tUMin, tVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, size, -size).setUv(tUMin, tVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, -size).setUv(tUMax, tVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, size, size).setUv(tUMax, tVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        // Bottom (Col 1, Row 2)
        float dnUMin = 1.0f * uScale, dnUMax = 2.0f * uScale;
        float dnVMin = 2.0f * vScale, dnVMax = 3.0f * vScale;
        buffer.addVertex(matrix, -size, -size, -size).setUv(dnUMin, dnVMin).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, -size, -size, size).setUv(dnUMin, dnVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, size).setUv(dnUMax, dnVMax).setColor(1.0f, 1.0f, 1.0f, alpha);
        buffer.addVertex(matrix, size, -size, -size).setUv(dnUMax, dnVMin).setColor(1.0f, 1.0f, 1.0f, alpha);

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        ps.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static final ResourceLocation SKYBOX_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/environment/skybox.png");
    private static final ResourceLocation SKYBOX_HIGH_TEXTURE_ID = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/environment/skybox_high.png");

    public static final float SKYBOX_DISTANCE = 100;
}
