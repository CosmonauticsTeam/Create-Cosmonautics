package dev.devce.rocketnautics.data.recipe;

import com.simibubi.create.AllBlocks;
import com.simibubi.create.api.data.recipe.DeployingRecipeGen;
import com.simibubi.create.api.data.recipe.ItemApplicationRecipeGen;
import com.simibubi.create.foundation.data.recipe.CreateRecipeProvider;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.registry.RocketBlocks;
import dev.devce.rocketnautics.registry.RocketItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.data.PackOutput;

import java.util.concurrent.CompletableFuture;

public class RocketItemApplicationRecipeGen extends ItemApplicationRecipeGen {

    GeneratedRecipe TITANIUM = create("titanium_casing", b -> b.require(AllBlocks.COPPER_CASING)
            .require(RocketItems.TITANIUM_ALLOY_SHEET)
            .output(RocketBlocks.TITANIUM_CASING));

    public RocketItemApplicationRecipeGen(PackOutput output, CompletableFuture<HolderLookup.Provider> registries) {
        super(output, registries, RocketNautics.MODID);
    }
}
