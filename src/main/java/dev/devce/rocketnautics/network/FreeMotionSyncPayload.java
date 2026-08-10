package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public record FreeMotionSyncPayload(int entityId, boolean freeMotionEnabled, boolean ambulant, Quaternionf orientation, Vector3f thrustStrength) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FreeMotionSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "free_motion_orientation_sync"));


    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMotionSyncPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.INT, FreeMotionSyncPayload::entityId,
            ByteBufCodecs.BOOL, FreeMotionSyncPayload::freeMotionEnabled,
            ByteBufCodecs.BOOL, FreeMotionSyncPayload::ambulant,
            ByteBufCodecs.QUATERNIONF, FreeMotionSyncPayload::orientation,
            ByteBufCodecs.VECTOR3F, FreeMotionSyncPayload::thrustStrength,
            FreeMotionSyncPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
