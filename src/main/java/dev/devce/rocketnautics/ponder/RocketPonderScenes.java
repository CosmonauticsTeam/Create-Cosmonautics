package dev.devce.rocketnautics.ponder;

import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;
import com.simibubi.create.content.fluids.pump.PumpBlockEntity;
import com.simibubi.create.content.fluids.tank.FluidTankBlock;
import com.simibubi.create.content.fluids.tank.FluidTankBlockEntity;
import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.createmod.catnip.animation.LerpedFloat;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

public class RocketPonderScenes {

    public static void thrusterIntro(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("rocket_thruster", "Rocket Thruster: Fuel and Thrust");
        scene.configureBasePlate(0, 0, 5);

        BlockPos thrusterPos = util.grid().at(2, 1, 2);
        BlockPos tankPos = util.grid().at(2, 1, 1);

        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.idle(30);

        scene.overlay().showText(100)
                .text("rocketnautics.ponder.rocket_thruster.text_1")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(120);

        scene.overlay().showText(110)
                .text("rocketnautics.ponder.rocket_thruster.text_2")
                .pointAt(util.vector().topOf(tankPos))
                .placeNearTarget();
        scene.idle(130);

        scene.overlay().showText(110)
                .text("rocketnautics.ponder.rocket_thruster.text_3")
                .pointAt(util.vector().topOf(tankPos))
                .placeNearTarget();
        scene.idle(130);

        scene.overlay().showCenteredScrollInput(thrusterPos, Direction.UP, 60);
        scene.overlay().showText(110)
                .text("rocketnautics.ponder.rocket_thruster.text_4")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(130);

        scene.effects().indicateSuccess(thrusterPos);
        scene.effects().emitParticles(util.vector().centerOf(thrusterPos).add(0, -0.7, 0),
                scene.effects().particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME,
                        new Vec3(0, -0.2, 0)),
                0.7f, 100);
        scene.overlay().showText(110)
                .text("rocketnautics.ponder.rocket_thruster.text_5")
                .pointAt(util.vector().blockSurface(thrusterPos, Direction.DOWN))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(130);
    }

    public static void creativeThrusterThrottle(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("creative_thruster", "Creative Thruster: Redstone Throttle");
        scene.configureBasePlate(0, 0, 5);

        BlockPos thrusterPos = util.grid().at(2, 1, 2);
        BlockPos leverPos = util.grid().at(2, 1, 1);

        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        // Keep only the structure's floor; its original rocket components do not apply
        // here.
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(thrusterPos, RocketBlocks.CREATIVE_THRUSTER.getDefaultState(), false);
        scene.world().setBlock(leverPos, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
        setAnalogLever(scene, util, leverPos, 0);
        scene.idle(30);

        scene.overlay().showText(100)
                .text("rocketnautics.ponder.creative_thruster.text_1")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.idle(120);

        scene.overlay().showControls(util.vector().topOf(thrusterPos), Pointing.DOWN, 40).rightClick();
        scene.overlay().showText(110)
                .text("rocketnautics.ponder.creative_thruster.text_2")
                .pointAt(util.vector().centerOf(thrusterPos))
                .placeNearTarget();
        scene.idle(130);

        scene.overlay().showText(100)
                .text("rocketnautics.ponder.creative_thruster.text_3")
                .pointAt(util.vector().topOf(leverPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(120);

        setAnalogLever(scene, util, leverPos, 10);
        scene.addKeyframe();
        scene.effects().indicateRedstone(leverPos);
        scene.effects().indicateSuccess(thrusterPos);
        scene.effects().emitParticles(util.vector().centerOf(thrusterPos).add(0, -0.7, 0),
                scene.effects().particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME,
                        new Vec3(0, -0.2, 0)),
                0.5f, 100);
        scene.idle(30);
        scene.overlay().showText(100)
                .text("rocketnautics.ponder.creative_thruster.text_4")
                .pointAt(util.vector().blockSurface(thrusterPos, Direction.DOWN))
                .placeNearTarget();
        scene.idle(120);

        setAnalogLever(scene, util, leverPos, 15);
        scene.addKeyframe();
        scene.effects().indicateRedstone(leverPos);
        scene.effects().indicateSuccess(thrusterPos);
        scene.effects().emitParticles(util.vector().centerOf(thrusterPos).add(0, -0.7, 0),
                scene.effects().particleEmitterWithinBlockSpace(net.minecraft.core.particles.ParticleTypes.FLAME,
                        new Vec3(0, -0.3, 0)),
                1.0f, 120);
        scene.idle(30);
        scene.overlay().showText(110)
                .text("rocketnautics.ponder.creative_thruster.text_5")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.idle(130);
    }

    public static void vectorThrusterGimbal(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("vector_thruster", "Vector Thruster: Gimbal Control");
        scene.configureBasePlate(0, 0, 5);

        BlockPos thrusterPos = util.grid().at(1, 1, 2);
        BlockPos leverPos = util.grid().at(1, 1, 3);
        BlockPos outputPipePos = util.grid().at(2, 1, 2);
        BlockPos pumpPos = util.grid().at(3, 1, 2);
        BlockPos inputPipePos = util.grid().at(4, 1, 2);
        BlockPos tankPos = util.grid().at(4, 1, 1);

        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.world().setBlocks(util.select().everywhere(), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlocks(util.select().fromTo(0, 0, 0, 4, 0, 4), AllBlocks.ANDESITE_CASING.getDefaultState(),
                false);
        scene.world().setBlock(tankPos, AllBlocks.FLUID_TANK.getDefaultState()
                .setValue(FluidTankBlock.BOTTOM, true)
                .setValue(FluidTankBlock.TOP, true)
                .setValue(FluidTankBlock.SHAPE, FluidTankBlock.Shape.WINDOW), false);
        scene.world().modifyBlockEntity(tankPos, FluidTankBlockEntity.class,
                tank -> {
                    tank.getTankInventory().fill(new FluidStack(Fluids.LAVA, 8_000), IFluidHandler.FluidAction.EXECUTE);
                    tank.setFluidLevel(LerpedFloat.linear().startWithValue(tank.getFillState()));
                    tank.sendData();
                });
        scene.world().setBlock(inputPipePos, AllBlocks.FLUID_PIPE.getDefaultState()
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.DOWN), false)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.UP), false)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.NORTH), true)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.SOUTH), false)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.EAST), false)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(Direction.WEST), true), false);
        scene.world().setBlock(pumpPos, AllBlocks.MECHANICAL_PUMP.getDefaultState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
        scene.world().setBlock(outputPipePos, AllBlocks.GLASS_FLUID_PIPE.getDefaultState()
                .setValue(net.minecraft.world.level.block.RotatedPillarBlock.AXIS, Direction.Axis.X), false);
        scene.world().setBlock(thrusterPos, RocketBlocks.VECTOR_THRUSTER.getDefaultState()
                .setValue(net.minecraft.world.level.block.DirectionalBlock.FACING, Direction.WEST), false);
        scene.world().modifyBlockEntity(pumpPos, PumpBlockEntity.class, pump -> pump.setSpeed(32.0f));
        scene.world().setBlock(leverPos, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
        setAnalogLever(scene, util, leverPos, 0);
        setVectorGimbal(scene, util, thrusterPos, 0.0f, 0.0f, 0.0f);
        scene.idle(15);

        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_1")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_2")
                .pointAt(util.vector().topOf(pumpPos))
                .placeNearTarget();
        scene.idle(90);

        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_3")
                .pointAt(util.vector().topOf(leverPos))
                .placeNearTarget();
        scene.idle(90);

        setAnalogLever(scene, util, leverPos, 5);
        setVectorGimbal(scene, util, thrusterPos, 0.0f, 0.0f, 0.165f);
        scene.effects().indicateRedstone(leverPos);
        scene.effects().indicateSuccess(thrusterPos);
        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_4")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(90);

        setAnalogLever(scene, util, leverPos, 10);
        setVectorGimbal(scene, util, thrusterPos, 0.0f, 0.0f, 0.33f);
        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_5")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(90);

        setAnalogLever(scene, util, leverPos, 15);
        setVectorGimbal(scene, util, thrusterPos, 0.0f, 0.0f, 0.495f);
        scene.overlay().showText(80)
                .text("rocketnautics.ponder.vector_thruster.text_6")
                .pointAt(util.vector().topOf(thrusterPos))
                .placeNearTarget();
        scene.addKeyframe();
        scene.idle(90);
    }

    private static void setAnalogLever(SceneBuilder scene, SceneBuildingUtil util, BlockPos leverPos, int strength) {
        scene.world().modifyBlockEntityNBT(util.select().position(leverPos), AnalogLeverBlockEntity.class,
                nbt -> nbt.putInt("State", strength));
    }

    private static void setVectorGimbal(SceneBuilder scene, SceneBuildingUtil util, BlockPos thrusterPos, float x,
            float y, float z) {
        scene.world().modifyBlockEntityNBT(util.select().position(thrusterPos), VectorThrusterBlockEntity.class,
                nbt -> {
                    nbt.putFloat("GimbalX", x);
                    nbt.putFloat("GimbalY", y);
                    nbt.putFloat("GimbalZ", z);
                });
    }

}
