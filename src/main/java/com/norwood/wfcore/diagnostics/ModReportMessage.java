package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.ModAuditService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server: the sha-256 of every jar the client loaded from its mods folder, keyed by file name.
 * The server matches these against {@code config/wfcore-modmanifest.json}; the payload is untrusted, so read
 * caps every field (entry count, name length, digest length) defensively.
 */
public record ModReportMessage(long nonce, List<Entry> entries) {

    /** One mods-folder jar: its file name and 32-byte sha-256. */
    public record Entry(String fileName, byte[] sha256) {}

    /** Hard caps for the untrusted inbound payload. */
    private static final int MAX_ENTRIES = 1024;
    private static final int MAX_NAME_LEN = 256;

    public static void write(ModReportMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeUtf(entry.fileName(), MAX_NAME_LEN);
            buf.writeByteArray(entry.sha256());
        }
    }

    public static ModReportMessage read(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int count = buf.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) {
            // Malformed / hostile: return an empty report so the server records "no usable report".
            return new ModReportMessage(nonce, List.of());
        }
        List<Entry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(MAX_NAME_LEN);
            byte[] sha = buf.readByteArray(64);
            entries.add(new Entry(name, sha));
        }
        return new ModReportMessage(nonce, entries);
    }

    public static void handle(ModReportMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> ModAuditService.INSTANCE.onReport(sender, msg));
        context.setPacketHandled(true);
    }
}
