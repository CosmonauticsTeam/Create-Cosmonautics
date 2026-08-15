package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SolarBurnPayload() implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SolarBurnPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "solar_burn")
    );

    public static final StreamCodec<FriendlyByteBuf, SolarBurnPayload> CODEC = StreamCodec.unit(new SolarBurnPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
