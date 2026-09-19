package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.orekit.frames.Frame;
import org.orekit.time.AbsoluteDate;

import java.util.Comparator;
import java.util.List;

public class UniverseRenderer {
    public static void render(@Nullable CubePlanet exclude,
                              PoseStack ps,
                              float dTick, float pTick,
                              @Nullable Vector3D pos, @Nullable Frame frame,
                              AbsoluteDate date,
                              Camera camera) {
        assert UniverseHelper.UNIVERSE != null;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        ps.pushPose();

        float vis = 1.0f;
        float angle = mc.level.getTimeOfDay(dTick);

        SkyboxRenderer.render(ps, camera, vis, angle);

        UniverseRenderData.buildVBOs();
        UniverseRenderData.buildVAOs();

        class Planet {
            CubePlanet planet;
            Vector3D pos;
            double distSq;

            Planet(CubePlanet planet, Vector3D pos, double distSq) {
                this.planet = planet;
                this.pos = pos;
                this.distSq = distSq;
            }

            public double getDistSq() { return distSq; }
        }

        List<Planet> planets = UniverseHelper.UNIVERSE.getPlanets().stream()
                .map(p -> {
                    Vector3D pPos = frame == null ? new Vector3D(0, 0, 0) : p.posInMyFrame(date, pos, frame);
                   return new Planet(p, pPos, pPos.getNormSq());
                })
                .sorted(Comparator.comparing(Planet::getDistSq))
                .toList()
                .reversed();

        boolean isDeepSpace = DeepSpaceHelper.isDeepSpace(mc.level);

        for (Planet pl : planets) {
            if (exclude != null && pl.planet.id() == exclude.id()) continue;
            if (!isDeepSpace && !RocketConfig.CLIENT.enableCustomSky.get()) continue;

            Vector3f p = new Vector3f((float)pl.pos.getX(), (float)pl.pos.getY(), (float)pl.pos.getZ());
            Vector3f toTarget = camera.getPosition().toVector3f().sub(p).normalize();
            Vector3f forward = new Vector3f(0, 0, -1)
                    .rotate(camera.rotation())
                    .normalize();

            float alignment = Math.clamp((forward.dot(toTarget) + 1.0f) * 0.5f, 0.0f, 1.0f);
            float fov = mc.options.fov().get() / 180.0f;

            if (alignment < 1 - fov && pl.distSq > 5*10e13f) continue;

            ps.pushPose();
            PlanetRenderer.render(pl.planet, ps, camera, pl.pos, date, frame, angle, pTick);
            ps.popPose();
        }

        LensFlareRenderer.renderQueue(camera);

        ps.popPose();
    }
}
