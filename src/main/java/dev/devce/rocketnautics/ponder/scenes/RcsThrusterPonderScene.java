package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;

import dev.devce.rocketnautics.content.blocks.RCSThrusterBlockEntity;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Documents Redstone-controlled RCS attitude correction. */
public final class RcsThrusterPonderScene {
        private RcsThrusterPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("rcs_thruster", "RCS Thruster: Spacecraft Attitude Control");
                scene.configureBasePlate(0, 0, 5);
                BlockPos rcs = util.grid().at(0, 1, 2), lever = util.grid().at(0, 1, 3);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlock(rcs, RocketBlocks.RCS_THRUSTER.getDefaultState()
                                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
                scene.world().setBlock(lever, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 0);
                scene.idle(20);
                text(scene, util, "text_1", rcs, 110);
                scene.idle(130);
                text(scene, util, "text_2", rcs, 100);
                scene.idle(120);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 5);
                setBurning(scene, util, rcs);
                scene.effects().indicateRedstone(lever);
                scene.effects().indicateSuccess(rcs);
                plume(scene, util, rcs, .3f, 80, -.1);
                text(scene, util, "text_3", lever, 110);
                scene.addKeyframe();
                scene.idle(130);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 15);
                scene.effects().indicateRedstone(lever);
                plume(scene, util, rcs, .6f, 100, -.2);
                scene.world().setBlock(lever, Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlock(rcs, Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlocks(util.select().fromTo(1, 1, 1, 3, 1, 3), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), false);
                scene.world().setBlocks(util.select().fromTo(1, 2, 1, 3, 2, 3), AllBlocks.BRASS_CASING.getDefaultState(), false);
                scene.world().setBlocks(util.select().fromTo(1, 3, 1, 3, 3, 3), Blocks.LIGHT_BLUE_STAINED_GLASS.defaultBlockState(), false);
                scene.world().setBlocks(util.select().fromTo(2, 4, 1, 2, 4, 3), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), false);
                scene.world().setBlocks(util.select().fromTo(1, 4, 2, 3, 4, 2), AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), false);
                rcs(scene, util, 0, 1, 2, Direction.WEST);
                rcs(scene, util, 4, 1, 2, Direction.EAST);
                rcs(scene, util, 2, 1, 0, Direction.NORTH);
                rcs(scene, util, 2, 1, 4, Direction.SOUTH);
                scene.overlay().showOutline(PonderPalette.INPUT, "rcs_pods", util.select().fromTo(0, 1, 0, 4, 1, 4), 120);
                scene.overlay().showText(120).text("rocketnautics.ponder.rcs_thruster.text_4")
                                .pointAt(util.vector().topOf(util.grid().at(2, 3, 2))).placeNearTarget();
                scene.addKeyframe();
                scene.idle(140);
        }

        private static void rcs(SceneBuilder s, SceneBuildingUtil u, int x, int y, int z, Direction facing) {
                s.world().setBlock(u.grid().at(x, y, z), RocketBlocks.RCS_THRUSTER.getDefaultState()
                                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, facing), false);
        }

        private static void setBurning(SceneBuilder s, SceneBuildingUtil u, BlockPos pos) {
                s.world().modifyBlockEntityNBT(u.select().position(pos), RCSThrusterBlockEntity.class,
                                nbt -> nbt.putBoolean("Burning", true));
        }

        private static void plume(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, float density, int cycles, double speed) {
                s.effects().emitParticles(u.vector().centerOf(pos).add(-.5, 0, 0), s.effects().particleEmitterWithinBlockSpace(
                                net.minecraft.core.particles.ParticleTypes.CLOUD, new Vec3(speed, 0, 0)), density, cycles);
        }

        private static void text(SceneBuilder s, SceneBuildingUtil u, String key, BlockPos pos, int time) {
                s.overlay().showText(time).text("rocketnautics.ponder.rcs_thruster." + key).pointAt(u.vector().topOf(pos))
                                .placeNearTarget();
        }
}
