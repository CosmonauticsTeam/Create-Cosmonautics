package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.AllBlocks;

import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlock;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneMode;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.catnip.math.Pointing;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;

/** Documents Gyrodyne power, mode selection, and attitude-control modes. */
public final class GyrodynePonderScene {
    private GyrodynePonderScene() {
    }

    public static void show(SceneBuilder scene, SceneBuildingUtil util) {
        scene.title("gyrodyne", "Gyrodyne: Attitude Control Modes");
        scene.configureBasePlate(0, 0, 5);
        BlockPos gyro = util.grid().at(2, 1, 2);
        BlockPos lever = util.grid().at(2, 1, 3);
        scene.world().showSection(util.select().everywhere(), Direction.DOWN);
        scene.world().setBlocks(util.select().layersFrom(1), Blocks.AIR.defaultBlockState(), false);
        scene.world().setBlock(gyro, RocketBlocks.GYRODYNE.getDefaultState(), false);
        scene.world().setBlock(lever, AllBlocks.ANALOG_LEVER.getDefaultState(), false);
        configure(scene, util, gyro, GyrodyneMode.OFF, 0, 0);
        scene.idle(20);
        text(scene, util, "text_1", gyro, 120);
        scene.idle(140);
        scene.overlay().showControls(util.vector().topOf(gyro), Pointing.DOWN, 50).rightClick();
        text(scene, util, "text_2", gyro, 120);
        scene.addKeyframe();
        scene.idle(140);
        configure(scene, util, gyro, GyrodyneMode.SAS, 30, -20);
        text(scene, util, "text_3", gyro, 110);
        scene.addKeyframe();
        scene.idle(130);
        configure(scene, util, gyro, GyrodyneMode.HOLD, -25, 20);
        text(scene, util, "text_4", gyro, 110);
        scene.addKeyframe();
        scene.idle(130);
        configure(scene, util, gyro, GyrodyneMode.PROGRADE, 35, 25);
        text(scene, util, "text_5", gyro, 130);
        scene.addKeyframe();
        scene.idle(150);
        configure(scene, util, gyro, GyrodyneMode.HORIZON, -35, -25);
        text(scene, util, "text_6", gyro, 130);
        scene.addKeyframe();
        scene.idle(150);
        PonderSceneSupport.setAnalogLever(scene, util, lever, 15);
        scene.world().modifyBlock(gyro, state -> state.setValue(GyrodyneBlock.POWERED, true), false);
        scene.world().modifyBlockEntityNBT(util.select().position(gyro), GyrodyneBlockEntity.class, nbt -> nbt.putFloat("RotorSpeed", 0));
        text(scene, util, "text_7", lever, 110);
        scene.idle(130);
    }

    private static void configure(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, GyrodyneMode mode, float tiltX, float tiltZ) {
        scene.world().modifyBlockEntity(pos, GyrodyneBlockEntity.class, gyro -> gyro.setMode(mode));
        scene.world().modifyBlockEntityNBT(util.select().position(pos), GyrodyneBlockEntity.class, nbt -> {
            nbt.putInt("Energy", 10_000);
            nbt.putFloat("RotorSpeed", mode == GyrodyneMode.OFF ? 0 : 40);
            nbt.putFloat("TargetTiltX", tiltX);
            nbt.putFloat("TargetTiltZ", tiltZ);
        });
    }

    private static void text(SceneBuilder scene, SceneBuildingUtil util, String key, BlockPos pos, int duration) {
        scene.overlay().showText(duration).text("rocketnautics.ponder.gyrodyne." + key).pointAt(util.vector().topOf(pos)).placeNearTarget();
    }
}
