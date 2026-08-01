package dev.devce.rocketnautics.content.blocks;

import com.mojang.blaze3d.vertex.PoseStack;
import com.simibubi.create.foundation.blockEntity.renderer.SafeBlockEntityRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class CreativeThrusterRenderer extends SafeBlockEntityRenderer<CreativeThrusterBlockEntity> {

    public CreativeThrusterRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    protected void renderSafe(CreativeThrusterBlockEntity be, float partialTicks, PoseStack ms, MultiBufferSource buffer, int light, int overlay) {
    }
}
