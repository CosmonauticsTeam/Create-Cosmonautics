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

public class AttitudeIndicatorProgram implements MFDProgram {

    private static final int RADIUS = 26;
    private static final double PITCH_SCALE = 0.7;

    private static final int[][] BAYER4 = {
        { 0,  8,  2, 10},
        {12,  4, 14,  6},
        { 3, 11,  1,  9},
        {15,  7, 13,  5}
    };

    private static final int[][] SKY_BANDS = {
        n64(12,  56, 184),
        n64( 0,  40, 152),
        n64( 0,  24, 120),
        n64( 0,  12,  88),
        n64( 0,   4,  56)
    };

    private static final int[][] GND_BANDS = {
        n64(184,  88,  24),
        n64(152,  64,  16),
        n64(120,  44,   8),
        n64( 88,  28,   0),
        n64( 56,  16,   0)
    };

    private static final int COLOR_BG          = 0xFF060810;
    private static final int COLOR_HORIZON     = 0xFFFFDD00;
    private static final int COLOR_HORIZON_SHD = 0xFF000000;
    private static final int COLOR_PITCH_SKY   = 0xFFE0F4FF;
    private static final int COLOR_PITCH_GND   = 0xFFFFCC88;
    private static final int COLOR_BEZEL_OUT   = 0xFF0A1828;
    private static final int COLOR_BEZEL_MID   = 0xFF1A3850;
    private static final int COLOR_BEZEL_RIM   = 0xFF224466;
    private static final int COLOR_CYAN        = 0xFF00FFCC;
    private static final int COLOR_RED         = 0xFFFF2244;
    private static final int COLOR_GOLD        = 0xFFFFCC00;
    private static final int COLOR_SHADOW      = 0xFF000000;

    @Override
    public String getName() {
        return "HORIZON";
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        int cx = canvas.width / 2;
        int cy = canvas.height / 2;

        canvas.clear(COLOR_BG);

        Direction facing = resolveBlockFacing(blockEntity);
        Quaterniond worldRot = resolveWorldRotation(blockEntity, facing);

        Vector3d forward = new Vector3d(0, 0, 1);
        worldRot.transform(forward);
        double pitch = Math.toDegrees(Math.asin(Math.max(-1.0, Math.min(1.0, forward.y))));

        Vector3d camUp = new Vector3d(0, 1, 0);
        worldRot.transformInverse(camUp);
        double roll = Math.atan2(camUp.x, camUp.y);

        renderHorizonField(canvas, cx, cy, pitch, roll);
        renderPitchLadder(canvas, cx, cy, pitch, roll);
        renderBezelAndCornerDecor(canvas, cx, cy);
        renderRollScale(canvas, cx, cy, roll);
        renderAircraftSymbol(canvas, cx, cy);
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

    private void renderHorizonField(MFDCanvas canvas, int cx, int cy, double pitch, double roll) {
        int[] pixels = canvas.getPixels();
        int width = canvas.width;
        int r2 = RADIUS * RADIUS;

        double normalX = -Math.sin(roll);
        double normalY = Math.cos(roll);
        double pitchOffset = pitch * PITCH_SCALE;

        for (int dy = -RADIUS; dy <= RADIUS; dy++) {
            int py = cy + dy;
            int maxDx = (int) Math.sqrt(r2 - dy * dy);
            int rowOffset = py * width;
            int screenY = -dy;

            for (int dx = -maxDx; dx <= maxDx; dx++) {
                int px = cx + dx;
                double dist = dx * normalX + screenY * normalY - pitchOffset;

                int bayer = BAYER4[py & 3][px & 3];
                int color = computeSkyGroundPixel(dist, bayer);
                pixels[rowOffset + px] = color;
            }
        }
        canvas.markDirty();
    }

    private int computeSkyGroundPixel(double dist, int bayer) {
        double absDist = Math.abs(dist);

        if (absDist <= 0.85) {
            return COLOR_HORIZON;
        }
        if (absDist <= 1.45) {
            return COLOR_HORIZON_SHD;
        }

        double angle = absDist / PITCH_SCALE;
        int band = Math.min(4, (int) (angle / 14.0));
        int[] rgb = (dist > 0) ? SKY_BANDS[band] : GND_BANDS[band];

        double ditherFactor = (bayer - 7.5) / 64.0;
        int r = n64q((int) (rgb[0] * (1.0 + ditherFactor)));
        int g = n64q((int) (rgb[1] * (1.0 + ditherFactor)));
        int b = n64q((int) (rgb[2] * (1.0 + ditherFactor)));

        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private void renderPitchLadder(MFDCanvas canvas, int cx, int cy, double pitch, double roll) {
        double normalX = -Math.sin(roll);
        double normalY = Math.cos(roll);
        double tanX = normalY;
        double tanY = -normalX;

        int[] rungs = {-60, -50, -40, -30, -20, -10, 10, 20, 30, 40, 50, 60};

        for (int rungDeg : rungs) {
            double distFromCenter = (rungDeg - pitch) * PITCH_SCALE;
            if (Math.abs(distFromCenter) > RADIUS - 4) continue;

            double rungCenterX = cx + normalX * distFromCenter;
            double rungCenterY = cy - normalY * distFromCenter;

            boolean major = (Math.abs(rungDeg) % 20 == 0);
            int halfLen = major ? 7 : 4;
            int color = (rungDeg > 0) ? COLOR_PITCH_SKY : COLOR_PITCH_GND;

            for (int t = -halfLen; t <= halfLen; t++) {
                if (rungDeg < 0 && (Math.abs(t) % 2 == 1)) continue;

                int px = (int) Math.round(rungCenterX + t * tanX);
                int py = (int) Math.round(rungCenterY + t * tanY);

                if (isInsideInstrument(px, py, cx, cy, RADIUS - 2)) {
                    canvas.setPixelFast(px, py, color);
                }
            }

            int tickDir = (rungDeg > 0) ? -1 : 1;
            int tickLen = major ? 3 : 2;

            for (int endSign : new int[]{-1, 1}) {
                double endX = rungCenterX + endSign * halfLen * tanX;
                double endY = rungCenterY + endSign * halfLen * tanY;

                for (int step = 1; step <= tickLen; step++) {
                    int tx = (int) Math.round(endX + tickDir * step * normalX);
                    int ty = (int) Math.round(endY - tickDir * step * normalY);
                    if (isInsideInstrument(tx, ty, cx, cy, RADIUS - 2)) {
                        canvas.setPixelFast(tx, ty, color);
                    }
                }
            }
        }
    }

    private void renderRollScale(MFDCanvas canvas, int cx, int cy, double roll) {
        int[] angles = {-60, -45, -30, -20, -10, 0, 10, 20, 30, 45, 60};

        for (int angleDeg : angles) {
            double rad = Math.toRadians(angleDeg - 90.0);
            boolean major = (Math.abs(angleDeg) == 0 || Math.abs(angleDeg) == 30 || Math.abs(angleDeg) == 60);
            int len = major ? 3 : 2;
            int col = (angleDeg == 0) ? COLOR_GOLD : (major ? COLOR_CYAN : COLOR_BEZEL_RIM);

            for (int r = RADIUS + 1; r <= RADIUS + len; r++) {
                int tx = cx + (int) Math.round(Math.cos(rad) * r);
                int ty = cy + (int) Math.round(Math.sin(rad) * r);
                canvas.setPixel(tx, ty, col);
            }
        }

        double pointerRad = -roll - Math.PI / 2.0;
        int pTipX = cx + (int) Math.round(Math.cos(pointerRad) * (RADIUS - 1));
        int pTipY = cy + (int) Math.round(Math.sin(pointerRad) * (RADIUS - 1));
        int pBaseX = cx + (int) Math.round(Math.cos(pointerRad) * (RADIUS - 4));
        int pBaseY = cy + (int) Math.round(Math.sin(pointerRad) * (RADIUS - 4));

        canvas.setPixel(pTipX, pTipY, COLOR_GOLD);
        canvas.setPixel(pBaseX, pBaseY, COLOR_GOLD);
        double perpX = -Math.sin(pointerRad);
        double perpY = Math.cos(pointerRad);
        canvas.setPixel((int) Math.round(pBaseX + perpX), (int) Math.round(pBaseY + perpY), COLOR_GOLD);
        canvas.setPixel((int) Math.round(pBaseX - perpX), (int) Math.round(pBaseY - perpY), COLOR_GOLD);
    }

    private void renderBezelAndCornerDecor(MFDCanvas canvas, int cx, int cy) {
        canvas.drawCircle(cx, cy, RADIUS + 4, COLOR_BEZEL_OUT, false);
        canvas.drawCircle(cx, cy, RADIUS + 3, COLOR_BEZEL_OUT, false);
        canvas.drawCircle(cx, cy, RADIUS + 2, COLOR_BEZEL_MID, false);
        canvas.drawCircle(cx, cy, RADIUS + 1, COLOR_BEZEL_RIM, false);

        int edge = 3;
        int len = 5;

        canvas.drawHLine(edge, edge + len, edge, COLOR_BEZEL_RIM);
        canvas.drawVLine(edge, edge, edge + len, COLOR_BEZEL_RIM);

        canvas.drawHLine(canvas.width - edge - len - 1, canvas.width - edge - 1, edge, COLOR_BEZEL_RIM);
        canvas.drawVLine(canvas.width - edge - 1, edge, edge + len, COLOR_BEZEL_RIM);

        canvas.drawHLine(edge, edge + len, canvas.height - edge - 1, COLOR_BEZEL_RIM);
        canvas.drawVLine(edge, canvas.height - edge - len - 1, canvas.height - edge - 1, COLOR_BEZEL_RIM);

        canvas.drawHLine(canvas.width - edge - len - 1, canvas.width - edge - 1, canvas.height - edge - 1, COLOR_BEZEL_RIM);
        canvas.drawVLine(canvas.width - edge - 1, canvas.height - edge - len - 1, canvas.height - edge - 1, COLOR_BEZEL_RIM);
    }

    private void renderAircraftSymbol(MFDCanvas canvas, int cx, int cy) {
        canvas.setPixel(cx, cy, COLOR_RED);
        canvas.setPixel(cx - 1, cy, COLOR_GOLD);
        canvas.setPixel(cx + 1, cy, COLOR_GOLD);
        canvas.setPixel(cx, cy - 1, COLOR_GOLD);
        canvas.setPixel(cx, cy + 1, COLOR_GOLD);

        canvas.drawHLine(cx - 12, cx - 4, cy, COLOR_GOLD);
        canvas.drawHLine(cx - 12, cx - 4, cy + 1, COLOR_SHADOW);

        canvas.setPixel(cx - 4, cy + 1, COLOR_GOLD);
        canvas.setPixel(cx - 4, cy + 2, COLOR_GOLD);
        canvas.setPixel(cx - 4, cy + 3, COLOR_SHADOW);

        canvas.drawHLine(cx + 4, cx + 12, cy, COLOR_GOLD);
        canvas.drawHLine(cx + 4, cx + 12, cy + 1, COLOR_SHADOW);

        canvas.setPixel(cx + 4, cy + 1, COLOR_GOLD);
        canvas.setPixel(cx + 4, cy + 2, COLOR_GOLD);
        canvas.setPixel(cx + 4, cy + 3, COLOR_SHADOW);

        canvas.setPixel(cx - 12, cy - 1, COLOR_GOLD);
        canvas.setPixel(cx + 12, cy - 1, COLOR_GOLD);
    }

    private boolean isInsideInstrument(int px, int py, int cx, int cy, int r) {
        int dx = px - cx;
        int dy = py - cy;
        return dx * dx + dy * dy <= r * r;
    }

    private static int[] n64(int r, int g, int b) {
        return new int[]{n64q(r), n64q(g), n64q(b)};
    }

    private static int n64q(int v) {
        int clamped = Math.max(0, Math.min(255, v));
        return (clamped >> 3) << 3;
    }
}
