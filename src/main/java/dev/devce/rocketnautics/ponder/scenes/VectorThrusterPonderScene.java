package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;

import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.catnip.animation.LerpedFloat;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

/** Documents the vector thruster's fuel path and gimbal control. */
public final class VectorThrusterPonderScene {
        private VectorThrusterPonderScene() {
        }

        public static void show(SceneBuilder scene, SceneBuildingUtil util) {
                scene.title("vector_thruster", "Vector Thruster: Gimbal Control");
                scene.configureBasePlate(0, 0, 5);
                BlockPos thruster = util.grid().at(1, 1, 2), lever = util.grid().at(1, 1, 3), output = util.grid().at(2, 1, 2),
                                pump = util.grid().at(3, 1, 2), input = util.grid().at(4, 1, 2), tank = util.grid().at(4, 1, 1);
                scene.world().showSection(util.select().everywhere(), Direction.DOWN);
                scene.world().setBlocks(util.select().everywhere(), Blocks.AIR.defaultBlockState(), false);
                scene.world().setBlocks(util.select().fromTo(0, 0, 0, 4, 0, 4), AllBlocks.ANDESITE_CASING.getDefaultState(), false);
                scene.world().setBlock(tank, AllBlocks.FLUID_TANK.getDefaultState().setValue(FluidTankBlock.BOTTOM, true)
                                .setValue(FluidTankBlock.TOP, true).setValue(FluidTankBlock.SHAPE, FluidTankBlock.Shape.WINDOW), false);
                scene.world().modifyBlockEntity(tank, FluidTankBlockEntity.class, be -> {
                        be.getTankInventory().fill(new FluidStack(Fluids.LAVA, 8_000), IFluidHandler.FluidAction.EXECUTE);
                        be.setFluidLevel(LerpedFloat.linear().startWithValue(be.getFillState()));
                        be.sendData();
                });
                scene.world().setBlock(input,
                                AllBlocks.FLUID_PIPE.getDefaultState().setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN), false)
                                                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.UP), false)
                                                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.NORTH), true)
                                                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH), false)
                                                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST), false)
                                                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST), true),
                                false);
                scene.world().setBlock(pump, AllBlocks.MECHANICAL_PUMP.getDefaultState()
                                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
                scene.world().setBlock(output, AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X), false);
                scene.world().setBlock(thruster, RocketBlocks.VECTOR_THRUSTER.getDefaultState()
                                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
                scene.world().modifyBlockEntity(pump, PumpBlockEntity.class, be -> be.setSpeed(32));
                scene.world().setBlock(lever, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
                PonderSceneSupport.setAnalogLever(scene, util, lever, 0);
                PonderSceneSupport.setVectorGimbal(scene, util, thruster, 0, 0, 0);
                scene.idle(15);
                text(scene, util, "text_1", thruster);
                scene.idle(90);
                text(scene, util, "text_2", pump);
                scene.idle(90);
                text(scene, util, "text_3", lever);
                scene.idle(90);
                step(scene, util, thruster, lever, 5, .165f, "text_4");
                scene.idle(90);
                step(scene, util, thruster, lever, 10, .33f, "text_5");
                scene.idle(90);
                step(scene, util, thruster, lever, 15, .495f, "text_6");
                scene.idle(90);
        }

        private static void text(SceneBuilder s, SceneBuildingUtil u, String key, BlockPos pos) {
                s.overlay().showText(80).text("rocketnautics.ponder.vector_thruster." + key).pointAt(u.vector().topOf(pos))
                                .placeNearTarget();
        }

        private static void step(SceneBuilder s, SceneBuildingUtil u, BlockPos thruster, BlockPos lever, int strength, float gimbal,
                        String key) {
                PonderSceneSupport.setAnalogLever(s, u, lever, strength);
                PonderSceneSupport.setVectorGimbal(s, u, thruster, 0, 0, gimbal);
                s.effects().indicateRedstone(lever);
                s.effects().indicateSuccess(thruster);
                text(s, u, key, thruster);
                s.addKeyframe();
        }
}
