package dev.devce.rocketnautics.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import dev.devce.rocketnautics.content.blocks.MFDBlock;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

@OnlyIn(Dist.CLIENT)
public class MFDRenderer extends SafeBlockEntityRenderer<MFDBlockEntity> {

    public MFDRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(MFDBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
        BlockState state = be.getBlockState();
        Direction facing = state.hasProperty(MFDBlock.FACING) ? state.getValue(MFDBlock.FACING) : Direction.NORTH;

        MFDCanvas canvas = be.getCanvas();
        if (canvas == null) return;

        be.render(partialTicks);

        String texName = "mfd_" + be.getBlockPos().asLong();
        canvas.bindTexture(texName);
        ResourceLocation textureLocation = canvas.getTextureLocation();
        if (textureLocation == null) return;

        ms.pushPose();
        ms.translate(0.5, 0.5, 0.5);

        Quaternionf facingRot = getFacingRotation(facing);
        ms.mulPose(facingRot);

        float min = -0.375f;
        float max = 0.375f;
        float z = 0.5005f;

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutout(textureLocation));
        Matrix4f mat = ms.last().pose();

        addVertex(vc, mat, min, max, z, 0.0f, 0.0f);
        addVertex(vc, mat, min, min, z, 0.0f, 1.0f);
        addVertex(vc, mat, max, min, z, 1.0f, 1.0f);
        addVertex(vc, mat, max, max, z, 1.0f, 0.0f);

        ms.popPose();
    }

    private static void addVertex(VertexConsumer vc, Matrix4f mat, float x, float y, float z, float u, float v) {
        vc.addVertex(mat, x, y, z)
                .setColor(255, 255, 255, 255)
                .setUv(u, v)
                .setOverlay(OverlayTexture.NO_OVERLAY)
                .setLight(LightTexture.FULL_BRIGHT)
                .setNormal(0.0f, 0.0f, 1.0f);
    }

    private Quaternionf getFacingRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> new Quaternionf().rotateY((float) Math.PI);
            case SOUTH -> new Quaternionf();
            case WEST -> new Quaternionf().rotateY((float) (-Math.PI / 2));
            case EAST -> new Quaternionf().rotateY((float) (Math.PI / 2));
            case UP -> new Quaternionf().rotateX((float) (-Math.PI / 2));
            case DOWN -> new Quaternionf().rotateX((float) (Math.PI / 2));
        };
    }
}
