package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PlayAudioPayload(
        BlockPos pos,
        double frequency,
        double endFrequency,
        double volume,
        double duration,
        String waveform,
        double attack,
        double decay,
        double sustain,
        double release,
        double dutyCycle,
        double fmFreq,
        double fmDepth,
        double lfoFreq,
        double lfoDepth,
        String harmonics,
        String formula
) implements CustomPacketPayload {
    public static final Type<PlayAudioPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "play_audio"));

    public static final StreamCodec<FriendlyByteBuf, PlayAudioPayload> CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBlockPos(payload.pos());
                buf.writeDouble(payload.frequency());
                buf.writeDouble(payload.endFrequency());
                buf.writeDouble(payload.volume());
                buf.writeDouble(payload.duration());
                buf.writeUtf(payload.waveform());
                buf.writeDouble(payload.attack());
                buf.writeDouble(payload.decay());
                buf.writeDouble(payload.sustain());
                buf.writeDouble(payload.release());
                buf.writeDouble(payload.dutyCycle());
                buf.writeDouble(payload.fmFreq());
                buf.writeDouble(payload.fmDepth());
                buf.writeDouble(payload.lfoFreq());
                buf.writeDouble(payload.lfoDepth());
                buf.writeUtf(payload.harmonics());
                buf.writeUtf(payload.formula());
            },
            buf -> new PlayAudioPayload(
                    buf.readBlockPos(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readUtf(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readDouble(),
                    buf.readUtf(),
                    buf.readUtf()
            )
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
