package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


public record ClassDumpCatalogMessage(List<Entry> entries, boolean truncated) {

    private static final int MAX_DECODED_ENTRIES = 4096;

    /** One dump on disk: {@code username} + {@code stamp} recovered from {@code <username>_<stamp>.txt}. */
    public record Entry(String username, String stamp, String fileName, int size, int classCount) {}

    public static void write(ClassDumpCatalogMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.truncated);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeUtf(entry.username(), 64);
            buf.writeUtf(entry.stamp(), 128);
            buf.writeUtf(entry.fileName(), 256);
            buf.writeVarInt(entry.size());
            buf.writeVarInt(entry.classCount());
        }
    }

    public static ClassDumpCatalogMessage read(FriendlyByteBuf buf) {
        boolean truncated = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), MAX_DECODED_ENTRIES);
        List<Entry> entries = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readUtf(64), buf.readUtf(128), buf.readUtf(256),
                    buf.readVarInt(), buf.readVarInt()));
        }
        return new ClassDumpCatalogMessage(entries, truncated);
    }

    public static void handle(ClassDumpCatalogMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.ClassDumpViewerClient.onCatalog(msg)));
        context.setPacketHandled(true);
    }
}
