package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.DiagnosticsService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DiagHeaderMessage(long nonce, int fbWidth, int fbHeight, int imgWidth, int imgHeight,
        int totalLen, int chunkSize, int chunkCount, byte[] digest) {

    public static void write(DiagHeaderMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.fbWidth);
        buf.writeVarInt(msg.fbHeight);
        buf.writeVarInt(msg.imgWidth);
        buf.writeVarInt(msg.imgHeight);
        buf.writeVarInt(msg.totalLen);
        buf.writeVarInt(msg.chunkSize);
        buf.writeVarInt(msg.chunkCount);
        buf.writeByteArray(msg.digest);
    }

    public static DiagHeaderMessage read(FriendlyByteBuf buf) {
        return new DiagHeaderMessage(
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(64));
    }

    public static void handle(DiagHeaderMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> DiagnosticsService.INSTANCE.onHeader(sender, msg));
        context.setPacketHandled(true);
    }
}
