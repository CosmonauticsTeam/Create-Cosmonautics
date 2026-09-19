package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL30;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;

import static dev.devce.rocketnautics.client.render.spaceRenderer.UniverseRenderData.FULLSCREEN_VAO;

public class StarRenderer {
    public static void render(CubePlanet planet, boolean isSphere, float sz, float parallaxFactor, Camera camera, AbsoluteDate date, Frame frame, Vector3D pos) {
        RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

        ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "star");
        ResourceLocation fboId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "star_fbo");

        RenderSystem.disableCull();
        RenderSystem.disableBlend();

        ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
        if (shader != null && shader.isValid()) {
            AdvancedFbo starFBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(fboId);

            if (starFBO != null) {
                starFBO.bind(true);
                shader.bind();

                shader.getUniformSafe("shape").setInt(isSphere ? 0 : 1);

                shader.getUniformSafe("rCore").setFloat(100);
                shader.getUniformSafe("rPlasma").setFloat(100*planet.starProperties().radius());

                shader.getUniformSafe("intensity").setFloat(planet.starProperties().intensity());

                shader.getUniformSafe("densityFalloff").setFloat(planet.starProperties().densityFalloff());

                shader.getUniformSafe("color").setVector(planet.starProperties().color());

                Matrix4f transform = new Matrix4f()
                        .translate(new Vector3f(
                                -(float)pos.getX(),
                                -(float)pos.getY(),
                                -(float)pos.getZ()
                        ).mul(parallaxFactor*100).add(camera.getPosition().toVector3f()))
                        .rotate(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()))
                        .scale(sz);
                Matrix4f invTransform = new Matrix4f(transform).invert();
                shader.getUniformSafe("transform").setMatrix(transform);
                shader.getUniformSafe("invTransform").setMatrix(invTransform);

                shader.getUniformSafe("voxelSz").setFloat(isSphere ? 0 : 16.0f);

                shader.getUniformSafe("numInScatteringPoints").setInt(2);

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
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, starFBO.getColorTextureAttachment(0).getId());
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

        Vector3D p = planet.getPosition(date, frame);
        Vector3f lightPos = new Vector3f((float)p.getX(), (float)p.getY(), (float)p.getZ());

        LensFlareRenderer.enqueue(lightPos);
    }
}
