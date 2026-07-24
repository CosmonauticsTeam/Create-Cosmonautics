package dev.devce.rocketnautics.data;

import com.tterrag.registrate.providers.RegistrateTagsProvider;
import dev.devce.rocketnautics.registry.RocketTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

public class RocketFluidTagsProvider {

    private static TagKey<Fluid> getCommonTag( String tag ) {
        return TagKey.create(Registries.FLUID, ResourceLocation.fromNamespaceAndPath("c", tag ) );
    }

    protected static void addTags(RegistrateTagsProvider.IntrinsicImpl<Fluid> prov) {
        prov.addTag(RocketTags.FluidTags.ROCKET_FUEL.tag)
                .add(Fluids.LAVA)
                .addOptionalTag(getCommonTag("gasoline"))
                .addOptionalTag(getCommonTag("diesel"))
                .addOptionalTag(getCommonTag("diesel_sulfur"))
                .addOptionalTag(getCommonTag("kerosene"))
                .addOptionalTag(getCommonTag("hydrogen"))
                .addOptionalTag(getCommonTag("lpg"))
                .addOptionalTag(getCommonTag("petroleum_gas"))
                .addOptionalTag(getCommonTag("napalm"))
                .addOptionalTag(getCommonTag("naphtha"));

        prov.addTag(RocketTags.FluidTags.OXIDIZER.tag)
            .add(Fluids.WATER)
            .addOptionalTag(getCommonTag("air"));
    }
}
