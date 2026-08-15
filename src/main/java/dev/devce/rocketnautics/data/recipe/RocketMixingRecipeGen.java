package dev.devce.rocketnautics.data.recipe;

import com.simibubi.create.AllItems;
import com.simibubi.create.api.data.recipe.MixingRecipeGen;
import com.simibubi.create.content.processing.recipe.HeatCondition;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

import java.util.concurrent.CompletableFuture;

import static dev.devce.rocketnautics.registry.RocketBlocks.*;
import static dev.devce.rocketnautics.registry.RocketItems.*;
import static dev.devce.rocketnautics.registry.RocketTags.*;

public class RocketMixingRecipeGen extends MixingRecipeGen {

    GeneratedRecipe TITANIUM_ALLOY = create("titanium_alloy", b -> b
            .require(MetalTags.TITANIUM.ingots)
            .require(MetalTags.TITANIUM.ingots)
            .require(MetalTags.TITANIUM.ingots)
            .require(com.simibubi.create.foundation.data.recipe.CommonMetal.BRASS.ingots)
            .output(RocketItems.TITANIUM_ALLOY, 4)
            .requiresHeat(HeatCondition.HEATED));

    GeneratedRecipe LUNAR_AGED_BASALT = create("lunar_aged_basalt", b -> b
            .require(LUNAR_SHATTERED_REGOLITH)
            .require(Blocks.BASALT)
            .output(RocketBlocks.LUNAR_AGED_BASALT, 2)
            .output(AllItems.CINDER_FLOUR)
            .requiresHeat(HeatCondition.HEATED));

    GeneratedRecipe LUNAR_ROCK = create("lunar_rock", b -> b
            .require(LUNAR_FRAGMENTED_ROCK)
            .output(RocketBlocks.LUNAR_ROCK)
            .requiresHeat(HeatCondition.HEATED));

    public RocketMixingRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, RocketNautics.MODID);
    }
}
