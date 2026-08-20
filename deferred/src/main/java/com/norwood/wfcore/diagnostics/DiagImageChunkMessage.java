package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * One slice of a capture travelling server-to-client. Clientbound custom payloads may be up to 1 MiB, but this
 * deliberately reuses {@link DiagChunkMessage}'s 32 KiB clamp so both directions share one sizing rule.
 */
public record DiagImageChunkMessage(long requestId, int index, byte[] data) {

    public static void write(DiagImageChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.requestId);
        buf.writeVarInt(msg.index);
        buf.writeByteArray(msg.data);
    }

    public static DiagImageChunkMessage read(FriendlyByteBuf buf) {
        return new DiagImageChunkMessage(buf.readLong(), buf.readVarInt(),
                buf.readByteArray(DiagChunkMessage.MAX_CHUNK_BYTES));
    }

    public static void handle(DiagImageChunkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.DiagViewerClient.onImageChunk(msg)));
        context.setPacketHandled(true);
    }
}
