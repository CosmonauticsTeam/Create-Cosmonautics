package dev.devce.rocketnautics.ponder;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import net.createmod.ponder.api.registration.PonderPlugin;
import net.createmod.ponder.api.registration.PonderSceneRegistrationHelper;
import net.minecraft.resources.ResourceLocation;

public class RocketPonderPlugin implements PonderPlugin {
    @Override
    public String getModId() {
        return RocketNautics.MODID;
    }

    @Override
    public void registerScenes(PonderSceneRegistrationHelper<ResourceLocation> helper) {
        helper.forComponents(RocketBlocks.ROCKET_THRUSTER.getId())
            .addStoryBoard(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"), RocketPonderScenes::thrusterIntro);
        helper.forComponents(RocketBlocks.CREATIVE_THRUSTER.getId())
            .addStoryBoard(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "rocket_final"), RocketPonderScenes::creativeThrusterThrottle);
    }
}
