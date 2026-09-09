package dev.devce.rocketnautics.content.sputnik.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SputnikOpenRequestPayload(BlockPos pos) implements CustomPacketPayload {
    public static final Type<SputnikOpenRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "sputnik_open_request"));

    public static final StreamCodec<FriendlyByteBuf, SputnikOpenRequestPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SputnikOpenRequestPayload::pos,
            SputnikOpenRequestPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
