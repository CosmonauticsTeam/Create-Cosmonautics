package dev.devce.rocketnautics.ponder;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.ponder.scenes.BoosterThrusterPonderScene;
import dev.devce.rocketnautics.ponder.scenes.CreativeThrusterPonderScene;
import dev.devce.rocketnautics.ponder.scenes.GyrodynePonderScene;
import dev.devce.rocketnautics.ponder.scenes.RcsThrusterPonderScene;
import dev.devce.rocketnautics.ponder.scenes.RocketThrusterPonderScene;
import dev.devce.rocketnautics.ponder.scenes.ThrusterMountPonderScene;
import dev.devce.rocketnautics.ponder.scenes.VectorThrusterPonderScene;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

/**
 * Registers each Ponder component with its structure template and storyboard.
 */
public class RocketPonderPlugin implements PonderPlugin {
        @Override
        public String getModId() {
                return RocketNautics.MODID;
        }

        @Override
        public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
                helper.forComponents(RocketBlocks.ROCKET_THRUSTER.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"),
                                RocketThrusterPonderScene::show);
                helper.forComponents(RocketBlocks.CREATIVE_THRUSTER.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"),
                                CreativeThrusterPonderScene::show);
                helper.forComponents(RocketBlocks.VECTOR_THRUSTER.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"),
                                VectorThrusterPonderScene::show);
                helper.forComponents(RocketBlocks.BOOSTER_THRUSTER.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"),
                                BoosterThrusterPonderScene::show);
                helper.forComponents(RocketBlocks.THRUSTER_MOUNT.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"), ThrusterMountPonderScene::show);
                helper.forComponents(RocketBlocks.RCS_THRUSTER.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rcs_rocket"), RcsThrusterPonderScene::show);
                helper.forComponents(RocketBlocks.GYRODYNE.getId()).addStoryBoard(
                                ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"), GyrodynePonderScene::show);
        }
}
