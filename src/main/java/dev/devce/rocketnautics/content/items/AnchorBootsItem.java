package dev.devce.rocketnautics.content.items;

import com.simibubi.create.content.equipment.armor.DivingBootsItem;
import dev.ryanhcode.sable.Sable;
import net.createmod.catnip.nbt.NBTHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

public class AnchorBootsItem extends DivingBootsItem {
    public AnchorBootsItem(Holder<ArmorMaterial> material, Properties properties, ResourceLocation textureLoc) {
        super(material, properties, textureLoc);
    }

    public static void accelerateDescentNearBlock(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity))
            return;

        if (!anchorAffects(entity))
            return;

        Vec3 motion = entity.getDeltaMovement();
        entity.setDeltaMovement(motion.add(0, -0.05, 0));
    }

    public static boolean isWornBy(Entity entity) {
        return !getWornItem(entity).isEmpty();
    }

    public static ItemStack getWornItem(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = livingEntity.getItemBySlot(SLOT);
        if (!(stack.getItem() instanceof AnchorBootsItem)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    protected static boolean anchorAffects(LivingEntity entity) {
        if (!isWornBy(entity)) {
            entity.getPersistentData()
                    .remove("HeavierBoots");
            return false;
        }

        NBTHelper.putMarker(entity.getPersistentData(), "HeavierBoots");
        BlockPos supportingPos = entity.getBlockPosBelowThatAffectsMyMovement();
        if (entity.level().getBlockState(supportingPos).isAir())
            return false;
        double y = Sable.HELPER.projectOutOfSubLevel(entity.level(), supportingPos.getBottomCenter()).y() + 1;
        if (entity.getY() - y > 0.2)
            return false;
        if (entity instanceof Player playerEntity) {
            return !playerEntity.getAbilities().flying;
        }
        return true;
    }
}
