package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.PreparedTexture;
import dev.devce.rocketnautics.client.SkyHandler;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import foundry.veil.api.client.render.VeilRenderBridge;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.framebuffer.AdvancedFbo;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.lwjgl.opengl.*;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import static dev.devce.rocketnautics.client.render.spaceRenderer.UniverseRenderData.*;

public class PlanetRenderer {
    public static void render(CubePlanet planet, PoseStack ps, Camera camera, Vector3D pos, AbsoluteDate date, Frame frame, float celestialAngle, float pTick) {
        assert UniverseHelper.UNIVERSE != null;

        if (planetTextures.get(planet.id()) == null) {
            ResourceLocation bakedTex = loadBakedPlanetTexture(planet.frame().getName(), planet.id());
            ResourceLocation bakedNormalTex = loadBakedPlanetNormalTexture(planet.frame().getName(), planet.id());

            PreparedTexture texture = null;
            if (bakedTex != null && bakedNormalTex != null) {
                texture = new PreparedTexture() {
                    @Override public ResourceLocation getId() { return bakedTex; }
                    @Override public ResourceLocation getNormalId() { return bakedNormalTex; }

                    @Override
                    public void retire() {
                        Minecraft.getInstance().getTextureManager().release(bakedTex);
                        Minecraft.getInstance().getTextureManager().release(bakedNormalTex);
                    }
                };
            }

            planetTextures.put(planet.id(), texture);
        }

        float parallaxFactor = (float) (SkyHandler.SKYBOX_DISTANCE / Math.max(1, pos.getNorm()));
        float size = (float) (planet.radius() * parallaxFactor);

        ps.translate(-pos.getX() * parallaxFactor, -pos.getY() * parallaxFactor, -pos.getZ() * parallaxFactor);
        ps.pushPose();
        ps.mulPose(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()));

        Matrix4f matrix = ps.last().pose();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.enableCull();

        boolean isSphere = dev.devce.rocketnautics.RocketConfig.SERVER.planetShape.get() == dev.devce.rocketnautics.RocketConfig.PlanetShape.SPHERE;

        if (planet.extras().star()) {
            StarRenderer.render(planet, isSphere, size, parallaxFactor, camera, date, frame, pos);
            ps.popPose();
            return;
        }

        Vector3f lightDir;
        Vector3f light;
        {
            Vector3D l;
            Vector3D lightSourcePosInOurFrame = UniverseHelper.UNIVERSE.getFrameByID(planet.extras().shadowLightSourceID()).map(sourceFrame -> {
                try {
                    return sourceFrame.getStaticTransformTo(planet.orekitFrame(), date).transformPosition(Vector3D.ZERO);
                } catch (Exception e) {
                    return Vector3D.ZERO;
                }
            }).orElse(Vector3D.ZERO);

            l = lightSourcePosInOurFrame.getNormSq() > 1e-6 ? lightSourcePosInOurFrame.normalize() : new Vector3D(1, 0, 0);
            lightDir = new Vector3f((float)l.getX(), (float)l.getY(), (float)l.getZ());

            l = planet.getRotationAtTime(date).applyInverseTo(l);
            light = new Vector3f((float)l.getX(), (float)l.getY(), (float)l.getZ());
        }

        if (planetNormalShader != null) {
            planetNormalShader.safeGetUniform("LightDir").set(light);
            RenderSystem.setShader(() -> planetNormalShader);
        } else {
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        }

        PreparedTexture texture = planetTextures.get(planet.id());
        if (texture != null) {
            texture.setShaderTexture();
            RenderSystem.setShaderTexture(1, texture.getNormalId());
        }

        VertexBuffer VBO = isSphere ? SPHERE_VBO : CUBE_VBO;

        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f scaledMatrix = new Matrix4f(matrix).scale(size);
        ShaderInstance activeShader = planetNormalShader != null ? planetNormalShader : RenderSystem.getShader();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);

        drawVBO(VBO, scaledMatrix, activeShader, false);

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);

        if (planet.extras().clouds()) {
            ResourceLocation cloudTexture = SkyHandler.getCloudTextureId();
            if (cloudTexture != null) {
                RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
                RenderSystem.setShaderTexture(0, cloudTexture);
                ShaderInstance cloudShader = RenderSystem.getShader();

                Matrix4f cloudMatrix = new Matrix4f(matrix).scale(size * 1.015f);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 0.45f);
                drawVBO(VBO, cloudMatrix, cloudShader, false);
                RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
            }
        }

        {
            Matrix4f shadowMatrix = new Matrix4f(matrix).scale(size * 1.016f);
            ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "shadow");
            ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
            if (shader != null) {
                shader.getUniformSafe("uLightDir").setVector(light);

                drawVBO(VBO, shadowMatrix, VeilRenderBridge.toShaderInstance(shader), false);
            }
        }


        if (planet.atmosphere() != null) {
            if (!planetAtmosphereOpticalDepthMaps.containsKey(planet.id())) {
                final int texSz = 256;

                ResourceLocation kernelId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere_tex");
                ShaderProgram kernel = VeilRenderSystem.setShader(kernelId);
                if (kernel != null && kernel.isValid() && kernel.isCompute()) {
                    int tex = GlStateManager._genTexture();
                    GlStateManager._bindTexture(tex);

                    GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R32F,
                            texSz, texSz,
                            0,
                            GL11.GL_RED, GL11.GL_FLOAT,
                            (ByteBuffer)null
                    );

                    GL11.glTexParameteri(
                            GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MIN_FILTER,
                            GL11.GL_NEAREST
                    );

                    GL11.glTexParameteri(
                            GL11.GL_TEXTURE_2D,
                            GL11.GL_TEXTURE_MAG_FILTER,
                            GL11.GL_NEAREST
                    );

                    GL42.glBindImageTexture(0, tex, 0, false, 0, GL15.GL_WRITE_ONLY, GL30.GL_R32F);

                    kernel.bind();

                    kernel.getUniformSafe("shape").setInt(isSphere ? 0 : 1);

                    kernel.getUniformSafe("rAtmosphere").setFloat(planet.atmosphere().radius());
                    kernel.getUniformSafe("densityFalloff").setFloat(planet.atmosphere().densityFalloff());

                    kernel.getUniformSafe("voxelSz").setFloat(isSphere ? 0 : 8.0f);

                    kernel.getUniformSafe("texSz").setInt(texSz);
                    kernel.getUniformSafe("numOutScatteringPoints").setInt(10);



                    int groupsX = (texSz + 7) / 8;
                    int groupsY = (texSz + 7) / 8;

                    GL43.glDispatchCompute(groupsX, groupsY, 1);

                    GL42.glMemoryBarrier(GL42.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

                    ShaderProgram.unbind();

                    planetAtmosphereOpticalDepthMaps.put(planet.id(), tex);
                }
            }

            RenderTarget target = Minecraft.getInstance().getMainRenderTarget();

            ResourceLocation shaderId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere");
            ResourceLocation fboId = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "atmosphere_fbo");

            RenderSystem.disableCull();
            RenderSystem.disableBlend();

            ShaderProgram shader = VeilRenderSystem.setShader(shaderId);
            if (shader != null && shader.isValid()) {
                AdvancedFbo atmosphereFBO = VeilRenderSystem.renderer().getFramebufferManager().getFramebuffer(fboId);

                if (atmosphereFBO != null) {
                    atmosphereFBO.bind(true);
                    shader.bind();

                    RenderSystem.activeTexture(GL13.GL_TEXTURE0);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getColorTextureId());
                    shader.getUniformSafe("sDiffuse").setInt(0);

                    RenderSystem.activeTexture(GL13.GL_TEXTURE1);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, target.getDepthTextureId());
                    shader.getUniformSafe("sDiffuseDepth").setInt(1);

                    RenderSystem.activeTexture(GL13.GL_TEXTURE2);
                    GL11.glBindTexture(GL11.GL_TEXTURE_2D, planetAtmosphereOpticalDepthMaps.getOrDefault(planet.id(), 0));
                    shader.getUniformSafe("sOpticalDepth").setInt(2);

                    shader.getUniformSafe("shape").setInt(isSphere ? 0 : 1);

                    shader.getUniformSafe("rPlanet").setFloat(100);
                    shader.getUniformSafe("rAtmosphere").setFloat(100*planet.atmosphere().radius());

                    shader.getUniformSafe("sunDir").setVector(lightDir);
                    shader.getUniformSafe("intensity").setFloat(planet.atmosphere().intensity());
                    shader.getUniformSafe("densityFalloff").setFloat(planet.atmosphere().densityFalloff());

                    float scatteringStrength = planet.atmosphere().scatteringStrength();
                    Vector3f wavelengths = planet.atmosphere().wavelengths();
                    Vector3f scatteringCoefficients = new Vector3f(
                            (float)Math.pow(400 / wavelengths.x, 4),
                            (float)Math.pow(400 / wavelengths.y, 4),
                            (float)Math.pow(400 / wavelengths.z, 4)
                    ).mul(scatteringStrength);
                    shader.getUniformSafe("scatteringCoefficients").setVector(scatteringCoefficients);

                    Matrix4f transform = new Matrix4f()
                            .translate(new Vector3f(
                                    -(float)pos.getX(),
                                    -(float)pos.getY(),
                                    -(float)pos.getZ()
                            ).mul(parallaxFactor*100).add(camera.getPosition().toVector3f()))
                            .rotate(DeepSpaceHelper.adapt(planet.getRotationAtTime(date)).get(new Quaternionf()))
                            .scale(size);
                    Matrix4f invTransform = new Matrix4f(transform).invert();
                    shader.getUniformSafe("transform").setMatrix(transform);
                    shader.getUniformSafe("invTransform").setMatrix(invTransform);

                    shader.getUniformSafe("voxelSz").setFloat(isSphere ? 0 : 8.0f);

                    shader.getUniformSafe("numInScatteringPoints").setInt(10);

                    shader.getUniformSafe("depthTest").setInt(0);

                    GL30.glBindVertexArray(FULLSCREEN_VAO);
                    GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
                    GL30.glBindVertexArray(0);

                    ShaderProgram.unbind();

                    GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, atmosphereFBO.getId());
                    GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, target.frameBufferId);

                    GL30.glBlitFramebuffer(
                            0, 0,
                            atmosphereFBO.getWidth(), atmosphereFBO.getHeight(),
                            0, 0,
                            target.width, target.height,
                            GL11.GL_COLOR_BUFFER_BIT,
                            GL11.GL_NEAREST
                    );

                    GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, target.frameBufferId);
                }
            }
        }

        RenderSystem.defaultBlendFunc();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();

        ps.popPose();
    }

    private static String getBakedPlanetFolderName(String planetName) {
        String lower = planetName.toLowerCase();
        if (lower.contains("earth") || lower.contains("overworld")) {
            return "planet_0_earth_seed_880";
        } else if (lower.contains("moon")) {
            return "planet_1_moon_seed_881";
        } else if (lower.contains("mars")) {
            return "planet_2_mars_seed_882";
        } else if (lower.contains("mercury") || lower.contains("ice")) {
            return "planet_3_mercury_seed_883";
        } else if (lower.contains("venus") || lower.contains("giant") || lower.contains("gas")) {
            return "planet_4_venus_seed_884";
        }
        return "planet_0_earth_seed_880";
    }

    private static ResourceLocation loadBakedPlanetTexture(String planetName, int planetId) {
        String folderName = getBakedPlanetFolderName(planetName);
        Minecraft mc = Minecraft.getInstance();

        try {
            int w = 512;
            int h = 512;
            ResourceLocation firstFaceLoc = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/planet_pack/" + folderName + "/albedo_py.png");
            var firstRes = mc.getResourceManager().getResource(firstFaceLoc);
            if (firstRes.isPresent()) {
                try (java.io.InputStream is = firstRes.get().open()) {
                    NativeImage sample = NativeImage.read(is);
                    w = sample.getWidth();
                    h = sample.getHeight();
                    sample.close();
                }
            } else {
                return null;
            }

            NativeImage sheet = new NativeImage(w * 6, h, false);

            String[] faceFiles = {
                    "albedo_py.png", // 0: py
                    "albedo_ny.png", // 1: ny
                    "albedo_nz.png", // 2: nz
                    "albedo_pz.png", // 3: pz
                    "albedo_nx.png", // 4: nx
                    "albedo_px.png"  // 5: px
            };

            for (int i = 0; i < 6; i++) {
                ResourceLocation faceLoc = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/planet_pack/" + folderName + "/" + faceFiles[i]);
                var res = mc.getResourceManager().getResource(faceLoc);
                if (res.isPresent()) {
                    try (java.io.InputStream is = res.get().open()) {
                        NativeImage faceImg = NativeImage.read(is);
                        faceImg.copyRect(sheet, 0, 0, i * w, 0, w, h, false, false);
                        faceImg.close();
                    }
                } else {
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) {
                            sheet.setPixelRGBA(i * w + x, y, 0xFFFFFFFF);
                        }
                    }
                }
            }

            DynamicTexture constructed = new DynamicTexture(sheet);
            ResourceLocation id = mc.getTextureManager().register("rocketnautics_baked_planet_" + planetId, constructed);
            constructed.setFilter(false, false);
            sheet.close();
            return id;
        } catch (Exception e) {
            RocketNautics.LOGGER.error("Failed to load baked planet texture for " + planetName, e);
            return null;
        }
    }

    private static ResourceLocation loadBakedPlanetNormalTexture(String planetName, int planetId) {
        String folderName = getBakedPlanetFolderName(planetName);
        Minecraft mc = Minecraft.getInstance();

        try {
            int w = 512;
            int h = 512;
            ResourceLocation firstFaceLoc = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/planet_pack/" + folderName + "/normal_py.png");
            var firstRes = mc.getResourceManager().getResource(firstFaceLoc);
            if (firstRes.isPresent()) {
                try (java.io.InputStream is = firstRes.get().open()) {
                    NativeImage sample = NativeImage.read(is);
                    w = sample.getWidth();
                    h = sample.getHeight();
                    sample.close();
                }
            } else {
                return null;
            }

            NativeImage sheet = new NativeImage(w * 6, h, false);

            String[] faceFiles = {
                    "normal_py.png", // 0: py
                    "normal_ny.png", // 1: ny
                    "normal_nz.png", // 2: nz
                    "normal_pz.png", // 3: pz
                    "normal_nx.png", // 4: nx
                    "normal_px.png"  // 5: px
            };

            for (int i = 0; i < 6; i++) {
                ResourceLocation faceLoc = ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "textures/planet_pack/" + folderName + "/" + faceFiles[i]);
                var res = mc.getResourceManager().getResource(faceLoc);
                if (res.isPresent()) {
                    try (java.io.InputStream is = res.get().open()) {
                        NativeImage faceImg = NativeImage.read(is);
                        faceImg.copyRect(sheet, 0, 0, i * w, 0, w, h, false, false);
                        faceImg.close();
                    }
                } else {
                    for (int x = 0; x < w; x++) {
                        for (int y = 0; y < h; y++) {
                            sheet.setPixelRGBA(i * w + x, y, (255 << 24) | (255 << 16) | (128 << 8) | 128);
                        }
                    }
                }
            }

            DynamicTexture constructed = new DynamicTexture(sheet);
            ResourceLocation id = mc.getTextureManager().register("rocketnautics_baked_planet_normal_" + planetId, constructed);
            constructed.setFilter(false, false);
            sheet.close();
            return id;
        } catch (Exception e) {
            RocketNautics.LOGGER.error("Failed to load baked planet normal texture for " + planetName, e);
            return null;
        }
    }

    public static void drawVBO(VertexBuffer vbo, Matrix4f modelMatrix, ShaderInstance shader, boolean bound) {
        if (vbo == null || shader == null) return;
        if (!bound) vbo.bind();
        vbo.drawWithShader(modelMatrix, RenderSystem.getProjectionMatrix(), shader);
        if (!bound) VertexBuffer.unbind();
    }

    public static ShaderInstance planetNormalShader = null;
    private static final Map<Integer, Integer> planetAtmosphereOpticalDepthMaps = new HashMap<>();

    private static final Map<Integer, PreparedTexture> planetTextures = new HashMap<>();
}
