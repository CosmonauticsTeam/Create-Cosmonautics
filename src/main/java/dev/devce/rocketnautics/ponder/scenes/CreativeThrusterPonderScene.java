package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;

import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Documents Redstone-controlled thrust without fuel. */
public final class CreativeThrusterPonderScene {
        private CreativeThrusterPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("creative_thruster", "Creative Thruster: Redstone Throttle");
                scene.configureBasePlate(0, 0, 5);
                BlockPos thrusterPos = util.grid().at(2, 1, 2), leverPos = util.grid().at(2, 1, 1);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlock(thrusterPos, RocketBlocks.CREATIVE_THRUSTER.getDefaultState(), false);
                scene.world().setBlock(leverPos, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
                PonderSceneSupport.setAnalogLever(scene, util, leverPos, 0);
                scene.idle(30);
                scene.overlay().showText(100).text("rocketnautics.ponder.creative_thruster.text_1")
                                .pointAt(util.vector().topOf(thrusterPos)).placeNearTarget();
                scene.idle(120);
                scene.overlay().showControls(util.vector().topOf(thrusterPos), Pointing.DOWN, 40).rightClick();
                scene.overlay().showText(110).text("rocketnautics.ponder.creative_thruster.text_2")
                                .pointAt(util.vector().centerOf(thrusterPos)).placeNearTarget();
                scene.idle(130);
                scene.overlay().showText(100).text("rocketnautics.ponder.creative_thruster.text_3").pointAt(util.vector().topOf(leverPos))
                                .placeNearTarget();
                scene.addKeyframe();
                scene.idle(120);
                showPower(scene, util, thrusterPos, leverPos, 10, .5f, 100, "rocketnautics.ponder.creative_thruster.text_4");
                scene.idle(120);
                showPower(scene, util, thrusterPos, leverPos, 15, 1f, 120, "rocketnautics.ponder.creative_thruster.text_5");
                scene.idle(130);
        }

        private static void showPower(SceneBuilder scene, SceneBuildingUtil util, BlockPos thruster, BlockPos lever, int power,
                        float density, int cycles, String text) {
                PonderSceneSupport.setAnalogLever(scene, util, lever, power);
                scene.addKeyframe();
                scene.effects().indicateRedstone(lever);
                scene.effects().indicateSuccess(thruster);
                scene.effects().emitParticles(util.vector().centerOf(thruster).add(0, -.7, 0), scene.effects()
                                .particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME, new Vec3(0, -.25, 0)),
                                density, cycles);
                scene.idle(30);
                scene.overlay().showText(power == 10 ? 100 : 110).text(text)
                                .pointAt(power == 10 ? util.vector().blockSurface(thruster, Direction.DOWN) : util.vector().topOf(thruster))
                                .placeNearTarget();
        }
}
