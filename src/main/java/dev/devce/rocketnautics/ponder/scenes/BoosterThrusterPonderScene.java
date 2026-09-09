package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;

import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlock;
import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlockEntity;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Documents solid-fuel ignition and irreversible booster burn. */
public final class BoosterThrusterPonderScene {
        private BoosterThrusterPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("booster_thruster", "Booster Thruster: Solid Fuel");
                scene.configureBasePlate(0, 0, 5);
                BlockPos thruster = util.grid().at(2, 1, 2), lever = util.grid().at(2, 1, 3), fuel = util.grid().at(3, 1, 2);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlock(thruster,
                                RocketBlocks.BOOSTER_THRUSTER.getDefaultState().setValue(BoosterThrusterBlock.FACING, Direction.WEST),
                                false);
                scene.world().setBlocks(util.select().fromTo(fuel, fuel.offset(1, 0, 0)), Blocks.COAL_BLOCK.defaultBlockState(), false);
                scene.world().setBlock(lever, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 0);
                scene.idle(20);
                text(scene, util, "text_1", fuel, 100);
                scene.idle(120);
                text(scene, util, "text_2", lever, 100);
                scene.addKeyframe();
                scene.idle(120);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 15);
                scene.world().modifyBlock(thruster, state -> state.setValue(BoosterThrusterBlock.POWERED, true), false);
                scene.world().modifyBlockEntityNBT(util.select().position(thruster), BoosterThrusterBlockEntity.class, nbt -> {
                        nbt.putBoolean("Burning", true);
                        nbt.putBoolean("Ignited", true);
                        nbt.putInt("Fuel", 200);
                        nbt.putInt("FuelTicks", 200);
                });
                scene.effects().indicateRedstone(lever);
                scene.effects().indicateSuccess(thruster);
                scene.effects().emitParticles(util.vector().centerOf(thruster).add(-.7, 0, 0), scene.effects()
                                .particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME, new Vec3(-.2, 0, 0)),
                                .7f, 100);
                text(scene, util, "text_3", thruster, 110);
                scene.addKeyframe();
                scene.idle(130);
                scene.overlay().showText(100).text("rocketnautics.ponder.booster_thruster.text_4")
                                .pointAt(util.vector().blockSurface(thruster, Direction.WEST)).placeNearTarget();
                scene.idle(120);
        }

        private static void text(SceneBuilder s, SceneBuildingUtil u, String key, BlockPos pos, int time) {
                s.overlay().showText(time).text("rocketnautics.ponder.booster_thruster." + key).pointAt(u.vector().topOf(pos))
                                .placeNearTarget();
        }
}
