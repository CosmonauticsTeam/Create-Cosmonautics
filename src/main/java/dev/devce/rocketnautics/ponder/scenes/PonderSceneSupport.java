package dev.devce.rocketnautics.ponder.scenes;

import com.simibubi.create.content.redstone.analogLever.AnalogLeverBlockEntity;

import dev.devce.rocketnautics.content.blocks.VectorThrusterBlockEntity;
import net.createmod.ponder.api.scene.SceneBuilder;
import net.createmod.ponder.api.scene.SceneBuildingUtil;
import net.minecraft.core.BlockPos;

/**
 * Shared scene actions. Keep this class limited to helpers used by multiple
 * storyboards.
 */
final class PonderSceneSupport {
    private PonderSceneSupport() {
    }

    /** Sets a Create analog lever's displayed signal strength. */
    static void setAnalogLever(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, int strength) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), AnalogLeverBlockEntity.class, nbt -> nbt.putInt("State", strength));
    }

    /** Updates the vector thruster's visual gimbal angles. */
    static void setVectorGimbal(SceneBuilder scene, SceneBuildingUtil util, BlockPos pos, float x, float y, float z) {
        scene.world().modifyBlockEntityNBT(util.select().position(pos), VectorThrusterBlockEntity.class, nbt -> {
            nbt.putFloat("GimbalX", x);
            nbt.putFloat("GimbalY", y);
            nbt.putFloat("GimbalZ", z);
        });
    }
}
