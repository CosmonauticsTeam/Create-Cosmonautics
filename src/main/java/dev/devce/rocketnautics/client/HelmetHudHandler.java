package dev.devce.rocketnautics.client;

import dev.devce.rocketnautics.RocketNauticsClient;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.items.SpaceHelmetItem;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Matrix4f;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.orekit.orbits.KeplerianOrbit;

@EventBusSubscriber(value = Dist.CLIENT)
public class HelmetHudHandler {
    public static boolean hudEnabled = true;
    private static boolean isLockedOn = false;

    public static boolean isHelmetWornInDeepSpace() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return false;
        if (!DeepSpaceHelper.isDeepSpace(mc.level)) return false;
        return mc.player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof SpaceHelmetItem;
    }

    public static Vector3d getLocalVelocityDir() {
        if (DeepSpaceHandler.getUniverse() == null || !DeepSpaceHandler.hasReceivedPosition()) return null;

        DeepSpacePosition pos = DeepSpaceHandler.getReceivedPosition();
        KeplerianOrbit orbit = pos.getCurrentOrbit();
        Vector3D orekitVelocity = orbit.getPVCoordinates().getVelocity();

        Vector3d localVelocity = new Vector3d(orekitVelocity.getX(), orekitVelocity.getY(), orekitVelocity.getZ());

        if (localVelocity.lengthSquared() < 1e-6) return null;
        return localVelocity.normalize();
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        if (!DeepSpaceHelper.isDeepSpace(mc.level)) {
            hudEnabled = false;
            isLockedOn = false;
        }

        // Toggle hudEnabled with key Y
        while (RocketNauticsClient.ALIGNMENT_TOGGLE.consumeClick()) {
            if (!DeepSpaceHelper.isDeepSpace(mc.level)) {
                mc.player.displayClientMessage(Component.literal("§cCannot enable HUD outside of Deep Space!"), true);
                continue;
            }
            hudEnabled = !hudEnabled;
            isLockedOn = false;
            mc.player.displayClientMessage(
                Component.literal("Space Helmet HUD & Alignment: " + (hudEnabled ? "§aENABLED" : "§cDISABLED")), 
                true
            );
        }

        if (!hudEnabled || !isHelmetWornInDeepSpace()) {
            isLockedOn = false;
            return;
        }

        Vector3d localVelocityDir = getLocalVelocityDir();
        if (localVelocityDir == null) return;

        Vec3 lookDir = mc.player.getLookAngle();
        double dot = lookDir.x * localVelocityDir.x + lookDir.y * localVelocityDir.y + lookDir.z * localVelocityDir.z;

        // If aimed very close, trigger lock-on
        if (dot > 0.985) {
            isLockedOn = true;
        } else if (dot < 0.95) {
            isLockedOn = false; // break lock-on if they look away significantly
        }

        if (isLockedOn) {
            // Keep the lock-on status color but do not force camera rotation
        }
    }

    @SubscribeEvent
    public static void onRenderGuiLayerPre(RenderGuiLayerEvent.Pre event) {
        if (!hudEnabled || !isHelmetWornInDeepSpace()) return;

        if (event.getName().equals(ResourceLocation.withDefaultNamespace("crosshair"))) {
            event.setCanceled(true); // Hide default crosshair

            int width = event.getGuiGraphics().guiWidth();
            int height = event.getGuiGraphics().guiHeight();
            int centerX = width / 2;
            int centerY = height / 2;
            int size = 3;

            boolean locked = isLockedOn;
            int hudColor = locked ? 0xFFFF4400 : 0xFF00E6FF;
            int crosshairColor = locked ? 0xBBFF4400 : 0xBB00E6FF;

            // Draw top line
            event.getGuiGraphics().fill(centerX - size, centerY - size, centerX + size, centerY - size + 1, crosshairColor);
            // Draw bottom line
            event.getGuiGraphics().fill(centerX - size, centerY + size - 1, centerX + size, centerY + size, crosshairColor);
            // Draw left line
            event.getGuiGraphics().fill(centerX - size, centerY - size + 1, centerX - size + 1, centerY + size - 1, crosshairColor);
            // Draw right line
            event.getGuiGraphics().fill(centerX + size - 1, centerY - size + 1, centerX + size, centerY + size - 1, crosshairColor);

            // Draw HUD Info
            Minecraft mc = Minecraft.getInstance();
            if (DeepSpaceHandler.getUniverse() != null && DeepSpaceHandler.hasReceivedPosition()) {
                DeepSpacePosition pos = DeepSpaceHandler.getReceivedPosition();
                KeplerianOrbit orbit = pos.getCurrentOrbit();
                double speed = orbit.getPVCoordinates().getVelocity().getNorm();
                double ecc = orbit.getE();

                // Find central planet
                CubePlanet planet = null;
                String frameName = pos.getFrame().getName();
                for (CubePlanet p : DeepSpaceHandler.getUniverse().getPlanets()) {
                    if (p.frame().getName().equals(frameName)) {
                        planet = p;
                        break;
                    }
                }

                double distance = orbit.getPVCoordinates().getPosition().getNorm();
                double alt = planet != null ? (distance - planet.radius()) : distance;

                String bodyName = planet != null ? planet.frame().getName() : "Deep Space";
                if (bodyName.length() > 0) {
                    bodyName = bodyName.substring(0, 1).toUpperCase() + bodyName.substring(1);
                }

                Component orbitingText = Component.translatable("gui.rocketnautics.hud.orbiting").append(": " + bodyName);
                Component altText;
                if (alt >= 100000) {
                    altText = Component.translatable("gui.rocketnautics.hud.altitude").append(String.format(java.util.Locale.US, ": %,.1f km", alt / 1000.0));
                } else {
                    altText = Component.translatable("gui.rocketnautics.hud.altitude").append(String.format(java.util.Locale.US, ": %,.0f m", alt));
                }
                Component speedText = Component.translatable("gui.rocketnautics.hud.speed").append(String.format(java.util.Locale.US, ": %,.1f m/s", speed));

                String eccStr = String.format(java.util.Locale.US, "%.3f", ecc);
                if (ecc >= 1.0) {
                    eccStr += " (" + Component.translatable("gui.rocketnautics.hud.escape").getString() + ")";
                }
                Component eccText = Component.translatable("gui.rocketnautics.hud.eccentricity").append(": " + eccStr);

                int textY = 15;
                int maxW = 0;
                maxW = Math.max(maxW, mc.font.width(orbitingText));
                maxW = Math.max(maxW, mc.font.width(altText));
                maxW = Math.max(maxW, mc.font.width(speedText));
                maxW = Math.max(maxW, mc.font.width(eccText));

                int textX = width - maxW - 15;
                int bracketX = textX - 5;

                // Draw a small background panel/lines for a futuristic HUD feel
                event.getGuiGraphics().fill(bracketX, textY - 2, bracketX + 1, textY + 40, crosshairColor);
                event.getGuiGraphics().fill(bracketX + 1, textY - 2, bracketX + 4, textY - 1, crosshairColor);
                event.getGuiGraphics().fill(bracketX + 1, textY + 39, bracketX + 4, textY + 40, crosshairColor);

                // Draw each line
                event.getGuiGraphics().drawString(mc.font, orbitingText, textX, textY, hudColor, true);
                event.getGuiGraphics().drawString(mc.font, altText, textX, textY + 10, hudColor, true);
                event.getGuiGraphics().drawString(mc.font, speedText, textX, textY + 20, hudColor, true);
                event.getGuiGraphics().drawString(mc.font, eccText, textX, textY + 30, hudColor, true);
            }
        }
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (!hudEnabled || event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) return;
        if (!isHelmetWornInDeepSpace()) return;

        Vector3d localVelocityDir = getLocalVelocityDir();
        if (localVelocityDir == null) return;

        Minecraft mc = Minecraft.getInstance();
        PoseStack poseStack = event.getPoseStack();
        Camera camera = mc.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        poseStack.translate(-camPos.x, -camPos.y, -camPos.z);

        // Position the target square 6 blocks in front of the player's head along the velocity vector
        Vec3 headPos = mc.player.getEyePosition(event.getPartialTick().getGameTimeDeltaPartialTick(true));
        Vec3 targetPos = headPos.add(localVelocityDir.x * 6.0, localVelocityDir.y * 6.0, localVelocityDir.z * 6.0);
        poseStack.translate(targetPos.x, targetPos.y, targetPos.z);

        // Billboarding
        poseStack.mulPose(camera.rotation());

        float size = 0.08f;
        Tesselator tesselator = Tesselator.getInstance();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        BufferBuilder buffer = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = poseStack.last().pose();

        // Render solid target square (bright orange/red when locked on, cyan when not)
        float r = isLockedOn ? 1.0f : 0.0f;
        float g = isLockedOn ? 0.4f : 0.8f;
        float b = isLockedOn ? 0.0f : 1.0f;
        float a = 0.7f;

        buffer.addVertex(matrix, -size, -size, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, size, -size, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, size, size, 0).setColor(r, g, b, a);
        buffer.addVertex(matrix, -size, size, 0).setColor(r, g, b, a);

        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
        poseStack.popPose();
    }
}
