package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.DiagnosticsService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DiagChunkMessage(long nonce, int index, byte[] data) {

    public static void write(DiagChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.index);
        buf.writeByteArray(msg.data);
    }

    public static DiagChunkMessage read(FriendlyByteBuf buf) {
        return new DiagChunkMessage(buf.readLong(), buf.readVarInt(), buf.readByteArray(1048576));
    }

    public static void handle(DiagChunkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> DiagnosticsService.INSTANCE.onChunk(sender, msg));
        context.setPacketHandled(true);
    }
}
