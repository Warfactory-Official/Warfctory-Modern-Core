package com.norwood.wfcore.diagnostics.server;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagCatalogMessage;

import net.minecraft.server.MinecraftServer;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads back what {@code DiagnosticsService.writeImage} put on disk: {@code <server dir>/screenshots} for
 * verified frames and {@code <server dir>/screenshots/flagged} for the ones {@link FrameVerifier} rejected.
 * This is the only reader of those directories — everything the viewer shows or streams goes through here, so
 * the filename validation lives in one place.
 */
public final class ScreenshotCatalog {

    /** Newest-first cap per tab. The catalog rides in a single packet and an operator scrolling past a few
     *  hundred entries is already lost; anything dropped is reported through the truncated flags. */
    public static final int MAX_ENTRIES_PER_TAB = 512;

    /** Exactly the character set {@code DiagnosticsService.sanitize} can produce, plus the extension. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]+\\.jpg");

    /** {@code <username>_yyyy-MM-dd_HH-mm-ss.jpg}; the greedy prefix stops at the last stamp-shaped tail. */
    private static final Pattern STAMPED =
            Pattern.compile("^(.*)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.jpg$");

    private ScreenshotCatalog() {}

    public record Scan(List<DiagCatalogMessage.Entry> entries, boolean truncatedVerified, boolean truncatedFlagged) {}

    /** Header data for one capture being streamed to a client. */
    public record Image(byte[] jpeg, int width, int height) {}

    public static Path directory(MinecraftServer server, boolean flagged) {
        Path base = server.getServerDirectory().toPath().resolve("screenshots");
        return flagged ? base.resolve("flagged") : base;
    }

    /** Scans both directories. Safe to call off the server thread; touches no game state. */
    public static Scan scan(MinecraftServer server) {
        List<DiagCatalogMessage.Entry> verified = list(server, false);
        List<DiagCatalogMessage.Entry> flagged = list(server, true);
        boolean truncatedVerified = verified.size() > MAX_ENTRIES_PER_TAB;
        boolean truncatedFlagged = flagged.size() > MAX_ENTRIES_PER_TAB;

        List<DiagCatalogMessage.Entry> entries = new ArrayList<>();
        entries.addAll(verified.subList(0, Math.min(verified.size(), MAX_ENTRIES_PER_TAB)));
        entries.addAll(flagged.subList(0, Math.min(flagged.size(), MAX_ENTRIES_PER_TAB)));
        return new Scan(entries, truncatedVerified, truncatedFlagged);
    }

    private static List<DiagCatalogMessage.Entry> list(MinecraftServer server, boolean flagged) {
        Path dir = directory(server, flagged);
        List<DiagCatalogMessage.Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        // Non-recursive on purpose: the flagged subdirectory is enumerated as its own tab, and nothing else
        // is expected under screenshots/.
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path) || !name.toLowerCase().endsWith(".jpg")) {
                    continue;
                }
                long size = Files.size(path);
                if (size <= 0 || size > Integer.MAX_VALUE) {
                    continue;
                }
                Matcher matcher = STAMPED.matcher(name);
                String username = matcher.matches() ? matcher.group(1) : "unknown";
                String stamp = matcher.matches() ? matcher.group(2) : name;
                out.add(new DiagCatalogMessage.Entry(flagged, username, stamp, name, (int) size));
            }
        } catch (IOException e) {
            WFCore.LOGGER.warn("[wfcore-diag] failed to list {}: {}", dir, e.toString());
            return out;
        }
        // Newest first. The stamp sorts lexicographically in chronological order, so it doubles as the key
        // and stays stable even if the files are copied around and lose their mtimes.
        out.sort(Comparator.comparing(DiagCatalogMessage.Entry::stamp).reversed());
        return out;
    }

    /**
     * Loads one capture by bare filename. Returns {@code null} for anything that fails validation — a client
     * may send an image request unprompted, so the name is treated as hostile: it must match
     * {@link #SAFE_NAME} (which excludes {@code /}, {@code \} and {@code ..}) and the resolved path's parent
     * must be exactly the expected directory.
     */
    public static Image read(MinecraftServer server, boolean flagged, String fileName, int maxBytes) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            return null;
        }
        Path dir = directory(server, flagged).toAbsolutePath().normalize();
        Path path = dir.resolve(fileName).toAbsolutePath().normalize();
        if (!path.getParent().equals(dir) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size <= 0 || size > maxBytes) {
                return null;
            }
            byte[] jpeg = Files.readAllBytes(path);
            int[] dims = dimensions(jpeg);
            if (dims == null) {
                return null;
            }
            return new Image(jpeg, dims[0], dims[1]);
        } catch (IOException e) {
            WFCore.LOGGER.warn("[wfcore-diag] failed to read {}: {}", path, e.toString());
            return null;
        }
    }

    /** Dimensions from the JPEG header only — the reader never decodes the pixels. */
    private static int[] dimensions(byte[] jpeg) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(jpeg))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                return null;
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in);
                return new int[] { reader.getWidth(0), reader.getHeight(0) };
            } finally {
                reader.dispose();
            }
        } catch (Exception e) {
            return null;
        }
    }
}
