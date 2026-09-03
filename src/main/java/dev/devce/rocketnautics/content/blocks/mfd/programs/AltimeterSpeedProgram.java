package dev.devce.rocketnautics.content.blocks.mfd.programs;

import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.blocks.mfd.MFDBlockEntity;
import dev.devce.rocketnautics.content.blocks.mfd.MFDCanvas;
import dev.devce.rocketnautics.content.blocks.mfd.MFDProgram;
import dev.devce.rocketnautics.content.orbit.universe.CubePlanet;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.joml.Vector3dc;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.Locale;

public class AltimeterSpeedProgram implements MFDProgram {

    private static final int BG_COLOR       = 0xFF05080E;
    private static final int DIAL_BG        = 0xFF080E18;
    private static final int DIAL_BORDER    = 0xFF14283C;
    private static final int DIAL_OUTER     = 0xFF0C1824;
    private static final int CYAN_BRIGHT    = 0xFF00FFCC;
    private static final int CYAN_MUTED     = 0xFF008877;
    private static final int GREEN_BRIGHT   = 0xFF00FF88;
    private static final int GREEN_MUTED    = 0xFF008844;
    private static final int GOLD           = 0xFFFFCC00;
    private static final int RED            = 0xFFFF3333;
    private static final int BOX_BG         = 0xFF040810;
    private static final int BOX_BORDER     = 0xFF142436;
    private static final int TEXT_COLOR     = 0xFFFFFFFF;
    private static final int FRAME_BORDER   = 0xFF0D1824;

    private static final int DIAL_RADIUS = 13;
    private static final int LEFT_CX     = 17;
    private static final int RIGHT_CX    = 47;
    private static final int DIAL_CY     = 27;

    private double lastSubLevelX = Double.NaN;
    private double lastSubLevelY = Double.NaN;
    private double lastSubLevelZ = Double.NaN;
    private long lastTimeMs = 0;
    private double atmosphericSpeed = 0;
    private double atmosphericVsi = 0;

    private record FlightData(double speed, double altitude, double vsi, boolean isOrbital) {}

    @Override
    public String getName() {
        return "ALT/SPD";
    }

    @Override
    public void render(MFDCanvas canvas, MFDBlockEntity blockEntity, float partialTicks) {
        FlightData data = resolveFlightData(blockEntity);

        canvas.clear(BG_COLOR);
        renderBezelFrame(canvas);

        renderSpeedGauge(canvas, LEFT_CX, DIAL_CY, data.speed, data.isOrbital);
        renderAltitudeGauge(canvas, RIGHT_CX, DIAL_CY, data.altitude, data.isOrbital);

        renderCenterVsiAndMode(canvas, data.vsi, data.isOrbital);
    }

    private FlightData resolveFlightData(MFDBlockEntity blockEntity) {
        if (DeepSpaceHandler.getUniverse() != null && DeepSpaceHandler.hasReceivedPosition()) {
            DeepSpacePosition dsp = DeepSpaceHandler.getReceivedPosition();
            KeplerianOrbit orbit = dsp.getCurrentOrbit();
            if (orbit != null) {
                TimeStampedPVCoordinates pv = orbit.getPVCoordinates();
                Vector3D pos = pv.getPosition();
                Vector3D vel = pv.getVelocity();

                double speed = vel.getNorm();
                double r = pos.getNorm();
                double planetRadius = 0;

                UniverseDefinition universe = DeepSpaceHandler.getUniverse();
                if (universe != null) {
                    Frame frame = orbit.getFrame();
                    for (CubePlanet planet : universe.getPlanets()) {
                        if (planet.orekitFrame().equals(frame)) {
                            planetRadius = planet.radius();
                            break;
                        }
                    }
                }

                double altitude = (planetRadius > 0 && r >= planetRadius) ? (r - planetRadius) : r;
                double vsi = (r > 0.001) ? vel.dotProduct(pos.normalize()) : 0.0;

                return new FlightData(speed, altitude, vsi, true);
            }
        }

        double alt = 0.0;
        long now = System.currentTimeMillis();

        if (blockEntity != null && blockEntity.getLevel() != null) {
            ClientSubLevel clientSubLevel = Sable.HELPER.getContainingClient(blockEntity);
            if (clientSubLevel != null) {
                Vector3dc p = clientSubLevel.logicalPose().position();
                alt = p.y();

                if (!Double.isNaN(lastSubLevelX) && lastTimeMs > 0 && now > lastTimeMs) {
                    double dt = (now - lastTimeMs) / 1000.0;
                    if (dt > 0.001 && dt < 1.0) {
                        double dx = p.x() - lastSubLevelX;
                        double dy = p.y() - lastSubLevelY;
                        double dz = p.z() - lastSubLevelZ;
                        double currentSpeed = Math.sqrt(dx * dx + dy * dy + dz * dz) / dt;
                        double currentVsi = dy / dt;
                        atmosphericSpeed = atmosphericSpeed * 0.8 + currentSpeed * 0.2;
                        atmosphericVsi = atmosphericVsi * 0.8 + currentVsi * 0.2;
                    }
                }

                lastSubLevelX = p.x();
                lastSubLevelY = p.y();
                lastSubLevelZ = p.z();
                lastTimeMs = now;
            } else {
                alt = blockEntity.getBlockPos().getY();
            }
        }

        return new FlightData(atmosphericSpeed, alt, atmosphericVsi, false);
    }

    private void renderSpeedGauge(MFDCanvas canvas, int cx, int cy, double speed, boolean isOrbital) {
        canvas.drawString("SPD", cx - 6, 4, CYAN_BRIGHT);

        renderDialFace(canvas, cx, cy, CYAN_BRIGHT, RED);

        double maxSpeed = isOrbital ? 12000.0 : 300.0;
        double frac = Math.min(1.0, Math.max(0.0, speed / maxSpeed));
        renderNeedle(canvas, cx, cy, frac);

        int boxX = cx - 13;
        int boxY = cy + 15;
        int boxW = 26;
        int boxH = 9;
        canvas.fillRect(boxX, boxY, boxW, boxH, BOX_BG);
        canvas.drawRect(boxX, boxY, boxW, boxH, BOX_BORDER);

        String text = formatMetric(speed);
        int textX = boxX + (boxW - text.length() * 4 + 1) / 2;
        canvas.drawString(text, textX, boxY + 2, TEXT_COLOR);

        String unit = speed >= 1000.0 ? "KM/S" : "M/S";
        int unitX = cx - (unit.length() * 4 - 1) / 2;
        canvas.drawString(unit, unitX, boxY + 11, CYAN_MUTED);
    }

    private void renderAltitudeGauge(MFDCanvas canvas, int cx, int cy, double altitude, boolean isOrbital) {
        canvas.drawString("ALT", cx - 6, 4, GREEN_BRIGHT);

        renderDialFace(canvas, cx, cy, GREEN_BRIGHT, GOLD);

        double maxAlt = isOrbital ? 2000000.0 : 2500.0;
        double frac = Math.min(1.0, Math.max(0.0, altitude / maxAlt));
        renderNeedle(canvas, cx, cy, frac);

        int boxX = cx - 13;
        int boxY = cy + 15;
        int boxW = 26;
        int boxH = 9;
        canvas.fillRect(boxX, boxY, boxW, boxH, BOX_BG);
        canvas.drawRect(boxX, boxY, boxW, boxH, BOX_BORDER);

        String text = formatMetric(altitude);
        int textX = boxX + (boxW - text.length() * 4 + 1) / 2;
        canvas.drawString(text, textX, boxY + 2, TEXT_COLOR);

        String unit = altitude >= 10000.0 ? "KM" : "M";
        int unitX = cx - (unit.length() * 4 - 1) / 2;
        canvas.drawString(unit, unitX, boxY + 11, GREEN_MUTED);
    }

    private void renderDialFace(MFDCanvas canvas, int cx, int cy, int arcColor, int arcWarnColor) {
        canvas.drawCircle(cx, cy, DIAL_RADIUS, DIAL_BG, true);
        canvas.drawCircle(cx, cy, DIAL_RADIUS, DIAL_BORDER, false);
        canvas.drawCircle(cx, cy, DIAL_RADIUS + 1, DIAL_OUTER, false);

        for (int deg = 135; deg <= 405; deg += 6) {
            double rad = Math.toRadians(deg);
            int ax = cx + (int) Math.round(Math.cos(rad) * (DIAL_RADIUS - 1));
            int ay = cy + (int) Math.round(Math.sin(rad) * (DIAL_RADIUS - 1));
            int col = (deg >= 365) ? arcWarnColor : arcColor;
            canvas.setPixelFast(ax, ay, col);
        }

        for (int i = 0; i <= 4; i++) {
            double deg = 135.0 + i * (270.0 / 4.0);
            double rad = Math.toRadians(deg);
            int tx = cx + (int) Math.round(Math.cos(rad) * (DIAL_RADIUS - 3));
            int ty = cy + (int) Math.round(Math.sin(rad) * (DIAL_RADIUS - 3));
            canvas.setPixelFast(tx, ty, TEXT_COLOR);
        }
    }

    private void renderNeedle(MFDCanvas canvas, int cx, int cy, double fraction) {
        double deg = 135.0 + fraction * 270.0;
        double rad = Math.toRadians(deg);

        int tipX = cx + (int) Math.round(Math.cos(rad) * (DIAL_RADIUS - 2));
        int tipY = cy + (int) Math.round(Math.sin(rad) * (DIAL_RADIUS - 2));

        canvas.drawLine(cx, cy, tipX, tipY, GOLD);
        canvas.setPixelFast(tipX, tipY, RED);

        canvas.drawCircle(cx, cy, 1, GOLD, true);
        canvas.setPixelFast(cx, cy, TEXT_COLOR);
    }

    private void renderCenterVsiAndMode(MFDCanvas canvas, double vsi, boolean isOrbital) {
        int centerX = 32;

        if (isOrbital) {
            canvas.drawString("ORB", centerX - 5, 4, GOLD);
        } else {
            canvas.drawString("ATM", centerX - 5, 4, CYAN_MUTED);
        }

        canvas.drawVLine(centerX, 12, 44, BOX_BORDER);
        canvas.drawHLine(centerX - 2, centerX + 2, DIAL_CY, GOLD);

        if (vsi > 1.5) {
            canvas.setPixelFast(centerX, DIAL_CY - 10, CYAN_BRIGHT);
            canvas.drawHLine(centerX - 1, centerX + 1, DIAL_CY - 9, CYAN_BRIGHT);
            canvas.drawHLine(centerX - 2, centerX + 2, DIAL_CY - 8, CYAN_BRIGHT);
        } else if (vsi < -1.5) {
            canvas.drawHLine(centerX - 2, centerX + 2, DIAL_CY + 8, RED);
            canvas.drawHLine(centerX - 1, centerX + 1, DIAL_CY + 9, RED);
            canvas.setPixelFast(centerX, DIAL_CY + 10, RED);
        }
    }

    private void renderBezelFrame(MFDCanvas canvas) {
        canvas.drawRect(0, 0, canvas.width, canvas.height, FRAME_BORDER);
    }

    private String formatMetric(double val) {
        double abs = Math.abs(val);
        if (abs < 1000.0) {
            return String.valueOf((int) Math.round(val));
        }
        if (abs < 100000.0) {
            return String.format(Locale.ROOT, "%.1fK", val / 1000.0);
        }
        if (abs < 10000000.0) {
            return (int) (val / 1000.0) + "K";
        }
        return String.format(Locale.ROOT, "%.1fM", val / 1000000.0);
    }
}
