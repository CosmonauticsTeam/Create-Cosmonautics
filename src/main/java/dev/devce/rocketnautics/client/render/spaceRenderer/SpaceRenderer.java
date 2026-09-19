package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.RocketDimensions;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.config.IrisConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.orekit.time.AbsoluteDate;

@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public final class SpaceRenderer {

    private static Boolean queuedShaderState = null;
    private static boolean shadersSuppressed = false;
    private static final IrisConfig config = Iris.getIrisConfig();

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post e) {
        if (queuedShaderState == null) return;
        try {
            config.setShadersEnabled(queuedShaderState);
            config.save();

            Iris.reload();
        } catch (Exception ignored) {}

        queuedShaderState = null;
    }
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent e) {
        boolean isModern = dev.devce.rocketnautics.RocketConfig.CLIENT.skyRenderingSystem.get() == dev.devce.rocketnautics.RocketConfig.SkyRenderingSystem.MODERN;
        if (!isModern) return;

        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) return;
        if (UniverseHelper.receivedPositionTick == -1 || UniverseHelper.UNIVERSE == null) return;

        Minecraft mc = Minecraft.getInstance();

        if (mc.level == null) return;
        ResourceKey<Level> dimension = mc.level.dimension();

        if (dimension == RocketDimensions.DEEP_SPACE || RocketConfig.CLIENT.enableCustomSky.get()) {
            if (config.areShadersEnabled()) {
                queuedShaderState = false;
                shadersSuppressed = true;
            }

            float partial = e.getPartialTick().getGameTimeDeltaPartialTick(true);
            AbsoluteDate date = UniverseHelper.getRenderDate(partial);

            PoseStack ps = e.getPoseStack();

            ps.pushPose();
            ps.mulPose(e.getModelViewMatrix()); // AFTER_SKY renders before the model view matrix is normally applied

            if (dimension == RocketDimensions.DEEP_SPACE) {
                Vec3 pos = e.getCamera().getPosition();
                VoxelShape box = DeepSpaceData.getBoxForPosition(pos);

                if (box.bounds().contains(pos))
                    UniverseRenderer.render(null, ps,
                            e.getPartialTick().getGameTimeDeltaTicks(), partial,
                            UniverseHelper.receivedPosition.getPosition(date),
                            UniverseHelper.receivedPosition.getFrame(),
                            date, e.getCamera());

                ps.pushPose();
                IBRenderer.render(ps, pos);
                ps.popPose();
            } else {
                // temporary block until universe data sync is fixed on planet surfaces
                if (true) return;

                CubePlanet planet = UniverseHelper.UNIVERSE.getPlanets().stream()
                        .filter(p -> {
                            if (p.linkedDimension() == null) return false;
                            return p.linkedDimension().key() == dimension;
                        })
                        .findFirst()
                        .orElse(null);

                UniverseRenderer.render(planet, ps,
                        e.getPartialTick().getGameTimeDeltaTicks(), partial,
                        UniverseHelper.receivedPosition.getPosition(date),
                        UniverseHelper.receivedPosition.getFrame(),
                        date, e.getCamera());
            }

            ps.popPose();
        } else {
            if (shadersSuppressed) {
                queuedShaderState = true;
                shadersSuppressed = false;
            }
        }
    }
}
