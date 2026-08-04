package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.DiagnosticsService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record DiagChunkMessage(long nonce, int index, byte[] data) {

    /**
     * Vanilla {@code ServerboundCustomPayloadPacket.MAX_PAYLOAD_SIZE} ({@link Short#MAX_VALUE}). A
     * serverbound custom-payload packet whose body exceeds this is rejected by the network decoder with
     * {@code IllegalArgumentException: Payload may not be larger than 32767 bytes}, which drops the client.
     */
    public static final int MAX_PAYLOAD_SIZE = 32767;

    /**
     * Per-chunk framing that rides alongside the raw JPEG slice inside the custom payload: the SimpleChannel
     * discriminator, the 8-byte nonce, the chunk-index varint and the byte-array length varint. A generous
     * fixed margin so a chunk packet always stays under {@link #MAX_PAYLOAD_SIZE}.
     */
    private static final int CHUNK_FRAMING_OVERHEAD = 64;

    /** Largest raw JPEG slice a single chunk may carry while keeping the packet under the payload limit. */
    public static final int MAX_CHUNK_BYTES = MAX_PAYLOAD_SIZE - CHUNK_FRAMING_OVERHEAD;

    /** Smallest sensible chunk; below this the per-packet overhead dominates. */
    public static final int MIN_CHUNK_BYTES = 1024;

    /** Clamp a requested chunk size into {@code [MIN_CHUNK_BYTES, MAX_CHUNK_BYTES]}. Used identically on both
     *  sides so the server's requested size and the client's actual size always agree. */
    public static int clampChunkSize(int requested) {
        if (requested < MIN_CHUNK_BYTES) {
            return MIN_CHUNK_BYTES;
        }
        if (requested > MAX_CHUNK_BYTES) {
            return MAX_CHUNK_BYTES;
        }
        return requested;
    }

    public static void write(DiagChunkMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.index);
        buf.writeByteArray(msg.data);
    }

    public static DiagChunkMessage read(FriendlyByteBuf buf) {
        return new DiagChunkMessage(buf.readLong(), buf.readVarInt(), buf.readByteArray(MAX_CHUNK_BYTES));
    }

    public static void handle(DiagChunkMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> DiagnosticsService.INSTANCE.onChunk(sender, msg));
        context.setPacketHandled(true);
    }
}
