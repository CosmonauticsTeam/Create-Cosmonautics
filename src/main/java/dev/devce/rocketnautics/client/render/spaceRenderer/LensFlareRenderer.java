package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devce.rocketnautics.RocketNautics;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;

import java.util.ArrayDeque;
import java.util.Queue;

import static dev.devce.rocketnautics.client.render.spaceRenderer.UniverseRenderData.FULLSCREEN_VAO;

public class LensFlareRenderer {
    private static Queue<Vector3f> queue = new ArrayDeque<>();

    public static void enqueue(Vector3f lightPos) { queue.add(lightPos); }

    public static void renderQueue(Camera camera) {
        while (!queue.isEmpty()) {
            Vector3f pos = queue.poll();
            render(pos, camera);
        }
    }

    public static void render(Vector3f lightPos, Camera camera) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

        RenderSystem.disableCull();
        RenderSystem.disableBlend();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "lens_flare");
        ResourceLocation fboId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "lens_flare_fbo");

        ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
        if (shader != null && shader.isValid()) {
            AdvancedFbo lfFBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(fboId);
            if (lfFBO != null) {
                lfFBO.bind(true);
                GL11.glViewport(0, 0, lfFBO.getWidth(), lfFBO.getHeight());

                shader.bind();

                RenderSystem.clearColor(0, 0, 0, 0);
                RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT, true);

                RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
                shader.getUniformSafe("sDiffuse").setInt(0);

                RenderSystem.activeTexture(GL13.GL_TEXTURE1);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getDepthTextureId());
                shader.getUniformSafe("sDiffuseDepth").setInt(1);

                shader.getUniformSafe("uResolution").setVector(new Vector2f(lfFBO.getWidth(), lfFBO.getHeight()));
                shader.getUniformSafe("uThreshold").setFloat(0.9f);
                shader.getUniformSafe("uChromaticAbberation").setFloat(0.01f);
    
                shader.getUniformSafe("uLightPos").setVector(lightPos);

                Vector3f toTarget = new Vector3f(lightPos).sub(camera.getPosition().toVector3f()).normalize();
                Vector3f forward = new Vector3f(0, 0, -1)
                    .rotate(camera.rotation())
                    .normalize();

                float alignment = Math.clamp((forward.dot(toTarget) + 1.0f) * 0.5f, 0.0f, 1.0f);

                shader.getUniformSafe("uIntensity").setFloat(alignment);
                shader.getUniformSafe("uRayPhase").setFloat(alignment * 2 * (float)Math.PI);

                GL30.glBindVertexArray(FULLSCREEN_VAO);
                GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                GL30.glBindVertexArray(0);

                ShaderProgram.unbind();

                target.bindWrite(true);
                RenderSystem.viewport(0, 0, target.width, target.height);

                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);

                RenderSystem.disableDepthTest();
                RenderSystem.depthMask(false);

                ResourceLocation compositeId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "composite");
                ShaderProgram composite = VeilRenderSystem.setShader(compositeId);
                if (composite != null && composite.isValid()) {
                    composite.bind();

                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, lfFBO.getColorTextureAttachment(0).getId());
                    composite.getUniformSafe("sComposite").setInt(0);

                    composite.getUniformSafe("uResolution").setVector(new Vector2f(target.width, target.height));

                    GL30.glBindVertexArray(FULLSCREEN_VAO);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    GL30.glBindVertexArray(0);

                    ShaderProgram.unbind();
                }

                RenderSystem.disableBlend();
                RenderSystem.depthMask(true);
            }
        }
    }
}
