package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.content.blocks.BoosterThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.EngineNozzleBlockEntity;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlockEntity;
import dev.devce.rocketnautics.content.blocks.RocketThrusterBlock;
import dev.devce.rocketnautics.content.blocks.EngineNozzleBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Camera;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = RocketNautics.MODID, value = Dist.CLIENT)
public class ExhaustClientRenderer {

    // Thread-safe map to track active plume sources in the loaded world sections
    private static final Map<BlockPos, PlumeInfo> ACTIVE_PLUMES = new ConcurrentHashMap<>();

    public static class PlumeInfo {
        public final BlockPos pos;
        public final Direction facing;
        public final float throttle;
        public final float ignitionTicks;
        public final float yOffset;

        public PlumeInfo(BlockPos pos, Direction facing, float throttle, float ignitionTicks, float yOffset) {
            this.pos = pos;
            this.facing = facing;
            this.throttle = throttle;
            this.ignitionTicks = ignitionTicks;
            this.yOffset = yOffset;
        }
    }

    /**
     * Registers or updates an active engine plume to be rendered in the level translucent stage.
     */
    public static void registerPlume(BlockPos pos, Direction facing, float throttle, float ignitionTicks, float yOffset) {
        ACTIVE_PLUMES.put(pos, new PlumeInfo(pos, facing, throttle, ignitionTicks, yOffset));
    }

    /**
     * Removes an engine plume from the active rendering list when it cools down or is unloaded.
     */
    public static void removePlume(BlockPos pos) {
        ACTIVE_PLUMES.remove(pos);
    }

    /**
     * Clears all tracked plumes (e.g. when changing dimensions or leaving world).
     */
    public static void clear() {
        ACTIVE_PLUMES.clear();
    }

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        // Render exhaust plumes AFTER particles and translucent blocks (water/clouds) have been fully drawn.
        // This ensures the custom shader depth-tests against them perfectly without any bleed-through.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || ACTIVE_PLUMES.isEmpty()) return;

        Camera camera = event.getCamera();
        double camX = camera.getPosition().x;
        double camY = camera.getPosition().y;
        double camZ = camera.getPosition().z;

        PoseStack ms = event.getPoseStack();

        for (PlumeInfo plume : ACTIVE_PLUMES.values()) {
            // Verify if block entity is still loaded/valid to prevent phantom rendering
            BlockEntity be = mc.level.getBlockEntity(plume.pos);
            if (be == null) {
                ACTIVE_PLUMES.remove(plume.pos);
                continue;
            }

            // Frustum/Distance check to skip far away plumes
            double distanceSq = plume.pos.distToCenterSqr(camX, camY, camZ);
            if (distanceSq > 16384.0) continue; // 128 block render limit

            ms.pushPose();

            // Translate matrix to plume position relative to the main camera
            double relX = plume.pos.getX() - camX;
            double relY = plume.pos.getY() - camY;
            double relZ = plume.pos.getZ() - camZ;
            ms.translate(relX, relY, relZ);

            // Rotate based on thruster facing direction (similar to block renderer translations)
            ms.translate(0.5, 0.5, 0.5);
            ms.mulPose(plume.facing.getRotation());
            ms.mulPose(com.mojang.math.Axis.XP.rotationDegrees(180));
            ms.translate(-0.5, -0.5, -0.5);

            // Apply translation offset to sit on the nozzle exit face
            ms.pushPose();
            ms.translate(0.5, plume.yOffset, 0.5);

            // Draw the volumetric plume using the MultiBufferSource.
            // Under AFTER_PARTICLES, the buffers are flushed immediately at the correct pipeline order.
            ExhaustRenderer.renderExhaustPlume(ms, mc.renderBuffers().bufferSource(), plume.throttle, plume.ignitionTicks, plume.facing);

            ms.popPose();
            ms.popPose();
        }
    }

    /**
     * Ticks the client thrusters to populate the ACTIVE_PLUMES rendering list.
     */
    public static void tickClientThruster(BlockEntity be) {
        if (be.getLevel() == null || !be.getLevel().isClientSide) return;

        if (be instanceof RocketThrusterBlockEntity rocket) {
            var thrust = rocket.thrust;
            if (thrust != null && thrust.isActive() && thrust.getThrottle() > 0.01f) {
                Direction facing = rocket.getBlockState().getValue(RocketThrusterBlock.FACING);
                registerPlume(rocket.getBlockPos(), facing, thrust.getThrottle(), thrust.getIgnitionTicks(), 0.1f);
            } else {
                removePlume(rocket.getBlockPos());
            }
        } 
        else if (be instanceof BoosterThrusterBlockEntity booster) {
            if (booster.isActive()) {
                Direction facing = booster.getBlockState().getValue(RocketThrusterBlock.FACING);
                int maxLimit = dev.devce.rocketnautics.RocketConfig.SERVER.brokenBarrier.get() ? 100 : 20;
                float throttle = booster.thrustPower != null ? (booster.thrustPower.getValue() / (float) maxLimit) : 1.0f;
                registerPlume(booster.getBlockPos(), facing, throttle, booster.ignitionTicks, 0.1f);
            } else {
                removePlume(booster.getBlockPos());
            }
        } 
        else if (be instanceof EngineNozzleBlockEntity nozzle) {
            if (nozzle.smoothedHeat > 0.05f) {
                Direction facing = nozzle.getBlockState().getValue(EngineNozzleBlock.FACING);
                float throttle = Mth.clamp(nozzle.smoothedHeat / 1.5f, 0f, 1f);
                registerPlume(nozzle.getBlockPos(), facing, throttle, throttle * 40.0f, 0.1f);
            } else {
                removePlume(nozzle.getBlockPos());
            }
        }
    }
}
