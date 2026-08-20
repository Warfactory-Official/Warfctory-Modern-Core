package com.norwood.wfcore.diagnostics.server;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.ClassDumpCatalogMessage;

import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class ClassDumpCatalog {

    public static final int MAX_ENTRIES = 512;

    /** Exactly the character set {@code ClassDumpService.sanitize} can produce, plus the extension. */
    private static final Pattern SAFE_NAME = Pattern.compile("[A-Za-z0-9_.-]+\\.txt");

    private static final Pattern STAMPED =
            Pattern.compile("^(.*)_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2})\\.txt$");

    private static final Pattern CLASS_COUNT = Pattern.compile("# classes:\\s*(\\d+)");

    private ClassDumpCatalog() {}

    public record Scan(List<ClassDumpCatalogMessage.Entry> entries, boolean truncated) {}

    public static Path directory(MinecraftServer server) {
        return server.getServerDirectory().toPath().resolve("classloader");
    }

    public static Scan scan(MinecraftServer server) {
        Path dir = directory(server);
        List<ClassDumpCatalogMessage.Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return new Scan(out, false);
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.txt")) {
            for (Path path : stream) {
                String name = path.getFileName().toString();
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                long size = Files.size(path);
                if (size <= 0 || size > Integer.MAX_VALUE) {
                    continue;
                }
                Matcher matcher = STAMPED.matcher(name);
                String username = matcher.matches() ? matcher.group(1) : "unknown";
                String stamp = matcher.matches() ? matcher.group(2) : name;
                out.add(new ClassDumpCatalogMessage.Entry(username, stamp, name, (int) size, readClassCount(path)));
            }
        } catch (IOException e) {
            WFCore.LOGGER.warn("[wfcore-classdump] failed to list {}: {}", dir, e.toString());
            return new Scan(out, false);
        }
        out.sort(Comparator.comparing(ClassDumpCatalogMessage.Entry::stamp).reversed());
        boolean truncated = out.size() > MAX_ENTRIES;
        if (truncated) {
            out = new ArrayList<>(out.subList(0, MAX_ENTRIES));
        }
        return new Scan(out, truncated);
    }

    /** Reads the {@code # classes: N} header line without loading the whole (multi-MB) dump. */
    private static int readClassCount(Path path) {
        try {
            byte[] head = new byte[512];
            int read;
            try (var in = Files.newInputStream(path)) {
                read = in.read(head);
            }
            if (read <= 0) {
                return 0;
            }
            Matcher m = CLASS_COUNT.matcher(new String(head, 0, read, StandardCharsets.UTF_8));
            return m.find() ? Integer.parseInt(m.group(1)) : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /** Loads one dump by bare filename, or {@code null} if it fails validation. The name is treated as hostile. */
    public static byte[] read(MinecraftServer server, String fileName, int maxBytes) {
        if (fileName == null || !SAFE_NAME.matcher(fileName).matches()) {
            return null;
        }
        Path dir = directory(server).toAbsolutePath().normalize();
        Path path = dir.resolve(fileName).toAbsolutePath().normalize();
        if (path.getParent() == null || !path.getParent().equals(dir) || !Files.isRegularFile(path)) {
            return null;
        }
        try {
            long size = Files.size(path);
            if (size <= 0 || size > maxBytes) {
                return null;
            }
            return Files.readAllBytes(path);
        } catch (IOException e) {
            WFCore.LOGGER.warn("[wfcore-classdump] failed to read {}: {}", path, e.toString());
            return null;
        }
    }
}
