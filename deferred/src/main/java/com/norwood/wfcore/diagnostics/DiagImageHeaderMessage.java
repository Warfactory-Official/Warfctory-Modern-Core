package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Opens a server-to-client image transfer: the mirror of {@link DiagHeaderMessage}, which carries a frame the
 * other way. {@code totalLen}/{@code chunkSize}/{@code chunkCount} let the client size its buffer up front and
 * {@code digest} lets it reject a truncated or scrambled transfer, exactly as the capture path does.
 */
public record DiagImageHeaderMessage(long requestId, int imgWidth, int imgHeight, int totalLen, int chunkSize,
        int chunkCount, byte[] digest) {

    public static void write(DiagImageHeaderMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.requestId);
        buf.writeVarInt(msg.imgWidth);
        buf.writeVarInt(msg.imgHeight);
        buf.writeVarInt(msg.totalLen);
        buf.writeVarInt(msg.chunkSize);
        buf.writeVarInt(msg.chunkCount);
        buf.writeByteArray(msg.digest);
    }

    public static DiagImageHeaderMessage read(FriendlyByteBuf buf) {
        return new DiagImageHeaderMessage(
                buf.readLong(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readByteArray(64));
    }

    public static void handle(DiagImageHeaderMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.DiagViewerClient.onImageHeader(msg)));
        context.setPacketHandled(true);
    }
}
