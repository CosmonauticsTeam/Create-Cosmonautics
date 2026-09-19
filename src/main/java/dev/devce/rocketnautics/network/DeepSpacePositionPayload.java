package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import dev.devce.rocketnautics.client.DeepSpaceHandler;
import dev.devce.rocketnautics.content.orbit.universe.DeepSpacePosition;
import dev.devce.rocketnautics.content.orbit.universe.UniverseDefinition;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Consumer;

public final class DeepSpacePositionPayload extends BufferPayload {
    public static final Type<DeepSpacePositionPayload> TYPE = new Type<>(RocketNautics.path("deep_space_position"));
    public static final StreamCodec<FriendlyByteBuf, DeepSpacePositionPayload> CODEC = codec(DeepSpacePositionPayload::new);

    private DeepSpacePositionPayload(FriendlyByteBuf fromStreamCodec) {
        super(fromStreamCodec);
    }

    private DeepSpacePositionPayload(Consumer<FriendlyByteBuf> writer) {
        super(writer);
    }

    public static DeepSpacePositionPayload of(DeepSpacePosition position, UniverseDefinition universe) {
        return new DeepSpacePositionPayload(buf -> position.write(buf, universe));
    }

    @Override
    protected void read(FriendlyByteBuf buf) {
        DeepSpaceHandler.receivePosition(buf);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
