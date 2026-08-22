package dev.devce.rocketnautics.ponder.scenes;

import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

/** Documents liquid fuel, thrust configuration, and exhaust safety. */
public final class RocketThrusterPonderScene {
        private RocketThrusterPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("rocket_thruster", "Rocket Thruster: Fuel and Thrust");
                scene.configureBasePlate(0, 0, 5);
                BlockPos thrusterPos = util.grid().at(2, 1, 2);
                BlockPos tankPos = util.grid().at(2, 1, 1);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.idle(30);
                scene.overlay().showText(100).text("rocketnautics.ponder.rocket_thruster.text_1").pointAt(util.vector().topOf(thrusterPos))
                                .placeNearTarget();
                scene.addKeyframe();
                scene.idle(120);
                scene.overlay().showText(110).text("rocketnautics.ponder.rocket_thruster.text_2").pointAt(util.vector().topOf(tankPos))
                                .placeNearTarget();
                scene.idle(130);
                scene.overlay().showText(110).text("rocketnautics.ponder.rocket_thruster.text_3").pointAt(util.vector().topOf(tankPos))
                                .placeNearTarget();
                scene.idle(130);
                scene.overlay().showCenteredScrollInput(thrusterPos, Direction.UP, 60);
                scene.overlay().showText(110).text("rocketnautics.ponder.rocket_thruster.text_4").pointAt(util.vector().topOf(thrusterPos))
                                .placeNearTarget();
                scene.addKeyframe();
                scene.idle(130);
                scene.effects().indicateSuccess(thrusterPos);
                scene.effects().emitParticles(util.vector().centerOf(thrusterPos).add(0, -.7, 0), scene.effects()
                                .particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME, new Vec3(0, -.2, 0)),
                                .7f, 100);
                scene.overlay().showText(110).text("rocketnautics.ponder.rocket_thruster.text_5")
                                .pointAt(util.vector().blockSurface(thrusterPos, Direction.DOWN)).placeNearTarget();
                scene.addKeyframe();
                scene.idle(130);
        }
}
