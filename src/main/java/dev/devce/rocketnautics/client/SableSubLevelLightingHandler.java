package dev.devce.rocketnautics.client;

import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.client.render.shader.SunDirectionalShadingPreProcessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.hipparchus.geometry.euclidean.threed.Vector3D;

/**
 * Sets up sun directional lighting uniforms for Sable SubLevel chunk shaders each frame.
 *
 * Works in tandem with SunDirectionalShadingPreProcessor which injects
 * the GLSL uniforms into chunk shaders at load time.
 * This class feeds the per-frame runtime values (sun position, intensity).
 *
 * Sable's VanillaSubLevelRenderDispatcher.setupDynamicEffects() sets
 * SableEnableNormalLighting = 1.0 before each SubLevel draw call.
 * Our SunEnabled uniform mirrors that intent, so PBR lighting only fires
 * on SubLevel geometry, not on ordinary world blocks.
 */
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class SableSubLevelLightingHandler {

    // Cached current sun direction (world-space, updated every frame via AFTER_SKY stage)
    private static float sunX = 0.0f;
    private static float sunY = 1.0f;
    private static float sunZ = 0.0f;
    private static float sunIntensity = 1.0f;

    /**
     * Called once per frame at AFTER_SKY (before chunk geometry is drawn).
     * Updates sun direction and intensity based on the current dimension and orbit data.
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;

        computeSunDirection(mc.level, event.getPartialTick().getGameTimeDeltaTicks());
    }

    /**
     * Computes the normalized sun direction vector for the current frame.
     */
    private static void computeSunDirection(Level level, float deltaTick) {
        boolean isDeepSpace = DeepSpaceHelper.isDeepSpace(level);
        if (!isDeepSpace) {
            sunIntensity = 0.0f;
            sunX = 0; sunY = 1; sunZ = 0;
            applySunUniformsToChunkShaders();
            return;
        }

        if (DeepSpaceHandler.hasReceivedPosition() && DeepSpaceHandler.UNIVERSE != null) {
            try {
                float partial = Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true);
                org.orekit.time.AbsoluteDate renderDate = DeepSpaceHandler.getRenderDate(partial);
                org.hipparchus.geometry.euclidean.threed.Vector3D shipPos =
                    DeepSpaceHandler.getReceivedPosition().getPosition(renderDate);
                org.orekit.frames.Frame shipFrame = DeepSpaceHandler.getReceivedPosition().getFrame();

                // Find Sol (or first star) and compute direction FROM ship TO star
                Vector3D solDir = DeepSpaceHandler.UNIVERSE.getPlanets().stream()
                    .filter(p -> p.extras() != null && p.extras().star())
                    .map(sol -> {
                        try {
                            return sol.posInMyFrame(renderDate, shipPos, shipFrame).negate();
                        } catch (Exception e) {
                            return null;
                        }
                    })
                    .filter(v -> v != null && v.getNormSq() > 1e-6)
                    .findFirst()
                    .map(Vector3D::normalize)
                    .orElse(new Vector3D(1, 0, 0));

                sunX = (float) solDir.getX();
                sunY = (float) solDir.getY();
                sunZ = (float) solDir.getZ();
                sunIntensity = 1.0f;
            } catch (Exception ignored) {
                // Fallback: angled sun
                sunX = 0.577f;
                sunY = 0.707f;
                sunZ = 0.408f;
                sunIntensity = 1.0f;
            }
        } else {
            sunX = 0.577f;
            sunY = 0.707f;
            sunZ = 0.408f;
            sunIntensity = 1.0f;
        }

        // Push to all core chunk shaders
        applySunUniformsToChunkShaders();
    }

    /**
     * Applies sun uniforms to all standard chunk shaders in GameRenderer as well as the active shader.
     */
    public static void applySunUniformsToChunkShaders() {
        uploadSunUniforms(GameRenderer.getRendertypeSolidShader(), false);
        uploadSunUniforms(GameRenderer.getRendertypeCutoutMippedShader(), false);
        uploadSunUniforms(GameRenderer.getRendertypeCutoutShader(), false);
        uploadSunUniforms(GameRenderer.getRendertypeTranslucentShader(), false);
        uploadSunUniforms(GameRenderer.getRendertypeTripwireShader(), false);

        ShaderInstance current = RenderSystem.getShader();
        if (current != null) {
            uploadSunUniforms(current, false);
        }
    }

    /**
     * Uploads sun direction uniforms to a specific shader.
     */
    public static void uploadSunUniforms(ShaderInstance shader, boolean onSubLevel) {
        if (shader == null) return;

        Uniform dir = shader.getUniform(SunDirectionalShadingPreProcessor.SUN_DIRECTION_UNIFORM);
        if (dir != null) {
            dir.set(sunX, sunY, sunZ);
        }

        boolean isDeepSpace = Minecraft.getInstance().level != null && DeepSpaceHelper.isDeepSpace(Minecraft.getInstance().level);
        boolean lightingActive = isDeepSpace && dev.devce.rocketnautics.RocketConfig.CLIENT.enableSpaceLighting.get();
        Uniform enabled = shader.getUniform(SunDirectionalShadingPreProcessor.SUN_ENABLED_UNIFORM);
        if (enabled != null) {
            enabled.set(lightingActive ? 1.0f : 0.0f);
        }

        Uniform intensity = shader.getUniform(SunDirectionalShadingPreProcessor.SUN_INTENSITY_UNIFORM);
        if (intensity != null) {
            intensity.set(sunIntensity);
        }

        dev.devce.rocketnautics.client.render.shadow.DirectionalShadowRenderer.bindToShader(shader);
    }

    public static float getSunX() { return sunX; }
    public static float getSunY() { return sunY; }
    public static float getSunZ() { return sunZ; }
    public static float getSunIntensity() { return sunIntensity; }
}