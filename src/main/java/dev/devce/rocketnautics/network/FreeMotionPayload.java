package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record FreeMotionPayload(Quaternionf orientation, Vector3f deltaMovement, Vector3f thrustStrength) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FreeMotionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "free_motion_orientation"));

    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMotionPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.QUATERNIONF, FreeMotionPayload::orientation,
            ByteBufCodecs.VECTOR3F, FreeMotionPayload::deltaMovement,
            ByteBufCodecs.VECTOR3F, FreeMotionPayload::thrustStrength,
            FreeMotionPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
