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
		showText(scene, util, "text_1", rcs);
		scene.idle(120);
		showText(scene, util, "text_2", rcs);
		scene.idle(120);
		PonderSceneSupport.setAnalogLever(scene, util, lever, 5);
		setBurning(scene, util, rcs, true);
		scene.effects().indicateRedstone(lever);
		scene.effects().indicateSuccess(rcs);
		plume(scene, util, rcs, .3f, 80, -.1);
		showText(scene, util, "text_3", lever);
		scene.idle(120);
		PonderSceneSupport.setAnalogLever(scene, util, lever, 15);
		scene.effects().indicateRedstone(lever);
		plume(scene, util, rcs, .6f, 100, -.2);
		scene.idle(120);
		scene.world().setBlock(lever, Blocks.AIR.defaultBlockState(), false);
		setBurning(scene, util, rcs, false);
		scene.idle(20);

		// This keyframe starts the self-contained casing chapter.
		scene.addKeyframe();
		showText(scene, util, "text_4", rcs);
		scene.idle(120);
		setCasing(scene, rcs, RocketBlocks.BRASS_ENCASED_RCS_THRUSTER.getDefaultState());
		showText(scene, util, "text_5", rcs);
		scene.idle(120);
		setCasing(scene, rcs, RocketBlocks.COPPER_ENCASED_RCS_THRUSTER.getDefaultState());
		showText(scene, util, "text_6", rcs);
		scene.idle(120);
		setCasing(scene, rcs, RocketBlocks.RAILWAY_ENCASED_RCS_THRUSTER.getDefaultState());
		showText(scene, util, "text_7", rcs);
		scene.idle(120);

		scene.addKeyframe();
		scene.world().setBlock(rcs, Blocks.AIR.defaultBlockState(), false);
		BlockPos core = util.grid().at(2, 1, 2);
		scene.world().setBlock(core, AllBlocks.INDUSTRIAL_IRON_BLOCK.getDefaultState(), false);
		rcs(scene, util, 1, 1, 2, Direction.WEST);
		rcs(scene, util, 3, 1, 2, Direction.EAST);
		rcs(scene, util, 2, 1, 1, Direction.NORTH);
		rcs(scene, util, 2, 1, 3, Direction.SOUTH);
		scene.overlay().showOutline(PonderPalette.INPUT, "rcs_pods", util.select().fromTo(1, 1, 1, 3, 1, 3), 120);
		showText(scene, util, "text_8", core);
		scene.idle(120);
	}

	private static void rcs(SceneBuilder s, SceneBuildingUtil u, int x, int y, int z, Direction facing) {
		s.world().setBlock(u.grid().at(x, y, z),
				RocketBlocks.RCS_THRUSTER.getDefaultState().setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, facing),
				false);
	}

	private static void setCasing(SceneBuilder s, BlockPos pos, net.minecraft.world.level.block.state.BlockState casing) {
		s.world().setBlock(pos, casing.setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
		s.effects().indicateSuccess(pos);
	}

	private static void setBurning(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, boolean burning) {
		s.world().modifyBlockEntityNBT(u.select().position(pos), RCSThrusterBlockEntity.class, nbt -> nbt.putBoolean("Burning", burning));
	}

	private static void plume(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, float density, int cycles, double speed) {
		s.effects().emitParticles(u.vector().centerOf(pos).add(-.5, 0, 0),
				s.effects().particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.CLOUD, new Vec3(speed, 0, 0)),
				density, cycles);
	}

	private static void showText(SceneBuilder s, SceneBuildingUtil u, String key, BlockPos pos) {
		s.overlay().showText(100).text("rocketnautics.ponder.rcs_thruster." + key).pointAt(u.vector().topOf(pos)).placeNearTarget();
	}
}
