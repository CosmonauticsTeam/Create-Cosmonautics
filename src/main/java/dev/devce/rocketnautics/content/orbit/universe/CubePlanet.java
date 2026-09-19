package dev.devce.rocketnautics.content.orbit.universe;

import dev.devce.rocketnautics.SkyDataHandler;
import dev.devce.rocketnautics.api.orbit.ColorPalette;
import dev.devce.rocketnautics.api.orbit.DeepSpaceHelper;
import dev.devce.rocketnautics.api.orbit.FrameTree;
import dev.devce.rocketnautics.api.orbit.FrameTreeOwner;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.hipparchus.geometry.euclidean.threed.Rotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orekit.time.AbsoluteDate;
import org.orekit.utils.TimeStampedAngularCoordinates;

// note -- texture definition is never synced to client
public record CubePlanet(@NotNull FrameTree frame, double radius, TimeStampedAngularCoordinates rotationDescription,
                         @Nullable PlanetDimensionData linkedDimension, @Nullable DeepSpaceTextureDefinition textureDefinition,
                         @NotNull PlanetExtras extras, @Nullable PlanetAtmosphere atmosphere, @Nullable StarProperties starProperties) implements FrameTreeOwner {

    public ColorPalette getRenderData(MinecraftServer server, int powerScaleClamp) {
        if (linkedDimension == null) return ColorPalette.EMPTY;
        ServerLevel level = server.getLevel(linkedDimension.key());
        if (level == null) return ColorPalette.EMPTY;
        return SkyDataHandler.getHandlerForLevel(level).getRenderDataForDeepSpace(powerScaleClamp);
    }

    public Rotation getRotationAtTime(AbsoluteDate date) {
        return rotationDescription.rotationShiftedBy(date.durationFrom(rotationDescription.getDate()));
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(frame.getId());
        buf.writeDouble(radius);
        DeepSpaceHelper.STAMPED_ANGULARCOORDS_CODEC_S.encode(buf, rotationDescription);
        buf.writeBoolean(linkedDimension != null);
        if (linkedDimension != null) {
            linkedDimension.write(buf);
        }
        PlanetExtras.CODEC.encode(buf, extras);

        buf.writeBoolean(atmosphere != null);
        if (atmosphere != null) {
            PlanetAtmosphere.CODEC.encode(buf, atmosphere);
        }

        buf.writeBoolean(starProperties != null);
        if (starProperties != null) {
            StarProperties.CODEC.encode(buf, starProperties);
        }
    }

    public static CubePlanet read(FriendlyByteBuf buf, FrameTree frameSource) {
        int id = buf.readVarInt();
        double radius = buf.readDouble();
        TimeStampedAngularCoordinates coords = DeepSpaceHelper.STAMPED_ANGULARCOORDS_CODEC_S.decode(buf);
        boolean hasLinked = buf.readBoolean();
        PlanetDimensionData linkedDimension = null;
        if (hasLinked) {
            linkedDimension = PlanetDimensionData.read(buf);
        }
        PlanetExtras extras = PlanetExtras.CODEC.decode(buf);

        boolean hasAtmosphere = buf.readBoolean();
        PlanetAtmosphere atmosphere = null;
        if (hasAtmosphere) {
            atmosphere = PlanetAtmosphere.CODEC.decode(buf);
        }

        boolean isStar = buf.readBoolean();
        StarProperties starProperties = null;
        if (isStar) {
            starProperties = StarProperties.CODEC.decode(buf);
        }

        return new CubePlanet(frameSource.getInTreeByID(id).get(), radius, coords, linkedDimension, null, extras, atmosphere, starProperties);
    }
}
