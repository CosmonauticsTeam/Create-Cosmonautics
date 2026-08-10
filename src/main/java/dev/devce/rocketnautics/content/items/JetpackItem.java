package dev.devce.rocketnautics.content.items;

import com.simibubi.create.content.equipment.armor.BacktankUtil;
import com.simibubi.create.content.equipment.armor.BaseArmorItem;
import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.FreeMotionEntity;
import dev.devce.rocketnautics.api.capability.IBacktank;
import dev.devce.rocketnautics.api.capability.JetpackFluidHandlerItemStack;
import dev.devce.rocketnautics.client.FreeMotionHandler;
import dev.devce.rocketnautics.client.render.JetpackLayer;
import dev.devce.rocketnautics.content.particles.JetpackFlameParticle;
import dev.devce.rocketnautics.content.physics.GlobalSpacePhysicsHandler;
import dev.devce.rocketnautics.mixin.BucketItemAccessor;
import dev.devce.rocketnautics.network.FreeMotionSetupPayload;
import dev.devce.rocketnautics.registry.RocketDataComponents;
import dev.devce.rocketnautics.registry.RocketItems;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

@EventBusSubscriber
public class JetpackItem extends BaseArmorItem implements IBacktank {

    //TODO: give custom inv icon
    //TODO: rewrite the spaghetti

    @Override
    public @Nullable ResourceLocation getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, ArmorMaterial.Layer layer, boolean innerModel) {
        return ResourceLocation.parse(String.format(Locale.ROOT, "%s:textures/models/armor/empty.png", textureLoc.getNamespace(), textureLoc.getPath()));
    }

    public JetpackItem(Properties properties, ResourceLocation textureLoc) {
        super(ArmorMaterials.NETHERITE, ArmorItem.Type.CHESTPLATE, properties, textureLoc);
    }

    @SubscribeEvent
    public static void onEntityTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!(player instanceof FreeMotionEntity fme)) return;
        AnchorBootsItem.accelerateDescentNearBlock(event);

        ItemStack worn = getWornItem(player);
        if (!(worn.getItem() instanceof JetpackItem j)) {
            fme.setAmbulant(false);
        }

        List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
        if (backtanks.isEmpty()) {
            fme.setAmbulant(false);
            setActive(worn, false);
        }

        if (!fme.isAmbulant() && fme.is6DOFEnabled() && player.onGround()) {
            fme.set6DOFEnabled(false);
            setActive(worn, false);
        }

        if (isActive(player)) {
            applyJetpackPhysics(player);
            fme.set6DOFEnabled(true);
            fme.setAmbulant(true);

            if (!player.level().isClientSide) {
                PacketDistributor.sendToPlayer((ServerPlayer)player,
                        new FreeMotionSetupPayload(
                                fme.is6DOFEnabled(),
                                fme.isAmbulant(),
                                fme.getMovementAcceleration(),
                                fme.getDampenerForce()
                        )
                );
            }
        } else fme.setAmbulant(false);
    }

    private static boolean applyJetpackPhysics(Player player) {
        Level level = player.level();
        if (level.isClientSide && !GlobalSpacePhysicsHandler.shouldDisplayTimer(player))
            player.getPersistentData().remove("VisualBacktankAir");

        if (!(player instanceof FreeMotionEntity fme)) return false;
        fme.setAmbulant(false);

        if (!fme.is6DOFEnabled()) return false;

        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!(chest.getItem() instanceof JetpackItem)) {
            return false;
        }
        var cap = chest.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap == null) return false;

        Vector3f thrustStrength = FreeMotionHandler.getThrustStrength(player.getId());

        // TODO: with dynamic consumption its probably better to replace the default timer with percentage on the HUD

        int thrustConsumption =  fme.isAmbulant() ? (int)(RocketConfig.SERVER.jetpackThrustConsumption.get() * (thrustStrength.lengthSquared() / 3)) : 0;
        int drain = cap.drain(thrustConsumption, IFluidHandler.FluidAction.SIMULATE).getAmount();
        if (drain < thrustConsumption) return false;

        if (!player.hasInfiniteMaterials()) {
            cap.drain(thrustConsumption, IFluidHandler.FluidAction.EXECUTE);
        }

        if (level.isClientSide) {
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            if (!backtanks.isEmpty()) {
                float visualBacktankAir = 0f;
                for (ItemStack stack : backtanks)
                    visualBacktankAir += BacktankUtil.getAir(stack);
                player.getPersistentData()
                        .putInt("VisualBacktankAir", Math.round(visualBacktankAir));
            }
        }

        fme.setAmbulant(true);
        return true;
    }

    @SubscribeEvent
    public static void onPlayerRender(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();

        if (!(entity instanceof Player player)) return;
        if (!(player instanceof FreeMotionEntity fme)) return;

        if (!fme.is6DOFEnabled() || !fme.isAmbulant()) return;

        Vector3f thrustStrength = FreeMotionHandler.getThrustStrength(player.getId());

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.BODY, new Vector3f(0, 11, 8), new Vector3f(0.0f, 2.0f, 4.0f), 3.0f, thrustStrength, 2.0f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.BODY, new Vector3f(0, 6, -6), new Vector3f(0.0f, 0.0f, -2.0f), 3.0f, thrustStrength, 2.0f, player);

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.BODY, new Vector3f(6, 2.5f, 3.5f), new Vector3f(1.5f, 0.0f, 0.0f), 0.75f, thrustStrength, 0.75f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.BODY, new Vector3f(-6, 2.5f, 3.5f), new Vector3f(-1.5f, 0.0f, 0.0f), 0.75f, thrustStrength, 0.75f, player);

        Vector3f arm_origin = new Vector3f(-0.5f, 8.0f, 5.0f);
        Vector3f common_velocity = new Vector3f(0.0f, 2.0f, 1.0f);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.RIGHT_ARM, new Vector3f(arm_origin), common_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.LEFT_ARM, new Vector3f(arm_origin).mul(-1, 1, 1), common_velocity, 1.0f, thrustStrength, 1.0f, player);

        Vector3f leg_down_main_origin = new Vector3f(0, 12, 5);

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_down_main_origin), common_velocity, 2.0f, thrustStrength, 1.5f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_down_main_origin).mul(-1, 1, 1), common_velocity, 2.0f, thrustStrength, 1.5f, player);

        Vector3f leg_up_main_origin = new Vector3f(0, 3, 4);
        Vector3f leg_up_main_velocity = new Vector3f(common_velocity).mul(1, -1, 1);

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_up_main_origin), leg_up_main_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_up_main_origin).mul(-1, 1, 1), leg_up_main_velocity, 1.0f, thrustStrength, 1.0f, player);

        Vector3f leg_sides_main_origin = new Vector3f(-4.75f, 8f, 0.0f);
        Vector3f leg_sides_main_velocity = new Vector3f(-1.0f, 2.0f, 0.0f);

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_sides_main_origin), leg_sides_main_velocity, 1.0f, thrustStrength, 1.0f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_sides_main_origin).mul(-1, 1, 1), new Vector3f(leg_sides_main_velocity).mul(-1, 1, 1), 1.0f, thrustStrength, 1.0f, player);


        Vector3f leg_forward_aux_origin = new Vector3f(-3f, 3f, -3f);
        Vector3f leg_forward_aux_velocity = new Vector3f(0.0f, 0.0f, -1.0f);

        spawnJetpackFlame(JetpackLayer.JetpackModelPart.RIGHT_LEG, new Vector3f(leg_forward_aux_origin), leg_forward_aux_velocity, 0.75f, thrustStrength, 0.75f, player);
        spawnJetpackFlame(JetpackLayer.JetpackModelPart.LEFT_LEG, new Vector3f(leg_forward_aux_origin).mul(-1, 1, 1), leg_forward_aux_velocity, 0.75f, thrustStrength, 0.75f, player);
    }

    private static void spawnJetpackFlame(JetpackLayer.JetpackModelPart anchor, Vector3f origin, Vector3f velocity, float maxThrust, Vector3f thrust, float scale, Player player) {
        Vector3f tail = new Vector3f(origin).add(velocity);

        JetpackLayer.modelPart2worldSpace(player, anchor, origin);
        JetpackLayer.modelPart2worldSpace(player, anchor, tail);

        Vector3f delta = new Vector3f(tail).sub(origin);
        Vector3f direction = new Vector3f(delta).mul(-1).normalize();
        float thrustStrength = (direction.dot(new Vector3f(thrust).normalize()) + 1.0f) * 0.5f;

        delta.mul(maxThrust * thrustStrength * 0.5f);

        if (thrustStrength > 0.1)
            Minecraft.getInstance().player.level().addParticle(new JetpackFlameParticle.JetpackFlameParticleOptions(new Vector3f(0.1f, 1.0f, 1.0f), scale * thrustStrength, 3), origin.x, origin.y, origin.z, delta.x, delta.y, delta.z);
    }

    @SubscribeEvent
    public static void onCapabilityRegister(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.FluidHandler.ITEM, (stack, ctx) -> new JetpackFluidHandlerItemStack(() -> RocketDataComponents.JETPACK_FUEL, stack, 8000, 4000), RocketItems.JETPACK);
    }

    public static void toggle(ServerPlayer player) {
        if (!(player instanceof FreeMotionEntity fme)) return;

        ItemStack worn = getWornItem(player);
        if (worn.getItem() instanceof JetpackItem j) {
            List<ItemStack> backtanks = BacktankUtil.getAllWithAir(player);
            if (backtanks.isEmpty()) return;

            boolean wasActive = j.setActive(worn, !j.isActive(worn));

            fme.setAmbulant(!wasActive);

            if (!fme.is6DOFEnabled() && !wasActive) fme.set6DOFEnabled(true);

            PacketDistributor.sendToPlayer(player,
                new FreeMotionSetupPayload(
                    fme.is6DOFEnabled(),
                    !wasActive,
                    fme.getMovementAcceleration(),
                    fme.getDampenerForce()
                )
            );

            if (wasActive) {
                player.displayClientMessage(Component.translatable("rocketnautics.jetpack.disabled").withStyle(ChatFormatting.RED), true);
            } else {
                player.displayClientMessage(Component.translatable("rocketnautics.jetpack.enabled").withStyle(ChatFormatting.GREEN), true);
            }
        }
    }

    public static boolean isActive(Player player) {
        ItemStack worn = getWornItem(player);
        if (worn.getItem() instanceof JetpackItem j) {
            return isActive(worn);
        }
        return false;
    }

    public static boolean isActive(ItemStack stack) {
        return stack.getOrDefault(RocketDataComponents.SYSTEMS_ACTIVE, false);
    }

    public static boolean setActive(ItemStack stack, boolean active) {
        return Boolean.TRUE.equals(stack.set(RocketDataComponents.SYSTEMS_ACTIVE, active));
    }

    public static boolean isWornBy(Entity entity) {
        return !getWornItem(entity).isEmpty();
    }

    public static ItemStack getWornItem(Entity entity) {
        if (!(entity instanceof LivingEntity livingEntity)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = livingEntity.getItemBySlot(EquipmentSlot.CHEST);
        if (!(stack.getItem() instanceof JetpackItem)) {
            return ItemStack.EMPTY;
        }
        return stack;
    }

    @Override
    public ItemStack getDefaultInstance() {
        ItemStack s = new ItemStack(this);
        var cap = s.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap != null) {
            cap.fill(new FluidStack(Fluids.LAVA, 8000), IFluidHandler.FluidAction.EXECUTE);
        }
        return s;
    }

    @Override
    public boolean overrideOtherStackedOnMe(ItemStack me, ItemStack other, Slot slot, ClickAction action, Player player, SlotAccess carriedSlotAccess) {
        if (action != ClickAction.SECONDARY) return false;
        var otherCap = other.getCapability(Capabilities.FluidHandler.ITEM);
        if (otherCap != null) {
            FluidStack drainable = otherCap.drain(new FluidStack(Fluids.LAVA, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE);
            if (!drainable.isEmpty()) {
                var ourCap = me.getCapability(Capabilities.FluidHandler.ITEM);
                if (ourCap != null) {
                    int fillable = ourCap.fill(drainable, IFluidHandler.FluidAction.SIMULATE);
                    FluidStack drained = otherCap.drain(new FluidStack(Fluids.LAVA, fillable), IFluidHandler.FluidAction.EXECUTE);
                    ourCap.fill(drained, IFluidHandler.FluidAction.EXECUTE);
                    carriedSlotAccess.set(otherCap.getContainer());
                    if (other.getItem() instanceof BucketItem b) {
                        ((BucketItemAccessor) b).rocketnautics$playEmptySound(player, player.level(), player.blockPosition());
                    }
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public boolean isEnchantable(ItemStack p_77616_1_) {
        return true;
    }

    @Override
    public boolean supportsEnchantment(ItemStack stack, Holder<Enchantment> enchantment) {
        if (enchantment.is(Enchantments.MENDING) || enchantment.is(Enchantments.UNBREAKING))
            return false;
        return super.supportsEnchantment(stack, enchantment);
    }

    @Override
    public boolean isBarVisible(ItemStack p_150899_) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * Mth.clamp(getAir(stack) / ((float) getMaxAirCapacity(stack)), 0, 1));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xfa9600;
    }

    @Override
    public int getMaxAirCapacity(ItemStack backtank) {
        var cap = backtank.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap == null) return IBacktank.super.getMaxAirCapacity(backtank);
        return cap.getTankCapacity(0);
    }

    @Override
    public int getAir(ItemStack backtank) {
        var cap = backtank.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap == null) return IBacktank.super.getAir(backtank);
        return cap.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).getAmount();
    }

    @Override
    public Integer setAir(ItemStack backtank, int newAir) {
        var cap = backtank.getCapability(Capabilities.FluidHandler.ITEM);
        if (cap == null) return IBacktank.super.setAir(backtank, newAir);
        FluidStack contained = cap.getFluidInTank(0);
        if (contained.isEmpty() && newAir > 0) {
            contained = new FluidStack(Fluids.LAVA, newAir);
            cap.fill(contained, IFluidHandler.FluidAction.EXECUTE);
        } else if (newAir > contained.getAmount()) {
            FluidStack fill = contained.copyWithAmount(newAir - contained.getAmount());
            cap.fill(fill, IFluidHandler.FluidAction.EXECUTE);
        } else if (newAir < contained.getAmount()) {
            cap.drain(contained.getAmount() - newAir, IFluidHandler.FluidAction.EXECUTE);
        }
        return contained.getAmount();
    }
}