package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.ModInventory;
import com.norwood.wfcore.diagnostics.ModListRequestMessage;
import com.norwood.wfcore.diagnostics.ModReportMessage;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;


public final class ModAuditClient {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-modaudit");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ModAuditClient() {}

    public static void onRequest(ModListRequestMessage req) {
        WORKER.submit(() -> {
            try {
                String report = buildReport(req.signatures());
                sendChunked(req.nonce(), gzip(report));
            } catch (Throwable t) {
                WFCore.LOGGER.debug("[wfcore-modaudit] failed to build mod report: {}", t.toString());
            }
        });
    }

    private static String buildReport(List<String> signatures) {
        StringBuilder sb = new StringBuilder(1 << 14);

        Map<String, String> modsByFile = ModInventory.modsByFileName();
        Set<String> modsFolderNames = new HashSet<>();

        // 1) Every physical jar in mods/ — the real integrity surface. Hash it and tag which loaded mods it provides.
        Path modsDir = FMLPaths.MODSDIR.get();
        if (Files.isDirectory(modsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
                for (Path p : stream) {
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String name = p.getFileName().toString();
                    if (!modsFolderNames.add(name)) {
                        continue; // dedup defensively
                    }
                    String hex = ModInventory.sha256hex(p);
                    if (hex == null) {
                        continue;
                    }
                    String mods = modsByFile.getOrDefault(name, "(present, not loaded)");
                    sb.append("H\t").append(hex).append('\t')
                            .append(ModInventory.sanitize(name)).append('\t')
                            .append(ModInventory.sanitize(mods)).append('\n');
                }
            } catch (Throwable t) {
                WFCore.LOGGER.debug("[wfcore-modaudit] could not scan mods dir: {}", t.toString());
            }
        }

        for (ModInventory.LoadedFile file : ModInventory.loadedFiles()) {
            if (modsFolderNames.contains(file.fileName()) || file.path() == null) {
                continue;
            }
            if (!Files.isRegularFile(file.path())) {
                continue;
            }
            String hex = ModInventory.sha256hex(file.path());
            sb.append("X\t").append(hex != null ? hex : "-").append('\t')
                    .append(ModInventory.sanitize(file.fileName())).append('\t')
                    .append(ModInventory.sanitize(file.mods())).append('\n');
        }

        if (signatures != null) {
            for (String sig : signatures) {
                if (sig != null && !sig.isBlank() && classPresent(sig.trim())) {
                    sb.append("C\t").append(ModInventory.sanitize(sig.trim())).append('\n');
                }
            }
        }

        return sb.toString();
    }

    private static boolean classPresent(String className) {
        ClassLoader[] loaders = {
                ModAuditClient.class.getClassLoader(),
                Thread.currentThread().getContextClassLoader()
        };
        for (ClassLoader loader : loaders) {
            if (loader == null) {
                continue;
            }
            try {
                Class.forName(className, false, loader);
                return true;
            } catch (ClassNotFoundException absent) {
                // try the next loader
            } catch (Throwable present) {
                return true;
            }
        }
        return false;
    }

    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(64, text.length() / 4));
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void sendChunked(long nonce, byte[] blob) {
        int chunk = ModReportMessage.MAX_BLOB;
        int parts = Math.max(1, (blob.length + chunk - 1) / chunk);
        if (parts > ModReportMessage.MAX_PARTS) {
            WFCore.LOGGER.debug("[wfcore-modaudit] mod report too large ({} bytes) to send", blob.length);
            return;
        }
        List<ModReportMessage> messages = new ArrayList<>(parts);
        for (int i = 0; i < parts; i++) {
            int from = i * chunk;
            int to = Math.min(from + chunk, blob.length);
            byte[] slice = new byte[to - from];
            System.arraycopy(blob, from, slice, 0, slice.length);
            messages.add(new ModReportMessage(nonce, i, parts, slice));
        }
        for (ModReportMessage msg : messages) {
            DiagNet.CHANNEL.sendToServer(msg);
        }
    }
}
