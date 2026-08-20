package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.ModAuditService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ModReportMessage(long nonce, int part, int parts, byte[] blob) {

    public static final int MAX_PARTS = 64;

    public static final int MAX_BLOB = DiagChunkMessage.MAX_PAYLOAD_SIZE - 64;

    public static void write(ModReportMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.part);
        buf.writeVarInt(msg.parts);
        buf.writeByteArray(msg.blob);
    }

    public static ModReportMessage read(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        byte[] blob = buf.readByteArray(MAX_BLOB);
        if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) {
            return new ModReportMessage(nonce, 0, 0, new byte[0]);
        }
        return new ModReportMessage(nonce, part, parts, blob);
    }

    public static void handle(ModReportMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> ModAuditService.INSTANCE.onReportChunk(sender, msg));
        context.setPacketHandled(true);
    }
}
