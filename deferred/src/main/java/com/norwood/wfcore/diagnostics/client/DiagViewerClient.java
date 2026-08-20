package com.norwood.wfcore.diagnostics.client;

import com.mojang.blaze3d.platform.NativeImage;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagCatalogMessage;
import com.norwood.wfcore.diagnostics.DiagImageChunkMessage;
import com.norwood.wfcore.diagnostics.DiagImageHeaderMessage;
import com.norwood.wfcore.diagnostics.DiagImageRequestMessage;
import com.norwood.wfcore.diagnostics.DiagListRequestMessage;
import com.norwood.wfcore.diagnostics.DiagNet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

import brachy.modularui.factory.ClientGUI;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client-side state behind {@code /wfcore_diag view}: the catalog the server sent, the selection, the in-flight
 * capture transfer, and the uploaded frame textures.
 *
 * <p>
 * Captures live on the <i>server's</i> disk, so browsing them is a network round trip: the command pushes a
 * {@link DiagCatalogMessage} (filenames only), and clicking a row asks for that one JPEG, which arrives as a
 * header plus chunks exactly like a capture travelling the other way. Decoding happens off-thread; the
 * {@link DynamicTexture} upload has to happen on the render thread, so it is bounced through
 * {@code Minecraft.execute}.
 *
 * <p>
 * Client-only — reached exclusively from {@code DistExecutor.unsafeRunWhenOn(Dist.CLIENT, ...)} in the message
 * handlers, so a dedicated server never loads it.
 */
public final class DiagViewerClient {

    /** Uploaded frames are full-size (up to {@code diagnostics.maxImageEdge}); keep only a few resident. */
    private static final int MAX_CACHED_TEXTURES = 4;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-diag-view");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    /** An uploaded capture, ready to blit. */
    public record Frame(ResourceLocation texture, int width, int height) {}

    private static List<DiagCatalogMessage.Entry> catalog = List.of();
    private static boolean truncatedVerified;
    private static boolean truncatedFlagged;

    private static DiagCatalogMessage.Entry selected;
    /** Player-name filter from the search box. Deliberately survives a refresh, which rebuilds the panel. */
    private static String searchFilter = "";
    private static long requestCounter;
    /** Id of the transfer we are willing to accept packets for; anything else is a stale or spoofed reply. */
    private static long activeRequest;
    private static Assembly assembly;
    private static String error;
    /** Monotonic and independent of {@link #requestCounter}, so two resident frames can never share an id. */
    private static int textureCounter;

    /** Insertion-ordered so the eldest upload is the one evicted. Keyed by {@link #key}. */
    private static final Map<String, Frame> FRAMES = new LinkedHashMap<>();

    private DiagViewerClient() {}

    /// ///////////////// packet entry points ////////////////////

    public static void onCatalog(DiagCatalogMessage msg) {
        catalog = List.copyOf(msg.entries());
        truncatedVerified = msg.truncatedVerified();
        truncatedFlagged = msg.truncatedFlagged();
        // The selection is held by filename, so it survives a refresh only if the file is still listed.
        if (selected != null && catalog.stream().noneMatch(e -> sameEntry(e, selected))) {
            selected = null;
        }
        // Rebuilding the panel from a fresh snapshot is simpler and less error-prone than mutating the row
        // widgets in place; refresh is an explicit operator action, so losing the scroll position is fine.
        ClientGUI.open(new DiagViewerScreen());
    }

    public static void onImageHeader(DiagImageHeaderMessage msg) {
        if (msg.requestId() != activeRequest) {
            return;
        }
        if (msg.totalLen() <= 0 || msg.chunkSize() <= 0 || msg.chunkCount() <= 0) {
            fail("malformed image header");
            return;
        }
        long expectedChunks = ((long) msg.totalLen() + msg.chunkSize() - 1) / msg.chunkSize();
        if (expectedChunks != msg.chunkCount()) {
            fail("image header chunk count mismatch");
            return;
        }
        assembly = new Assembly(msg.requestId(), msg.totalLen(), msg.chunkSize(), msg.chunkCount(), msg.digest());
    }

    public static void onImageChunk(DiagImageChunkMessage msg) {
        Assembly current = assembly;
        if (current == null || msg.requestId() != activeRequest || msg.requestId() != current.requestId) {
            return;
        }
        if (!current.accept(msg.index(), msg.data())) {
            assembly = null;
            fail("malformed image chunk " + msg.index());
            return;
        }
        if (!current.complete()) {
            return;
        }
        assembly = null;
        DiagCatalogMessage.Entry target = selected;
        if (target == null) {
            return;
        }
        WORKER.submit(() -> decodeAndUpload(current, target));
    }

    /// ///////////////// GUI-facing state ////////////////////

    public static List<DiagCatalogMessage.Entry> catalog() {
        return catalog;
    }

    public static String searchFilter() {
        return searchFilter;
    }

    public static void setSearchFilter(String value) {
        searchFilter = value == null ? "" : value;
    }

    /** Case-insensitive substring match on the player name; a blank filter matches everything. */
    public static boolean matchesFilter(String username) {
        String filter = searchFilter;
        return filter.isEmpty()
                || username.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    /** How many captures on {@code flagged}'s tab survive the current filter. */
    public static long visibleCount(boolean flagged) {
        return catalog.stream().filter(e -> e.flagged() == flagged && matchesFilter(e.username())).count();
    }

    public static boolean isTruncated(boolean flagged) {
        return flagged ? truncatedFlagged : truncatedVerified;
    }

    public static DiagCatalogMessage.Entry selected() {
        return selected;
    }

    public static boolean isSelected(DiagCatalogMessage.Entry entry) {
        return selected != null && sameEntry(entry, selected);
    }

    /** The uploaded frame for the current selection, or {@code null} while it is still in flight. */
    public static Frame selectedFrame() {
        return selected == null ? null : FRAMES.get(key(selected));
    }

    /** Non-null when the last transfer failed; shown in the preview pane instead of the image. */
    public static String error() {
        return error;
    }

    /** Selects a capture and requests its bytes unless they are already uploaded. */
    public static void select(DiagCatalogMessage.Entry entry) {
        selected = entry;
        error = null;
        assembly = null;
        if (entry == null || FRAMES.containsKey(key(entry))) {
            return;
        }
        activeRequest = ++requestCounter;
        DiagNet.CHANNEL.sendToServer(new DiagImageRequestMessage(activeRequest, entry.flagged(), entry.fileName()));
    }

    public static void refresh() {
        DiagNet.CHANNEL.sendToServer(DiagListRequestMessage.INSTANCE);
    }

    /**
     * Drops every uploaded frame. Called when the viewer screen closes — a handful of multi-megapixel textures
     * left resident for the rest of the session is exactly the kind of leak nobody notices until VRAM runs out.
     */
    public static void releaseTextures() {
        activeRequest = 0L;
        assembly = null;
        for (Frame frame : FRAMES.values()) {
            Minecraft.getInstance().getTextureManager().release(frame.texture());
        }
        FRAMES.clear();
    }

    /// ///////////////// decode + upload ////////////////////

    private static void decodeAndUpload(Assembly done, DiagCatalogMessage.Entry entry) {
        try {
            if (!digestMatches(done.digest, done.buffer)) {
                failLater("transfer digest mismatch");
                return;
            }
            BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(done.buffer));
            if (decoded == null) {
                failLater("could not decode capture");
                return;
            }
            int w = decoded.getWidth();
            int h = decoded.getHeight();
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, w, h, false);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int argb = decoded.getRGB(x, y);
                    // NativeImage packs ABGR; the JPEG is opaque, so force full alpha.
                    int r = (argb >> 16) & 0xFF;
                    int g = (argb >> 8) & 0xFF;
                    int b = argb & 0xFF;
                    image.setPixelRGBA(x, y, 0xFF000000 | (b << 16) | (g << 8) | r);
                }
            }
            Minecraft.getInstance().execute(() -> upload(entry, image, w, h));
        } catch (Exception e) {
            failLater("decode failed: " + e);
        }
    }

    /** Render thread only: {@link DynamicTexture} uploads straight to GL on construction. */
    private static void upload(DiagCatalogMessage.Entry entry, NativeImage image, int width, int height) {
        String cacheKey = key(entry);
        if (FRAMES.containsKey(cacheKey)) {
            image.close();
            return;
        }
        ResourceLocation id = new ResourceLocation(WFCore.MOD_ID, "diag/view/" + (++textureCounter));
        try {
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(image));
        } catch (Exception e) {
            image.close();
            error = "upload failed: " + e;
            return;
        }
        evictOldest();
        FRAMES.put(cacheKey, new Frame(id, width, height));
        error = null;
    }

    private static void evictOldest() {
        String keep = selected == null ? null : key(selected);
        while (FRAMES.size() >= MAX_CACHED_TEXTURES) {
            Iterator<Map.Entry<String, Frame>> it = FRAMES.entrySet().iterator();
            Map.Entry<String, Frame> eldest = it.next();
            // Never evict what is on screen: nothing would re-request it, so the pane would sit on "Loading".
            if (keep != null && keep.equals(eldest.getKey())) {
                if (!it.hasNext()) {
                    return;
                }
                eldest = it.next();
            }
            it.remove();
            Minecraft.getInstance().getTextureManager().release(eldest.getValue().texture());
        }
    }

    /// ///////////////// helpers ////////////////////

    private static void fail(String reason) {
        error = reason;
        WFCore.LOGGER.warn("[wfcore-diag] viewer: {}", reason);
    }

    /** Same as {@link #fail} but safe from the worker thread. */
    private static void failLater(String reason) {
        Minecraft.getInstance().execute(() -> fail(reason));
    }

    private static boolean digestMatches(byte[] declared, byte[] data) {
        try {
            return Arrays.equals(MessageDigest.getInstance("SHA-256").digest(data), declared);
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean sameEntry(DiagCatalogMessage.Entry a, DiagCatalogMessage.Entry b) {
        return a.flagged() == b.flagged() && a.fileName().equals(b.fileName());
    }

    private static String key(DiagCatalogMessage.Entry entry) {
        return (entry.flagged() ? "f/" : "v/") + entry.fileName();
    }

    /** Chunk accumulator, mirroring the server's {@code FrameSession} for transfers going the other way. */
    private static final class Assembly {

        private final long requestId;
        private final int totalLen;
        private final int chunkSize;
        private final int chunkCount;
        private final byte[] digest;
        private final byte[] buffer;
        private final boolean[] received;
        private int receivedChunks;

        Assembly(long requestId, int totalLen, int chunkSize, int chunkCount, byte[] digest) {
            this.requestId = requestId;
            this.totalLen = totalLen;
            this.chunkSize = chunkSize;
            this.chunkCount = chunkCount;
            this.digest = digest;
            this.buffer = new byte[totalLen];
            this.received = new boolean[chunkCount];
        }

        boolean accept(int index, byte[] data) {
            if (index < 0 || index >= chunkCount || received[index]) {
                return false;
            }
            int offset = index * chunkSize;
            int expected = Math.min(chunkSize, totalLen - offset);
            if (offset < 0 || offset > totalLen || data.length != expected) {
                return false;
            }
            System.arraycopy(data, 0, buffer, offset, expected);
            received[index] = true;
            receivedChunks++;
            return true;
        }

        boolean complete() {
            return receivedChunks == chunkCount;
        }
    }
}
