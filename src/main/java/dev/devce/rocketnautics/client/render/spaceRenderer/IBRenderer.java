package dev.devce.rocketnautics.client.render.spaceRenderer;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import dev.devce.rocketnautics.content.orbit.DeepSpaceData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class IBRenderer {
    private static final ResourceLocation FORCEFIELD_LOCATION = ResourceLocation.withDefaultNamespace("textures/misc/forcefield.png");
    private static final float FORCEFIELD_DIST = 8;

    public static void render(PoseStack ps, Vec3 pos) {
        VoxelShape box = DeepSpaceData.getBoxForPosition(pos);
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.blendFuncSeparate(
                GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE, GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ZERO
        );
        RenderSystem.setShaderTexture(0, FORCEFIELD_LOCATION);
        RenderSystem.depthMask(Minecraft.useShaderTransparency());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.polygonOffset(-3.0F, -3.0F);
        RenderSystem.enablePolygonOffset();
        RenderSystem.disableCull();
        float maxX = (float) box.max(Direction.Axis.X);
        float minX = (float) box.min(Direction.Axis.X);
        float maxY = (float) box.max(Direction.Axis.Y);
        float minY = (float) box.min(Direction.Axis.Y);
        float maxZ = (float) box.max(Direction.Axis.Z);
        float minZ = (float) box.min(Direction.Axis.Z);
        float s = (float) Math.min(Minecraft.getInstance().gameRenderer.getDepthFar(), box.bounds().getXsize());
        float s2 = s / 2;
        float clampedX = (float) Mth.clamp(pos.x(), minX + s2, maxX - s2);
        float properX = (float) (clampedX - pos.x());
        float clampedY = (float) Mth.clamp(pos.y(), minY + s2, maxY - s2);
        float properY = (float) (clampedY - pos.y());
        float clampedZ = (float) Mth.clamp(pos.z(), minZ + s2, maxZ - s2);
        float properZ = (float) (clampedZ - pos.z());

        if (pos.x() > maxX - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxX - pos.x());
            double frac = Math.min(1, 1 - d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), d, properY + s2, properZ - s2).setUv(0 + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY + s2, properZ + s2).setUv(s + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY - s2, properZ + s2).setUv(s + clampedZ, s - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY - s2, properZ - s2).setUv(0 + clampedZ, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (pos.x() < minX + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minX - pos.x());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), d, properY + s2, properZ - s2).setUv(0 + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY + s2, properZ + s2).setUv(s + clampedZ, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY - s2, properZ + s2).setUv(s + clampedZ, s - clampedY);
            bufferbuilder.addVertex(ps.last(), d, properY - s2, properZ - s2).setUv(0 + clampedZ, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (pos.y() > maxY - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxY - pos.y());
            double frac = Math.min(1, 1 - d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), properX + s2, d, properZ - s2).setUv(0 + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(ps.last(), properX + s2, d, properZ + s2).setUv(s + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(ps.last(), properX - s2, d, properZ + s2).setUv(s + clampedZ, s - clampedX);
            bufferbuilder.addVertex(ps.last(), properX - s2, d, properZ - s2).setUv(0 + clampedZ, s - clampedX);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (pos.y() < minY + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minY - pos.y());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), properX + s2, d, properZ - s2).setUv(0 + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(ps.last(), properX + s2, d, properZ + s2).setUv(s + clampedZ, 0 - clampedX);
            bufferbuilder.addVertex(ps.last(), properX - s2, d, properZ + s2).setUv(s + clampedZ, s - clampedX);
            bufferbuilder.addVertex(ps.last(), properX - s2, d, properZ - s2).setUv(0 + clampedZ, s - clampedX);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (pos.z() > maxZ - FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (maxZ - pos.z());
            double frac = Math.min(1, 1 - (maxZ - pos.z()) / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), properX - s2, properY + s2, d).setUv(0 + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), properX + s2, properY + s2, d).setUv(s + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), properX + s2, properY - s2, d).setUv(s + clampedX, s - clampedY);
            bufferbuilder.addVertex(ps.last(), properX - s2, properY - s2, d).setUv(0 + clampedX, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }
        if (pos.z() < minZ + FORCEFIELD_DIST) {
            BufferBuilder bufferbuilder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float d = (float) (minZ - pos.z());
            double frac = Math.min(1, 1 + d / FORCEFIELD_DIST);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, (float) frac);

            bufferbuilder.addVertex(ps.last(), properX - s2, properY + s2, d).setUv(0 + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), properX + s2, properY + s2, d).setUv(s + clampedX, 0 - clampedY);
            bufferbuilder.addVertex(ps.last(), properX + s2, properY - s2, d).setUv(s + clampedX, s - clampedY);
            bufferbuilder.addVertex(ps.last(), properX - s2, properY - s2, d).setUv(0 + clampedX, s - clampedY);
            BufferUploader.drawWithShader(bufferbuilder.buildOrThrow());
        }

        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.polygonOffset(0.0F, 0.0F);
        RenderSystem.disablePolygonOffset();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
