package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DiagRequestMessage(long nonce, int maxEdge, int quality, int maxBytes, int chunkSize) {

    public static void write(DiagRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.maxEdge);
        buf.writeVarInt(msg.quality);
        buf.writeVarInt(msg.maxBytes);
        buf.writeVarInt(msg.chunkSize);
    }

    public static DiagRequestMessage read(FriendlyByteBuf buf) {
        return new DiagRequestMessage(buf.readLong(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt());
    }

    public static void handle(DiagRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.FrameSampler.onRequest(msg)));
        context.setPacketHandled(true);
    }
}
