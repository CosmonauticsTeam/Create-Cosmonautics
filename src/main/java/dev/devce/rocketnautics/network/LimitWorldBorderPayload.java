package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record LimitWorldBorderPayload() implements CustomPacketPayload {
    public static final Type<LimitWorldBorderPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "limit_world_border"));

    public static final StreamCodec<FriendlyByteBuf, LimitWorldBorderPayload> CODEC = StreamCodec.unit(new LimitWorldBorderPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
