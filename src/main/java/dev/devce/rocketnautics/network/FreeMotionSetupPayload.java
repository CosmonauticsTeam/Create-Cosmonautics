package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
public record FreeMotionSetupPayload(boolean is6DOFEnabled, boolean isAmbulant, float movementAcceleration, float dampenerForce) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<FreeMotionSetupPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "free_motion_state"));


    public static final StreamCodec<RegistryFriendlyByteBuf, FreeMotionSetupPayload> CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, FreeMotionSetupPayload::is6DOFEnabled,
            ByteBufCodecs.BOOL, FreeMotionSetupPayload::isAmbulant,
            ByteBufCodecs.FLOAT, FreeMotionSetupPayload::movementAcceleration,
            ByteBufCodecs.FLOAT, FreeMotionSetupPayload::dampenerForce,
            FreeMotionSetupPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
