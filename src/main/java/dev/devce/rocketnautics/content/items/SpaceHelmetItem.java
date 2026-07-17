package dev.devce.rocketnautics.content.items;

import com.simibubi.create.content.equipment.armor.DivingHelmetItem;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;

public class SpaceHelmetItem extends DivingHelmetItem {
    public SpaceHelmetItem(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc) {
        super(material, properties, textureLoc);
    }

    @Override
    public void initializeClient(java.util.function.Consumer<net.neoforged.neoforge.client.extensions.common.IClientItemExtensions> consumer) {
        // Do not call super.initializeClient to fall back to the vanilla armor model and use our custom texture.
    }
}
