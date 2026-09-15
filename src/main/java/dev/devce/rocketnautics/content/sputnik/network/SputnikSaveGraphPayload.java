package dev.devce.rocketnautics.content.sputnik.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SputnikSaveGraphPayload(BlockPos pos, int sputnikId, byte[] graphBytes) implements CustomPacketPayload {
    public static final Type<SputnikSaveGraphPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "sputnik_save_graph"));

    public static final StreamCodec<FriendlyByteBuf, SputnikSaveGraphPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            SputnikSaveGraphPayload::pos,
            ByteBufCodecs.VAR_INT,
            SputnikSaveGraphPayload::sputnikId,
            ByteBufCodecs.BYTE_ARRAY,
            SputnikSaveGraphPayload::graphBytes,
            SputnikSaveGraphPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
