package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.RocketNauticsClient;
import dev.devce.rocketnautics.content.physics.SpaceTransitionHandler;
import dev.devce.rocketnautics.content.items.JetpackItem;
import dev.devce.rocketnautics.content.items.LegThrustersItem;
import dev.devce.rocketnautics.network.DampenersTogglePayload;
import dev.devce.rocketnautics.network.JetpackTogglePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.EquipmentSlot;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Event subscriber for game-bus client-side events.
 * Manages dynamic render distance, camera shake, and client-side tick logic.
 */
public class RocketNauticsClientEvents {
    private static float currentRoll = 0;
    private static float prevRoll = 0;

    /**
     * Handles dynamic render distance adjustment and jetpack input processing every tick.
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (mc.screen instanceof net.minecraft.client.gui.screens.ReceivingLevelScreen) return;

        dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager ropeManager =    
                dev.simulated_team.simulated.content.blocks.rope.strand.client.ClientLevelRopeManager.getOrCreate(mc.level);
        if (ropeManager != null) {
            ropeManager.tickInterpolation((double) mc.level.getGameTime());
        }

        // Force minimum render distance during seamless dimension transitions
        if (RocketNauticsClient.seamlessTransitionTicks > 0) {
            if (mc.options.renderDistance().get() > 2) {
                mc.options.renderDistance().set(2);
                RocketNauticsClient.lastAppliedRenderDistance = 2;
            }
            return;
        }

        int currentDist = mc.options.renderDistance().get();
        
        // Capture original render distance if not yet set
        if (RocketNauticsClient.originalRenderDistance == -1) {
            double y = mc.player.getY();
            String dim = mc.level.dimension().location().getPath();
            double threshold = SpaceTransitionHandler.OVERWORLD_SPACE_Y;
            boolean isAtForcedAltitude = (dim.equals("overworld") && y > threshold - 5000) || (dim.equals("deep_space") && y < 800);

            // Avoid capturing the 'forced' value of 2 as the original setting
            if (currentDist > 2 || !isAtForcedAltitude) {
                RocketNauticsClient.originalRenderDistance = currentDist;
                RocketNauticsClient.lastAppliedRenderDistance = currentDist;
            }
        } else {
            // Update original setting if user manually changed it in options
            if (RocketNauticsClient.lastAppliedRenderDistance != -1 && currentDist != RocketNauticsClient.lastAppliedRenderDistance) {
                RocketNauticsClient.originalRenderDistance = currentDist;
                RocketNauticsClient.lastAppliedRenderDistance = currentDist;
            }
        }

        if (RocketNauticsClient.originalRenderDistance != -1) {
            int targetDist = RocketNauticsClient.originalRenderDistance;

            // Apply dynamic adjustment based on altitude to prevent lag at high views
            if (RocketConfig.CLIENT.enableDynamicRenderDistance.get()) {
                double y = mc.player.getY();
                String dim = mc.level.dimension().location().getPath();
                double threshold = SpaceTransitionHandler.OVERWORLD_SPACE_Y;

                if (dim.equals("overworld")) {
                    if (y > threshold - 500) targetDist = 2;
                    else if (y > threshold - 1000) targetDist = 4;
                    else if (y > threshold - 2000) targetDist = 8;
                    else if (y > threshold - 5000) targetDist = 12;
                } else if (dim.equals("deep_space")) {
                    if (y < 200) targetDist = 2;
                    else if (y < 400) targetDist = 4;
                    else if (y < 600) targetDist = 8;
                    else if (y < 800) targetDist = 12;
                }
            }

            // Gradually transition to target distance to avoid stutter
            if (targetDist != currentDist && mc.level.getGameTime() % 10 == 0) {
                int nextDist = currentDist < targetDist ? Math.min(currentDist + 2, targetDist) : Math.max(currentDist - 2, targetDist);
                mc.options.renderDistance().set(nextDist);
                RocketNauticsClient.lastAppliedRenderDistance = nextDist;
            }
        }

        
        while (RocketNauticsClient.JETPACK_TOGGLE.consumeClick()) {
            if (mc.player.getItemBySlot(EquipmentSlot.CHEST).getItem() instanceof JetpackItem) {
                PacketDistributor.sendToServer(new JetpackTogglePayload());
            }
        }

        while (RocketNauticsClient.DAMPENERS_TOGGLE.consumeClick()) {
            if (mc.player.getItemBySlot(EquipmentSlot.LEGS).getItem() instanceof LegThrustersItem) {
                PacketDistributor.sendToServer(new DampenersTogglePayload());
            }
        }

        CameraShakeHandler.tick();
    }

    @SubscribeEvent
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        float[] angles = new float[]{event.getPitch(), event.getYaw(), event.getRoll()};
        CameraShakeHandler.applyShake((float)event.getPartialTick(), angles);
        event.setPitch(angles[0]);
        event.setYaw(angles[1]);
        event.setRoll(angles[2]);
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        RocketNauticsClient.onRenderLevelStage(event);
    }
}
