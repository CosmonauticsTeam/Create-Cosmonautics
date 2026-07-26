package dev.devce.rocketnautics.network;

import dev.devce.rocketnautics.RocketNautics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record VectorThrusterSyncPayload(BlockPos pos, List<ItemStack> frequencies) implements CustomPacketPayload {
    public static final Type<VectorThrusterSyncPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(RocketNautics.MODID, "vector_thruster_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, VectorThrusterSyncPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC,
            VectorThrusterSyncPayload::pos,
            ItemStack.OPTIONAL_STREAM_CODEC.apply(ByteBufCodecs.list()),
            VectorThrusterSyncPayload::frequencies,
            VectorThrusterSyncPayload::new
    );

    public VectorThrusterSyncPayload(BlockPos pos, Map<Direction, ItemStack> freqs1, Map<Direction, ItemStack> freqs2) {
        this(pos, buildList(freqs1, freqs2));
    }

    private static List<ItemStack> buildList(Map<Direction, ItemStack> freqs1, Map<Direction, ItemStack> freqs2) {
        List<ItemStack> list = new ArrayList<>();
        Direction[] directions = Direction.values();
        for (int i = 0; i < 6; i++) {
            Direction dir = directions[i];
            ItemStack f1 = freqs1.get(dir);
            ItemStack f2 = freqs2.get(dir);
            list.add(f1 != null ? f1 : ItemStack.EMPTY);
            list.add(f2 != null ? f2 : ItemStack.EMPTY);
        }
        return list;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
