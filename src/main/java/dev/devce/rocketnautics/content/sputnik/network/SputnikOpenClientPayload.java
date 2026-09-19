package dev.devce.rocketnautics.content.sputnik.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SputnikOpenClientPayload(BlockPos pos, int sputnikId, byte[] graphBytes) implements CustomPacketPayload {
    public static final Type<SputnikOpenClientPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "sputnik_open_client"));

    public static final StreamCodec<FriendlyByteBuf, SputnikOpenClientPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SputnikOpenClientPayload::pos,
            ByteBufCodecs.VAR_INT,
            SputnikOpenClientPayload::sputnikId,
            ByteBufCodecs.BYTE_ARRAY,
            SputnikOpenClientPayload::graphBytes,
            SputnikOpenClientPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
