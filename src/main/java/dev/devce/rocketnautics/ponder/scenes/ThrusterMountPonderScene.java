package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllItems;

import dev.devce.rocketnautics.content.blocks.EngineNozzleBlock;
import dev.devce.rocketnautics.content.blocks.EnginePipesBlock;
import dev.devce.rocketnautics.content.blocks.ThrusterMountBlock;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketItems;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.PonderPalette;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;

/** Documents modular engine assembly, fuels, nozzles, and pipe cycles. */
public final class ThrusterMountPonderScene {
        private ThrusterMountPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("thruster_mount", "Thruster Mount: Build a Liquid Engine");
                scene.configureBasePlate(0, 0, 5);
                BlockPos mount = util.grid().at(3, 1, 2), pipe = util.grid().at(2, 1, 2), nozzle = util.grid().at(1, 1, 2),
                                fuel = util.grid().at(3, 1, 1), oxidizer = util.grid().at(3, 1, 3);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlock(mount,
                                RocketBlocks.THRUSTER_MOUNT.getDefaultState().setValue(ThrusterMountBlock.FACING, Direction.EAST), false);
                scene.world().setBlock(fuel, AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.Z), false);
                scene.world().setBlock(oxidizer, AllBlocks.FLUID_PIPE.get().getAxisState(Direction.Axis.Z), false);
                scene.idle(20);
                text(scene, util, "text_1", mount, 110);
                scene.overlay().showOutline(PonderPalette.GREEN, "inputs", util.select().fromTo(fuel, oxidizer), 110);
                scene.idle(130);
                text(scene, util, "text_2", fuel, 120);
                scene.idle(140);
                click(scene, util, mount, new ItemStack(AllBlocks.FLUID_PIPE.get()));
                scene.world().setBlock(pipe, RocketBlocks.ENGINE_PIPES.getDefaultState().setValue(EnginePipesBlock.FACING, Direction.WEST)
                                .setValue(EnginePipesBlock.PIPE_TYPE, 0), false);
                text(scene, util, "text_3", pipe, 110);
                scene.addKeyframe();
                scene.idle(130);
                click(scene, util, mount, new ItemStack(RocketItems.COPPER_NOZZLE.get()));
                scene.world().setBlock(nozzle, RocketBlocks.ENGINE_NOZZLE.getDefaultState()
                                .setValue(EngineNozzleBlock.FACING, Direction.WEST).setValue(EngineNozzleBlock.NOZZLE_TYPE, 1), false);
                text(scene, util, "text_4", nozzle, 120);
                scene.idle(140);
                click(scene, util, mount, new ItemStack(RocketItems.TITANIUM_NOZZLE.get()));
                scene.world().modifyBlock(nozzle, state -> state.setValue(EngineNozzleBlock.NOZZLE_TYPE, 2), false);
                text(scene, util, "text_5", nozzle, 120);
                scene.addKeyframe();
                scene.idle(140);
                scene.world().modifyBlock(pipe, state -> state.setValue(EnginePipesBlock.PIPE_TYPE, 3), false);
                click(scene, util, pipe, new ItemStack(AllItems.WRENCH.get()));
                text(scene, util, "text_6", pipe, 130);
                scene.addKeyframe();
                scene.idle(150);
        }

        private static void click(SceneBuilder s, SceneBuildingUtil u, BlockPos pos, ItemStack item) {
                s.overlay().showControls(u.vector().topOf(pos), Pointing.DOWN, 50).withItem(item).rightClick();
        }

        private static void text(SceneBuilder s, SceneBuildingUtil u, String key, BlockPos pos, int time) {
                s.overlay().showText(time).text("rocketnautics.ponder.thruster_mount." + key).pointAt(u.vector().centerOf(pos))
                                .placeNearTarget();
        }
}
