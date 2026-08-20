package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.ClassDumpCatalogMessage;
import com.norwood.wfcore.diagnostics.ClassDumpListRequestMessage;
import com.norwood.wfcore.diagnostics.ClassDumpViewChunkMessage;
import com.norwood.wfcore.diagnostics.ClassDumpViewRequestMessage;
import com.norwood.wfcore.diagnostics.DiagNet;

import net.minecraft.client.Minecraft;

import brachy.modularui.factory.ClientGUI;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPInputStream;


public final class ClassDumpViewerClient {

    public static final int MAX_VIEW_ROWS = 800;

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-classdump-view");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private static List<ClassDumpCatalogMessage.Entry> catalog = List.of();
    private static boolean truncated;

    private static ClassDumpCatalogMessage.Entry selected;
    private static String searchFilter = "";
    private static String classFilter = "";

    private static long requestCounter;
    private static long activeRequest;
    private static Assembly assembly;

    private static List<String> lines = List.of();
    private static String error;

    private static final int MAX_INFLATED_BYTES = 128 * 1024 * 1024;

    private static boolean viewerOpen;
    private static long screenGeneration;

    private ClassDumpViewerClient() {}


    private static void openViewer() {
        viewerOpen = true;
        long generation = ++screenGeneration;
        ClientGUI.open(new ClassDumpViewerScreen(generation));
    }

    public static void onScreenClosed(long generation) {
        if (generation != screenGeneration) {
            return;
        }
        viewerOpen = false;
        reset();
    }
    public static void onCatalog(ClassDumpCatalogMessage msg) {
        catalog = List.copyOf(msg.entries());
        truncated = msg.truncated();
        if (selected != null && catalog.stream().noneMatch(e -> sameEntry(e, selected))) {
            selected = null;
            lines = List.of();
        }
        openViewer();
    }

    public static void onViewChunk(ClassDumpViewChunkMessage msg) {
        if (msg.parts() <= 0 || msg.requestId() != activeRequest) {
            return;
        }
        Assembly current = assembly;
        if (current == null || current.requestId != msg.requestId()) {
            current = new Assembly(msg.requestId(), msg.parts());
            assembly = current;
        }
        if (current.parts != msg.parts()) {
            assembly = null;
            fail("class dump transfer inconsistent");
            return;
        }
        if (!current.accept(msg.part(), msg.blob())) {
            return;
        }
        if (!current.complete()) {
            return;
        }
        assembly = null;
        Assembly done = current;
        WORKER.submit(() -> decode(done));
    }

    private static void decode(Assembly done) {
        try {
            String text = inflate(done.concat(), MAX_INFLATED_BYTES);
            if (text == null) {
                Minecraft.getInstance().execute(() -> fail("dump too large or corrupt"));
                return;
            }
            List<String> parsed = new ArrayList<>();
            for (String line : text.split("\n")) {
                parsed.add(line);
            }
            Minecraft.getInstance().execute(() -> {
                if (done.requestId != activeRequest) {
                    return; // superseded by a newer selection
                }
                lines = List.copyOf(parsed);
                error = null;
                // Re-open so the right pane rebuilds against the now-loaded text.
                if (viewerOpen) {
                    openViewer();
                }
            });
        } catch (Throwable t) {
            Minecraft.getInstance().execute(() -> fail("could not decode dump: " + t));
        }
    }

    // ------------------------------------------------------------------------------------------------------
    // GUI-facing state
    // ------------------------------------------------------------------------------------------------------

    public static List<ClassDumpCatalogMessage.Entry> catalog() {
        return catalog;
    }

    public static boolean isTruncated() {
        return truncated;
    }

    public static String searchFilter() {
        return searchFilter;
    }

    public static void setSearchFilter(String value) {
        searchFilter = value == null ? "" : value;
    }

    public static boolean matchesFilter(String username) {
        String filter = searchFilter;
        return filter.isEmpty() || username.toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT));
    }

    public static long visibleCount() {
        return catalog.stream().filter(e -> matchesFilter(e.username())).count();
    }

    public static String classFilter() {
        return classFilter;
    }

    public static void setClassFilter(String value) {
        classFilter = value == null ? "" : value;
    }

    /** Applies the current class filter by rebuilding the viewer (Enter in the class-filter box). */
    public static void applyClassFilter() {
        if (viewerOpen) {
            openViewer();
        }
    }

    public static ClassDumpCatalogMessage.Entry selected() {
        return selected;
    }

    public static boolean isSelected(ClassDumpCatalogMessage.Entry entry) {
        return selected != null && sameEntry(entry, selected);
    }

    public static String error() {
        return error;
    }

    public static boolean isLoading() {
        return selected != null && lines.isEmpty() && error == null;
    }

    /** Class lines of the selected dump matching the class filter (comment/header lines drop under a filter). */
    public static List<String> filteredLines() {
        String filter = classFilter.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line.isEmpty()) {
                continue;
            }
            if (filter.isEmpty() || line.toLowerCase(Locale.ROOT).contains(filter)) {
                out.add(line);
                if (out.size() >= MAX_VIEW_ROWS) {
                    break;
                }
            }
        }
        return out;
    }

    /** Total class lines that match the filter (may exceed {@link #MAX_VIEW_ROWS}). */
    public static long matchCount() {
        String filter = classFilter.toLowerCase(Locale.ROOT);
        return lines.stream()
                .filter(l -> !l.isEmpty())
                .filter(l -> filter.isEmpty() || l.toLowerCase(Locale.ROOT).contains(filter))
                .count();
    }

    public static void select(ClassDumpCatalogMessage.Entry entry) {
        selected = entry;
        error = null;
        assembly = null;
        lines = List.of();
        classFilter = "";
        if (entry == null) {
            return;
        }
        activeRequest = ++requestCounter;
        DiagNet.CHANNEL.sendToServer(new ClassDumpViewRequestMessage(activeRequest, entry.fileName()));
        // Rebuild now so the pane immediately shows "Loading..." and the new row highlights, instead of the
        // previous dump's classes lingering until the transfer completes.
        if (viewerOpen) {
            openViewer();
        }
    }

    public static void refresh() {
        DiagNet.CHANNEL.sendToServer(ClassDumpListRequestMessage.INSTANCE);
    }

    public static void reset() {
        activeRequest = 0L;
        assembly = null;
    }

    private static void fail(String reason) {
        error = reason;
        WFCore.LOGGER.warn("[wfcore-classdump] viewer: {}", reason);
    }

    /** Inflates the downloaded gzip, refusing anything that decompresses past {@code maxOut} (zip-bomb guard). */
    private static String inflate(byte[] gz, int maxOut) {
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gz))) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(maxOut, gz.length * 4));
            byte[] buf = new byte[1 << 15];
            int read;
            long total = 0;
            while ((read = in.read(buf)) != -1) {
                total += read;
                if (total > maxOut) {
                    return null;
                }
                out.write(buf, 0, read);
            }
            return out.toString(StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean sameEntry(ClassDumpCatalogMessage.Entry a, ClassDumpCatalogMessage.Entry b) {
        return a.fileName().equals(b.fileName());
    }

    /** Part-indexed chunk accumulator for a downloading dump. */
    private static final class Assembly {
        private final long requestId;
        private final int parts;
        private final byte[][] slices;
        private int remaining;

        Assembly(long requestId, int parts) {
            this.requestId = requestId;
            this.parts = parts;
            this.slices = new byte[parts][];
            this.remaining = parts;
        }

        boolean accept(int index, byte[] data) {
            if (index < 0 || index >= parts || slices[index] != null) {
                return false;
            }
            slices[index] = data;
            remaining--;
            return true;
        }

        boolean complete() {
            return remaining == 0;
        }

        byte[] concat() {
            int total = 0;
            for (byte[] s : slices) {
                total += s == null ? 0 : s.length;
            }
            byte[] out = new byte[total];
            int pos = 0;
            for (byte[] s : slices) {
                if (s != null) {
                    System.arraycopy(s, 0, out, pos, s.length);
                    pos += s.length;
                }
            }
            return out;
        }
    }
}
