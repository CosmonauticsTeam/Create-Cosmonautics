package dev.devce.rocketnautics.client.render.spaceRenderer;

import dev.devce.rocketnautics.RocketConfig;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import it.unimi.dsi.fastutil.Pair;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.util.ArrayListDeque;
import net.minecraft.util.Mth;
import org.hipparchus.geometry.euclidean.threed.Vector3D;
import org.jetbrains.annotations.Nullable;
import org.orekit.frames.Frame;
import org.orekit.orbits.KeplerianOrbit;
import org.orekit.orbits.Orbit;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedPVCoordinates;

import java.util.Collections;
import java.util.Iterator;
import java.util.stream.Stream;

public final class UniverseHelper {
    public static AbsoluteDate getRenderDate(float partial) {
        return receivedPosition
                .getLocalUniverseTime()
                .shiftedBy(receivedPosition.getTimescale() * ((double) partial + (getLocalMinecraftTicks() - receivedPositionTick)) / 20);
    }

    public static int getLocalMinecraftTicks() { return Minecraft.getInstance().levelRenderer.getTicks();}

    public static @Nullable UniverseDefinition UNIVERSE;

    public static long receivedPositionTick = -1;
    public static final DeepSpacePosition receivedPosition = new DeepSpacePosition();

    private static long receivedUniverseDateTick = -1;
    private static AbsoluteDate receivedUniverseDate;
    private static float receivedUniverseTickrate;
    private static final ArrayListDeque<Pair<AbsoluteDate, Orbit>> positionPredictions = new ArrayListDeque<>(100);
    private static final DeepSpacePosition nextPrediction = new DeepSpacePosition();

    public static void receiveUniverse(UniverseDefinition definition) {
        UNIVERSE = definition;
        receivedPosition.reset();
        nextPrediction.reset();
        receivedPositionTick = -1;
        receivedUniverseDateTick = -1;
    }

    public static void receiveUniverseTime(long universeTicks, float serverTickRate) {
        receivedUniverseDateTick = getLocalMinecraftTicks();
        receivedUniverseDate = DeepSpaceHelper.getDateByTicks(universeTicks);
        receivedUniverseTickrate = serverTickRate;
    }

    public static boolean hasReceivedPosition() {
        return receivedPositionTick != -1;
    }

    public static void receivePosition(FriendlyByteBuf buf) {
        if (UNIVERSE != null) {
            receivedPositionTick = getLocalMinecraftTicks();
            receivedPosition.read(buf, UNIVERSE);
            positionPredictions.clear();
            receivedPosition.copyTo(nextPrediction);
        }
    }

    public static @Nullable AbsoluteDate getPredictedUniverseDate(float partial) {
        if (receivedUniverseDateTick == -1) return null;
        return new AbsoluteDate(receivedUniverseDate, (getLocalMinecraftTicks() - receivedUniverseDateTick + partial) * receivedUniverseTickrate / 400f);
    }

    public static Iterator<Vector3D> getPositionPrediction(Frame frame, int upTo) {
        if (UNIVERSE == null) return Collections.emptyIterator();
        AbsoluteDate renderDate = getRenderDate(0);
        return new Iterator<>() {
            int index = 0;

            @Override
            public boolean hasNext() {
                return index < upTo && index < 10000;
            }

            @Override
            public Vector3D next() {
                while (positionPredictions.size() <= index) {
                    KeplerianOrbit orbit = nextPrediction.getCurrentOrbit();
                    TimeStampedPVCoordinates coords = orbit.getPVCoordinates();
                    if (coords.getDate().isAfterOrEqualTo(renderDate)) {
                        positionPredictions.addLast(Pair.of(coords.getDate(), nextPrediction.getOrbit()));
                    }
                    double correctedAngularVelocity = orbit.getEccentricAnomalyDot() / Mth.lerp(orbit.getE() * orbit.getE(), 1, 1 - coords.getVelocity().normalize().crossProduct(coords.getAcceleration().normalize()).getNorm() / 2);
                    int lookaheadTicks = (int) (20 * Math.toRadians(RocketConfig.CLIENT.orbitPredictionAngularThreshold.get()) / correctedAngularVelocity);
                    nextPrediction.setTimescale(Math.max(1, lookaheadTicks));
                    nextPrediction.propagate(UNIVERSE);
                }
                Pair<AbsoluteDate, Orbit> pair = positionPredictions.get(index);
                index++;
                return pair.right().getPosition(pair.left(), frame);
            }
        };
    }

    public static Stream<AbsoluteDate> getPredictionDates(int maximum) {
        return positionPredictions.stream().map(Pair::left).limit(maximum);
    }

    public static Iterator<Orbit> getPredictionOrbits() {
        return new Iterator<>() {
            int index = 0;
            Orbit previous = null;
            Orbit foundNext = null;

            private void ensureNext() {
                while (foundNext == null && index < positionPredictions.size()) {
                    Orbit find = positionPredictions.get(index).right();
                    if (find != previous) {
                        foundNext = find;
                        previous = find;
                    }
                    index++;
                }
            }

            @Override
            public boolean hasNext() {
                ensureNext();
                return foundNext != null;
            }

            @Override
            public Orbit next() {
                ensureNext();
                Orbit ret = foundNext;
                foundNext = null;
                return ret;
            }
        };
    }
}
