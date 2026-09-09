package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.MapColor;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class TerrainMapProgram implements MFDProgram {

    private static final int BUFFER_SIZE = 64 * 64;
    private final int[] cachedMap = new int[BUFFER_SIZE];
    private final int[] cachedHeights = new int[BUFFER_SIZE];
    private final Map<BlockState, Integer> colorCache = new ConcurrentHashMap<>();

    private long lastScanTime = 0;
    private int lastCenterX = Integer.MIN_VALUE;
    private int lastCenterZ = Integer.MIN_VALUE;
    private int lastCenterY = Integer.MIN_VALUE;

    @Override
    public String getName() {
        return "MAP";
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        Minecraft mc = Minecraft.getInstance();
        Level level = blockEntity != null ? blockEntity.getLevel() : null;
        if (level == null) {
            level = mc.level;
        }

        if (level == null) {
            renderStandby(canvas);
            return;
        }

        int centerX;
        int centerY;
        int centerZ;
        float headingYaw;

        ClientSubLevel clientSubLevel = (blockEntity != null) ? Sable.HELPER.getContainingClient(blockEntity) : null;
        if (clientSubLevel != null) {
            Vector3dc p = clientSubLevel.logicalPose().position();
            centerX = (int) Math.floor(p.x());
            centerY = (int) Math.floor(p.y());
            centerZ = (int) Math.floor(p.z());

            Quaterniondc shipRot = clientSubLevel.renderPose().orientation();
            Vector3d fwd = new Vector3d(0, 0, 1);
            shipRot.transform(fwd);
            headingYaw = (float) Math.toDegrees(Math.atan2(-fwd.x, fwd.z));
        } else if (blockEntity != null && blockEntity.getLevel() != null) {
            centerX = blockEntity.getBlockPos().getX();
            centerY = blockEntity.getBlockPos().getY();
            centerZ = blockEntity.getBlockPos().getZ();
            headingYaw = blockEntity.getBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                    ? blockEntity.getBlockState().getValue(BlockStateProperties.HORIZONTAL_FACING).toYRot()
                    : 0.0f;
        } else if (mc.player != null) {
            centerX = mc.player.getBlockX();
            centerY = mc.player.getBlockY();
            centerZ = mc.player.getBlockZ();
            headingYaw = mc.player.getYRot();
        } else {
            renderStandby(canvas);
            return;
        }

        long now = System.currentTimeMillis();
        boolean positionChanged = Math.abs(centerX - lastCenterX) >= 1
                || Math.abs(centerZ - lastCenterZ) >= 1
                || Math.abs(centerY - lastCenterY) >= 2;

        if (positionChanged || (now - lastScanTime) > 100) {
            scanTerrain(level, centerX, centerY, centerZ);
            lastScanTime = now;
            lastCenterX = centerX;
            lastCenterZ = centerZ;
            lastCenterY = centerY;
        }

        System.arraycopy(cachedMap, 0, canvas.getPixels(), 0, BUFFER_SIZE);
        canvas.markDirty();

        for (int i = 0; i < 64; i += 2) {
            blendPixel(canvas, 32, i, 0x25FFFFFF);
            blendPixel(canvas, i, 32, 0x25FFFFFF);
        }

        drawNearbyPlayers(canvas, level, centerX, centerZ);
        drawShipArrow(canvas, 32, 32, headingYaw);
    }

    private void scanTerrain(Level level, int centerX, int centerY, int centerZ) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int startX = centerX - 32;
        int startZ = centerZ - 32;

        for (int dz = 0; dz < 64; dz++) {
            int wz = startZ + dz;
            for (int dx = 0; dx < 64; dx++) {
                int wx = startX + dx;
                int idx = dz * 64 + dx;

                if (!level.hasChunkAt(wx, wz)) {
                    cachedMap[idx] = 0xFF080C14;
                    cachedHeights[idx] = centerY;
                    continue;
                }

                int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, wx, wz);
                boolean isUnderground = centerY < surfaceY - 3;
                int searchTop = isUnderground ? Math.min(centerY + 2, surfaceY) : Math.max(centerY, surfaceY);
                int searchBottom = Math.max(level.getMinBuildHeight(), searchTop - 64);

                pos.set(wx, searchTop, wz);
                BlockState foundState = null;
                int foundY = searchBottom;
                int waterDepth = 0;

                while (pos.getY() >= searchBottom) {
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir()) {
                        if (state.getFluidState().is(Fluids.WATER)) {
                            waterDepth++;
                            if (foundState == null) {
                                foundState = state;
                                foundY = pos.getY();
                            }
                        } else {
                            if (foundState == null) {
                                foundState = state;
                                foundY = pos.getY();
                            }
                            break;
                        }
                    }
                    pos.setY(pos.getY() - 1);
                }

                if (foundState == null) {
                    foundState = level.getBlockState(pos.set(wx, searchBottom, wz));
                    foundY = searchBottom;
                }

                cachedHeights[idx] = foundY;
                pos.set(wx, foundY, wz);
                int baseCol = resolveBlockColor(foundState, level, pos);

                if (waterDepth > 1) {
                    float depthShade = Math.max(0.45f, 1.0f - waterDepth * 0.05f);
                    baseCol = shadeColor(baseCol, depthShade);
                }

                cachedMap[idx] = baseCol;
            }
        }

        for (int dz = 0; dz < 64; dz++) {
            for (int dx = 0; dx < 64; dx++) {
                int idx = dz * 64 + dx;
                int y = cachedHeights[idx];
                int northY = (dz > 0) ? cachedHeights[(dz - 1) * 64 + dx] : y;
                int dy = y - northY;

                if (dy != 0) {
                    float factor = 1.0f;
                    if (dy > 0) {
                        factor = Math.min(1.35f, 1.0f + dy * 0.14f);
                    } else {
                        factor = Math.max(0.65f, 1.0f + dy * 0.12f);
                    }
                    cachedMap[idx] = shadeColor(cachedMap[idx], factor);
                }
            }
        }
    }

    private int resolveBlockColor(BlockState state, Level level, BlockPos pos) {
        try {
            int tint = Minecraft.getInstance().getBlockColors().getColor(state, level, pos, 0);
            if (tint != -1) {
                return 0xFF000000 | tint;
            }
        } catch (Throwable ignored) {}

        try {
            MapColor mapColor = state.getMapColor(level, pos);
            if (mapColor != null && mapColor != MapColor.NONE && mapColor.col != 0) {
                return 0xFF000000 | mapColor.col;
            }
        } catch (Throwable ignored) {}

        return colorCache.computeIfAbsent(state, s -> {
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(s.getBlock());
            int hash = id.hashCode();
            int r = 80 + Math.abs(hash % 120);
            int g = 80 + Math.abs((hash >> 8) % 120);
            int b = 80 + Math.abs((hash >> 16) % 120);
            return 0xFF000000 | (r << 16) | (g << 8) | b;
        });
    }

    private int shadeColor(int color, float factor) {
        int r = Math.min(255, (int) (((color >> 16) & 0xFF) * factor));
        int g = Math.min(255, (int) (((color >> 8) & 0xFF) * factor));
        int b = Math.min(255, (int) ((color & 0xFF) * factor));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void blendPixel(MFDCanvas canvas, int x, int y, int overlayArgb) {
        if (x < 0 || x >= 64 || y < 0 || y >= 64) return;
        int base = canvas.getPixel(x, y);
        float alpha = ((overlayArgb >> 24) & 0xFF) / 255.0f;
        int or = (overlayArgb >> 16) & 0xFF;
        int og = (overlayArgb >> 8) & 0xFF;
        int ob = overlayArgb & 0xFF;

        int br = (base >> 16) & 0xFF;
        int bg = (base >> 8) & 0xFF;
        int bb = base & 0xFF;

        int nr = (int) (br * (1 - alpha) + or * alpha);
        int ng = (int) (bg * (1 - alpha) + og * alpha);
        int nb = (int) (bb * (1 - alpha) + ob * alpha);

        canvas.setPixel(x, y, 0xFF000000 | (nr << 16) | (ng << 8) | nb);
    }

    private void drawShipArrow(MFDCanvas canvas, int cx, int cy, float yaw) {
        double rad = Math.toRadians(yaw);
        double fwdX = -Math.sin(rad);
        double fwdZ = Math.cos(rad);
        double rightX = -fwdZ;
        double rightZ = fwdX;

        int tipX = (int) Math.round(cx + fwdX * 3.8);
        int tipY = (int) Math.round(cy + fwdZ * 3.8);

        int lX = (int) Math.round(cx - fwdX * 2.2 - rightX * 2.5);
        int lY = (int) Math.round(cy - fwdZ * 2.2 - rightZ * 2.5);

        int rX = (int) Math.round(cx - fwdX * 2.2 + rightX * 2.5);
        int rY = (int) Math.round(cy - fwdZ * 2.2 + rightZ * 2.5);

        int nX = (int) Math.round(cx - fwdX * 0.8);
        int nY = (int) Math.round(cy - fwdZ * 0.8);

        drawThickLine(canvas, tipX, tipY, lX, lY, 0xFF000000);
        drawThickLine(canvas, tipX, tipY, rX, rY, 0xFF000000);
        drawThickLine(canvas, lX, lY, nX, nY, 0xFF000000);
        drawThickLine(canvas, rX, rY, nX, nY, 0xFF000000);

        drawLine(canvas, tipX, tipY, lX, lY, 0xFFFF2244);
        drawLine(canvas, tipX, tipY, rX, rY, 0xFFFF2244);
        drawLine(canvas, lX, lY, nX, nY, 0xFFFF2244);
        drawLine(canvas, rX, rY, nX, nY, 0xFFFF2244);

        canvas.setPixel(cx, cy, 0xFFFFFFFF);
    }

    private void drawNearbyPlayers(MFDCanvas canvas, Level level, int cx, int cz) {
        for (Player other : level.players()) {
            int relX = other.getBlockX() - cx + 32;
            int relZ = other.getBlockZ() - cz + 32;

            if (relX >= 2 && relX < 62 && relZ >= 2 && relZ < 62) {
                if (Math.abs(relX - 32) > 2 || Math.abs(relZ - 32) > 2) {
                    canvas.setPixel(relX, relZ - 1, 0xFF00FFCC);
                    canvas.setPixel(relX - 1, relZ, 0xFF00FFCC);
                    canvas.setPixel(relX, relZ, 0xFFFFFFFF);
                    canvas.setPixel(relX + 1, relZ, 0xFF00FFCC);
                    canvas.setPixel(relX, relZ + 1, 0xFF00FFCC);
                }
            }
        }
    }

    private void drawLine(MFDCanvas canvas, int x0, int y0, int x1, int y1, int col) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = (x0 < x1) ? 1 : -1;
        int sy = (y0 < y1) ? 1 : -1;
        int err = dx - dy;

        while (true) {
            if (x0 >= 0 && x0 < 64 && y0 >= 0 && y0 < 64) {
                canvas.setPixel(x0, y0, col);
            }
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x0 += sx;
            }
            if (e2 < dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    private void drawThickLine(MFDCanvas canvas, int x0, int y0, int x1, int y1, int col) {
        for (int ox = -1; ox <= 1; ox++) {
            for (int oy = -1; oy <= 1; oy++) {
                drawLine(canvas, x0 + ox, y0 + oy, x1 + ox, y1 + oy, col);
            }
        }
    }

    private void renderStandby(MFDCanvas canvas) {
        canvas.clear(0xFF060A12);
        canvas.drawRect(0, 0, 64, 64, 0xFF142436);
    }
}
