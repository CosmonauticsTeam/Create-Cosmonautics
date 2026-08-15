package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlock;
import dev.devce.rocketnautics.content.blocks.gyrodyne.GyrodyneBlockEntity;
import dev.devce.rocketnautics.registry.RocketPartials;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class GyrodyneRenderer extends SafeBlockEntityRenderer<GyrodyneBlockEntity> {

    public GyrodyneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(GyrodyneBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState blockState = be.getBlockState();
        Direction facing = blockState.hasProperty(GyrodyneBlock.FACING) ? blockState.getValue(GyrodyneBlock.FACING) : Direction.UP;

        float rotorAngle = be.getRotorAngle(partialTicks);
        float tiltX = be.getGimbalTiltX(partialTicks);
        float tiltZ = be.getGimbalTiltZ(partialTicks);

        var modelRenderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var renderBuffer = buffer.getBuffer(RenderType.cutout());

        ms.pushPose();

        // Translate to block center (8, 8, 8)
        ms.translate(0.5, 0.5, 0.5);

        // Orient to block facing
        Quaternionf facingRotation = getFacingRotation(facing);
        ms.mulPose(facingRotation);

        // --- Render Outer Ring (Gimbal Ring 1) ---
        ms.pushPose();
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tiltX), 1, 0, 0)));
        ms.translate(-0.5, -0.5, -0.5);
        if (RocketPartials.gyrodyneRing1 != null) {
            modelRenderer.renderModel(ms.last(), renderBuffer, blockState, RocketPartials.gyrodyneRing1, 1.0f, 1.0f, 1.0f, light, overlay);
        }
        ms.popPose();

        // --- Render Inner Ring (Gimbal Ring 2) ---
        ms.pushPose();
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tiltX), 1, 0, 0)));
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tiltZ), 0, 0, 1)));
        ms.translate(-0.5, -0.5, -0.5);
        if (RocketPartials.gyrodyneRing2 != null) {
            modelRenderer.renderModel(ms.last(), renderBuffer, blockState, RocketPartials.gyrodyneRing2, 1.0f, 1.0f, 1.0f, light, overlay);
        }
        ms.popPose();

        // --- Render Flywheel Rotor ---
        ms.pushPose();
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tiltX), 1, 0, 0)));
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(tiltZ), 0, 0, 1)));
        ms.mulPose(new Quaternionf(new AxisAngle4f((float) Math.toRadians(rotorAngle), 0, 1, 0)));
        ms.translate(-0.5, -0.5, -0.5);
        if (RocketPartials.gyrodyneRotor != null) {
            modelRenderer.renderModel(ms.last(), renderBuffer, blockState, RocketPartials.gyrodyneRotor, 1.0f, 1.0f, 1.0f, light, overlay);
        }
        ms.popPose();

        ms.popPose();
    }

    private Quaternionf getFacingRotation(Direction facing) {
        return switch (facing) {
            case DOWN -> new Quaternionf().rotateX((float) Math.PI);
            case UP -> new Quaternionf();
            case NORTH -> new Quaternionf().rotateX((float) (Math.PI / 2)).rotateY((float) Math.PI);
            case SOUTH -> new Quaternionf().rotateX((float) (Math.PI / 2));
            case WEST -> new Quaternionf().rotateZ((float) (Math.PI / 2));
            case EAST -> new Quaternionf().rotateZ((float) (-Math.PI / 2));
        };
    }
}
