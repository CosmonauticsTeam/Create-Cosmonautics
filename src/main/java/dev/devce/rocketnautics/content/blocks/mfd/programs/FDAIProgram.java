package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.content.blocks.MFDBlock;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.companion.math.Pose3dc;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;

public class FDAIProgram implements MFDProgram {

    private static final int RADIUS = 27;

    private static final int[][] BAYER4 = {
        { 0,  8,  2, 10},
        {12,  4, 14,  6},
        { 3, 11,  1,  9},
        {15,  7, 13,  5}
    };

    private static final int[][] SKY_BANDS = {
        n64(8,  40, 168),
        n64(0,  24, 136),
        n64(0,  12, 104),
        n64(0,   4,  72),
        n64(0,   0,  48)
    };

    private static final int[][] GND_BANDS = {
        n64(176, 80,  16),
        n64(144, 56,   8),
        n64(112, 40,   0),
        n64( 80, 24,   0),
        n64( 56, 12,   0)
    };

    private static final int[] SHADE_MULT = {56, 112, 168, 232};

    private static final int HORIZON_COLOR = 0xFFFFDD00;
    private static final int GRID_MAJOR    = 0xFFDDDDDD;
    private static final int GRID_MINOR    = 0xFF666666;
    private static final int NORTH_COLOR   = 0xFFFF2020;
    private static final int SOUTH_COLOR   = 0xFF2080FF;
    private static final int EAST_COLOR    = 0xFFFFFF00;
    private static final int WEST_COLOR    = 0xFFFF8800;
    private static final int BEZEL_OUTER   = 0xFF0A1828;
    private static final int BEZEL_INNER   = 0xFF1A3850;
    private static final int BEZEL_RIM     = 0xFF224466;
    private static final int RETICLE_CYN   = 0xFF00FFCC;
    private static final int RETICLE_DRK   = 0xFF003322;
    private static final int CENTER_RED    = 0xFFFF2244;

    private static RayLUT lut = null;

    private static class RayLUT {
        final int radius;
        final int[] pixelOffsets;
        final int[] pixX;
        final int[] pixY;
        final float[] nx;
        final float[] ny;
        final float[] nz;
        final int count;

        RayLUT(int width, int height, int radius) {
            this.radius = radius;
            int r2 = radius * radius;
            int cx = width / 2;
            int cy = height / 2;
            double invR = 1.0 / radius;

            int total = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                int maxDx = (int) Math.sqrt(r2 - dy * dy);
                total += maxDx * 2 + 1;
            }

            this.count = total;
            this.pixelOffsets = new int[total];
            this.pixX = new int[total];
            this.pixY = new int[total];
            this.nx = new float[total];
            this.ny = new float[total];
            this.nz = new float[total];

            int idx = 0;
            for (int dy = -radius; dy <= radius; dy++) {
                int maxDx = (int) Math.sqrt(r2 - dy * dy);
                int py = cy + dy;
                for (int dx = -maxDx; dx <= maxDx; dx++) {
                    int px = cx + dx;
                    double x = dx * invR;
                    double y = -dy * invR;
                    double z = Math.sqrt(Math.max(0.0, 1.0 - x * x - y * y));

                    pixelOffsets[idx] = py * width + px;
                    pixX[idx] = px;
                    pixY[idx] = py;
                    nx[idx] = (float) x;
                    ny[idx] = (float) y;
                    nz[idx] = (float) z;
                    idx++;
                }
            }
        }
    }

    @Override
    public String getName() {
        return "FDAI";
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        int w = canvas.width;
        int h = canvas.height;
        int cx = w / 2;
        int cy = h / 2;

        if (lut == null || lut.radius != RADIUS) {
            lut = new RayLUT(w, h, RADIUS);
        }

        canvas.clear(0xFF060810);

        Direction facing = resolveBlockFacing(blockEntity);
        Quaterniond worldRot = resolveWorldRotation(blockEntity, facing);

        renderSphere(canvas, worldRot);
        renderBezel(canvas, cx, cy);
        renderProgradeMarker(canvas, cx, cy);
        renderReticle(canvas, cx, cy);
    }

    private Direction resolveBlockFacing(MFDBlockEntity blockEntity) {
        if (blockEntity == null) return Direction.NORTH;
        BlockState state = blockEntity.getBlockState();
        return state.hasProperty(MFDBlock.FACING) ? state.getValue(MFDBlock.FACING) : Direction.NORTH;
    }

    private Quaterniond resolveWorldRotation(MFDBlockEntity blockEntity, Direction facing) {
        Quaterniond blockFacingRot = getBlockFacingRotation(facing);
        Quaterniond worldRot = new Quaterniond();

        if (blockEntity == null || blockEntity.getLevel() == null) {
            return worldRot.set(blockFacingRot);
        }

        ClientSubLevel clientSubLevel = Sable.HELPER.getContainingClient(blockEntity);
        if (clientSubLevel != null) {
            Pose3dc renderPose = clientSubLevel.renderPose();
            Quaterniondc shipRot = renderPose.orientation();
            worldRot.set(shipRot).mul(blockFacingRot);
        } else {
            worldRot.set(blockFacingRot);
        }
        return worldRot;
    }

    private Quaterniond getBlockFacingRotation(Direction facing) {
        return switch (facing) {
            case NORTH -> new Quaterniond().rotateY(Math.PI);
            case SOUTH -> new Quaterniond();
            case WEST  -> new Quaterniond().rotateY(-Math.PI / 2);
            case EAST  -> new Quaterniond().rotateY(Math.PI / 2);
            case UP    -> new Quaterniond().rotateX(-Math.PI / 2);
            case DOWN  -> new Quaterniond().rotateX(Math.PI / 2);
        };
    }

    private void renderSphere(MFDCanvas canvas, Quaterniond worldRot) {
        int[] pixels = canvas.getPixels();
        Vector3d camRay = new Vector3d();
        Vector3d worldRay = new Vector3d();

        for (int i = 0; i < lut.count; i++) {
            camRay.set(lut.nx[i], lut.ny[i], lut.nz[i]);
            worldRot.transform(camRay, worldRay);

            double lat = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, worldRay.y))));
            double lon = (Math.toDegrees(Math.atan2(worldRay.x, worldRay.z)) + 360.0) % 360.0;

            int px = lut.pixX[i];
            int py = lut.pixY[i];
            int bayer = BAYER4[py & 3][px & 3];

            int color = computeN64Pixel(lat, lon, bayer, lut.nz[i]);
            pixels[lut.pixelOffsets[i]] = color;
        }
        canvas.markDirty();
    }

    private int computeN64Pixel(double lat, double lon, int bayer, float nz) {
        double absLat = Math.abs(lat);

        if (isCardinalNorth(lon)) return NORTH_COLOR;
        if (isCardinalSouth(lon)) return SOUTH_COLOR;
        if (isCardinalEast(lon))  return EAST_COLOR;
        if (isCardinalWest(lon))  return WEST_COLOR;

        if (absLat < 2.0) return HORIZON_COLOR;

        if (isMajorLatLine(absLat)) return GRID_MAJOR;
        if (isMajorLonLine(lon))    return GRID_MAJOR;
        if (isMinorLatLine(absLat)) return GRID_MINOR;
        if (isMinorLonLine(lon))    return GRID_MINOR;

        int latBand = Math.min(4, (int)((absLat - 2.0) / 17.5));
        int[] bandColor = (lat >= 0) ? SKY_BANDS[latBand] : GND_BANDS[latBand];

        int shadeLevel = computeShadeLevel(nz, bayer);

        return applyFlatShade(bandColor, shadeLevel);
    }

    private boolean isCardinalNorth(double lon) { return lon < 3.5 || lon > 356.5; }
    private boolean isCardinalSouth(double lon) { return Math.abs(lon - 180.0) < 3.5; }
    private boolean isCardinalEast(double lon)  { return Math.abs(lon -  90.0) < 3.0; }
    private boolean isCardinalWest(double lon)  { return Math.abs(lon - 270.0) < 3.0; }

    private boolean isMajorLatLine(double absLat) {
        double mod = absLat % 30.0;
        return absLat > 4.0 && mod < 1.8;
    }

    private boolean isMinorLatLine(double absLat) {
        double mod = absLat % 10.0;
        return absLat > 4.0 && mod < 1.2;
    }

    private boolean isMajorLonLine(double lon) {
        double mod = lon % 90.0;
        return mod < 1.8 || mod > 88.2;
    }

    private boolean isMinorLonLine(double lon) {
        double mod = lon % 45.0;
        return mod < 1.2 || mod > 43.8;
    }

    private int computeShadeLevel(float nz, int bayer) {
        double shade = 0.3 + 0.7 * nz;
        double dithered = shade + (bayer - 7.5) / 64.0;
        int level = (int)(dithered * 4.0);
        return Math.max(0, Math.min(3, level));
    }

    private int applyFlatShade(int[] rgb, int shadeLevel) {
        int mult = SHADE_MULT[shadeLevel];
        int r = n64q(rgb[0] * mult / 255);
        int g = n64q(rgb[1] * mult / 255);
        int b = n64q(rgb[2] * mult / 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void renderBezel(MFDCanvas canvas, int cx, int cy) {
        canvas.drawCircle(cx, cy, RADIUS + 4, BEZEL_OUTER, false);
        canvas.drawCircle(cx, cy, RADIUS + 3, BEZEL_OUTER, false);
        canvas.drawCircle(cx, cy, RADIUS + 2, BEZEL_INNER, false);
        canvas.drawCircle(cx, cy, RADIUS + 1, BEZEL_RIM,   false);

        for (int deg = 0; deg < 360; deg += 45) {
            double rad = Math.toRadians(deg);
            int tickColor = (deg % 90 == 0) ? RETICLE_CYN : BEZEL_RIM;
            int r0 = RADIUS + 2;
            int r1 = RADIUS + (deg % 90 == 0 ? 5 : 4);
            for (int r = r0; r <= r1; r++) {
                int tx = cx + (int) Math.round(Math.cos(rad) * r);
                int ty = cy + (int) Math.round(Math.sin(rad) * r);
                canvas.setPixel(tx, ty, tickColor);
            }
        }
    }

    private void renderProgradeMarker(MFDCanvas canvas, int cx, int cy) {
        int topY = cy - RADIUS - 1;
        canvas.setPixel(cx,      topY - 2, 0xFFFFDD00);
        canvas.setPixel(cx - 1,  topY - 2, 0xFF886600);
        canvas.setPixel(cx + 1,  topY - 2, 0xFF886600);
        canvas.setPixel(cx,      topY - 1, 0xFFFFEE44);
    }

    private void renderReticle(MFDCanvas canvas, int cx, int cy) {
        canvas.setPixel(cx, cy, CENTER_RED);

        canvas.setPixel(cx - 1, cy,     RETICLE_CYN);
        canvas.setPixel(cx + 1, cy,     RETICLE_CYN);
        canvas.setPixel(cx,     cy - 1, RETICLE_CYN);
        canvas.setPixel(cx,     cy + 1, RETICLE_CYN);

        canvas.drawHLine(cx - 13, cx - 5, cy,     RETICLE_CYN);
        canvas.drawHLine(cx - 13, cx - 5, cy + 1, RETICLE_DRK);
        canvas.drawHLine(cx + 5,  cx + 13, cy,    RETICLE_CYN);
        canvas.drawHLine(cx + 5,  cx + 13, cy + 1, RETICLE_DRK);

        canvas.setPixel(cx - 13, cy - 1, RETICLE_CYN);
        canvas.setPixel(cx + 13, cy - 1, RETICLE_CYN);

        canvas.drawLine(cx - 4, cy + 2, cx,     cy + 7, RETICLE_CYN);
        canvas.drawLine(cx + 4, cy + 2, cx,     cy + 7, RETICLE_CYN);
        canvas.setPixel(cx, cy + 7, RETICLE_DRK);
    }

    private static int[] n64rgb(int r, int g, int b) {
        return new int[]{n64q(r), n64q(g), n64q(b)};
    }

    private static int n64q(int v) {
        int clamped = Math.max(0, Math.min(255, v));
        return (clamped >> 3) << 3;
    }

    private static int[] n64(int r, int g, int b) {
        return n64rgb(r, g, b);
    }
}
