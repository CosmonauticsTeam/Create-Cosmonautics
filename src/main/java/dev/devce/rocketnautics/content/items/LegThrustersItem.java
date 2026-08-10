package dev.devce.rocketnautics.content.items;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.armor.BaseArmorItem;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.api.orbit.AtmosphereFlags;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.EntityMovementExtension;
import dev.ryanhcode.sable.mixinterface.entity.entity_sublevel_collision.LivingEntityMovementExtension;
import dev.ryanhcode.sable.sublevel.SubLevel;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@EventBusSubscriber
public class LegThrustersItem extends BaseArmorItem {

    //TODO: give custom inv icon

    @Override
    public @Nullable ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.parse(String.format(Locale.ROOT, "%s:textures/models/armor/empty.png", textureLoc.getNamespace(), textureLoc.getPath(), slot == EquipmentSlot.LEGS ? 2 : 1));
    }

    public LegThrustersItem(Holder<ArmorMaterial> armorMaterial, Properties properties, ResourceLocation textureLoc) {
        super(armorMaterial, Type.LEGGINGS, properties, textureLoc);
    }

    public static void toggle(ServerPlayer player) {
        ItemStack worn = getWornItem(player);
        if (worn.getItem() instanceof LegThrustersItem j) {
            boolean wasActive = j.setActive(worn, !j.isActive(worn));
            if (wasActive) {
                player.displayClientMessage(Component.translatable("rocketnautics.dampeners.disabled").withStyle(ChatFormatting.RED), true);
                worn.remove(RocketDataComponents.DAMPENER_RELATIVE_SUBLEVEL);
            } else {
                SubLevel containing = Sable.HELPER.getContaining(player);
                if (containing == null) {
                    containing = ((EntityMovementExtension) player).sable$getTrackingSubLevel();
                }
                if (containing == null || containing.isRemoved()) {
                    player.displayClientMessage(Component.translatable("rocketnautics.dampeners.enabled").withStyle(ChatFormatting.GREEN), true);
                    worn.remove(RocketDataComponents.DAMPENER_RELATIVE_SUBLEVEL);
                } else {
                    player.connection.send(new ClientboundSetEntityMotionPacket(player));
                    worn.set(RocketDataComponents.DAMPENER_RELATIVE_SUBLEVEL, containing.getUniqueId());
                    player.displayClientMessage(Component.translatable("rocketnautics.dampeners.relative").withStyle(ChatFormatting.BLUE), true);
                }
            }
        }
    }

    public static boolean isWornBy(Entity entity) {
        return !getWornItem(entity).isEmpty();
    }

    public static ItemStack getWornItem(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = livingEntity.getItemBySlot(EquipmentSlot.LEGS);
        if (!(stack.getItem() instanceof LegThrustersItem)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    @SubscribeEvent
    public static void entityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Player entity)) return;

        Level level = entity.level();
        if (level.isClientSide && !GlobalSpacePhysicsHandler.shouldDisplayTimer(entity))
            entity.getPersistentData().remove("VisualBacktankAir");

        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(entity);
        if (backtanks.isEmpty() && !entity.hasInfiniteMaterials()) return;

        boolean active = handleInertialDamping(entity);
        if (!active || entity.hasInfiniteMaterials()) return;

        if (level.isClientSide) {
            float visualBacktankAir = 0f;
            for (ItemStack stack : backtanks)
                visualBacktankAir += BacktankUtil.getAir(stack);

            entity.getPersistentData()
                    .putInt("VisualBacktankAir", Math.round(visualBacktankAir));
        }

        boolean inFluid = !entity.isEyeInFluidType(NeoForgeMod.EMPTY_TYPE.value());
        boolean inSpace = GlobalSpacePhysicsHandler.getFlags(entity).contains(AtmosphereFlags.LOW_DENSITY);

        int period = inSpace ? 4 : inFluid ? 2 : 1;

        if (level.getGameTime() % period == 0)
            BacktankUtil.consumeAir(entity, backtanks.getFirst(), RocketConfig.SERVER.legThrusterBaseConsumption.getAsInt());
    }

    private static boolean handleInertialDamping(Player entity) {
        if (!(entity instanceof FreeMotionEntity fme)) return false;

        ItemStack wornItem = getWornItem(entity);
        if (wornItem.isEmpty()) {
            fme.setDampenerForce(0);
            return false;
        }

        if (!((LegThrustersItem) wornItem.getItem()).isActive(wornItem)) {
            fme.setDampenerForce(0);
            return false;
        }

        UUID relativeID = wornItem.get(RocketDataComponents.DAMPENER_RELATIVE_SUBLEVEL);
        SubLevelContainer c = relativeID != null ? SubLevelContainer.getContainer(entity.level()) : null;
        SubLevel relative = c != null ? c.getSubLevel(relativeID) : null;
        if (relative != null && ((EntityMovementExtension) entity).sable$getTrackingSubLevel() != relative) {
            ((LivingEntityMovementExtension) entity).sable$getInheritedVelocity().set(relative.logicalPose().position().sub(relative.lastPose().position(), new Vector3d()));
        }

        fme.setDampenerForce((float)RocketConfig.SERVER.legThrusterThrustFactor.getAsDouble());
        return true;
    }

    public boolean isActive(ItemStack stack) {
        return stack.getOrDefault(RocketDataComponents.SYSTEMS_ACTIVE, false);
    }

    public boolean setActive(ItemStack stack, boolean active) {
        return Boolean.TRUE.equals(stack.set(RocketDataComponents.SYSTEMS_ACTIVE, active));
    }

    public static boolean legThrustersActive(Player entity) {
        if (entity.hasInfiniteMaterials() || !isWornBy(entity)) {
            return false;
        }

        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(entity);
        if (backtanks.isEmpty()) {
            return false;
        }

        ItemStack worn = getWornItem(entity);
        if (((LegThrustersItem) worn.getItem()).isActive(worn)) {
            return true;
        }
        return true;
    }
}
