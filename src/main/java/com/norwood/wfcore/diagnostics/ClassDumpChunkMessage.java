package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.ClassDumpService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public record ClassDumpChunkMessage(long nonce, int part, int parts, byte[] blob) {

    public static final int MAX_PARTS = 1024;

    public static final int MAX_BLOB = DiagChunkMessage.MAX_PAYLOAD_SIZE - 64;

    public static void write(ClassDumpChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.part);
        buf.writeVarInt(msg.parts);
        buf.writeByteArray(msg.blob);
    }

    public static ClassDumpChunkMessage read(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        byte[] blob = buf.readByteArray(MAX_BLOB);
        if (parts < 1 || parts > MAX_PARTS || part < 0 || part >= parts) {
            return new ClassDumpChunkMessage(nonce, 0, 0, new byte[0]);
        }
        return new ClassDumpChunkMessage(nonce, part, parts, blob);
    }

    public static void handle(ClassDumpChunkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> ClassDumpService.INSTANCE.onUploadChunk(sender, msg));
        context.setPacketHandled(true);
    }
}
