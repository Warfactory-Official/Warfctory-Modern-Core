package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public record ClassDumpViewChunkMessage(long requestId, int part, int parts, byte[] blob) {

    public static void write(ClassDumpViewChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.requestId);
        buf.writeVarInt(msg.part);
        buf.writeVarInt(msg.parts);
        buf.writeByteArray(msg.blob);
    }

    public static ClassDumpViewChunkMessage read(FriendlyByteBuf buf) {
        long requestId = buf.readLong();
        int part = buf.readVarInt();
        int parts = buf.readVarInt();
        byte[] blob = buf.readByteArray(ClassDumpChunkMessage.MAX_BLOB);
        if (parts < 1 || parts > ClassDumpChunkMessage.MAX_PARTS || part < 0 || part >= parts) {
            return new ClassDumpViewChunkMessage(requestId, 0, 0, new byte[0]);
        }
        return new ClassDumpViewChunkMessage(requestId, part, parts, blob);
    }

    public static void handle(ClassDumpViewChunkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.ClassDumpViewerClient.onViewChunk(msg)));
        context.setPacketHandled(true);
    }
}
