package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * The catalog of captures on the server's disk, sent to an operator's client so {@code /wfcore_diag view} can
 * browse them. Filenames only — image bytes travel separately and on demand
 * ({@link DiagImageRequestMessage}), because a frame is up to
 * {@code diagnostics.maxImageBytes} and pushing every one of them eagerly would stall the connection.
 *
 * <p>
 * {@code truncatedVerified} / {@code truncatedFlagged} say a tab hit
 * {@code ScreenshotCatalog.MAX_ENTRIES_PER_TAB} and older captures were left off, so the GUI can say so rather
 * than quietly implying the list is complete.
 */
public record DiagCatalogMessage(List<Entry> entries, boolean truncatedVerified, boolean truncatedFlagged) {

    /** Hard ceiling on decode, so a hostile or corrupt packet cannot make the client allocate unboundedly. */
    private static final int MAX_DECODED_ENTRIES = 4096;

    /**
     * One capture on disk. {@code username} is the prefix recovered from the filename (the capture writer
     * builds names as {@code <sanitized username>_<stamp>.jpg}); {@code stamp} is the human-readable
     * {@code yyyy-MM-dd_HH-mm-ss} tail, or the raw filename when it does not follow the convention.
     */
    public record Entry(boolean flagged, String username, String stamp, String fileName, int size) {}

    public static void write(DiagCatalogMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.truncatedVerified);
        buf.writeBoolean(msg.truncatedFlagged);
        buf.writeVarInt(msg.entries.size());
        for (Entry entry : msg.entries) {
            buf.writeBoolean(entry.flagged());
            buf.writeUtf(entry.username(), 64);
            buf.writeUtf(entry.stamp(), 128);
            buf.writeUtf(entry.fileName(), 256);
            buf.writeVarInt(entry.size());
        }
    }

    public static DiagCatalogMessage read(FriendlyByteBuf buf) {
        boolean truncatedVerified = buf.readBoolean();
        boolean truncatedFlagged = buf.readBoolean();
        int count = Math.min(buf.readVarInt(), MAX_DECODED_ENTRIES);
        List<Entry> entries = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            entries.add(new Entry(buf.readBoolean(), buf.readUtf(64), buf.readUtf(128), buf.readUtf(256),
                    buf.readVarInt()));
        }
        return new DiagCatalogMessage(entries, truncatedVerified, truncatedFlagged);
    }

    public static void handle(DiagCatalogMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.DiagViewerClient.onCatalog(msg)));
        context.setPacketHandled(true);
    }
}
