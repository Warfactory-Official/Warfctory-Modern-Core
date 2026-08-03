package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.ModListRequestMessage;
import com.norwood.wfcore.diagnostics.ModReportMessage;

import net.minecraftforge.fml.loading.FMLPaths;

import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


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
                List<ModReportMessage.Entry> entries = collect();
                DiagNet.CHANNEL.sendToServer(new ModReportMessage(req.nonce(), entries));
            } catch (Throwable t) {
                WFCore.LOGGER.debug("[wfcore-modaudit] failed to build mod report: {}", t.toString());
            }
        });
    }

    private static List<ModReportMessage.Entry> collect() throws Exception {
        Path modsDir = FMLPaths.MODSDIR.get();
        if (!Files.isDirectory(modsDir)) {
            return List.of();
        }

        // Hash every jar physically present in the main mods folder. Dedup by file name defensively.
        Map<String, byte[]> byName = new LinkedHashMap<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(modsDir, "*.jar")) {
            for (Path p : stream) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String name = p.getFileName().toString();
                if (byName.containsKey(name)) {
                    continue;
                }
                byte[] digest = sha256(p);
                if (digest != null) {
                    byName.put(name, digest);
                }
            }
        }

        List<ModReportMessage.Entry> out = new ArrayList<>(byName.size());
        for (Map.Entry<String, byte[]> e : byName.entrySet()) {
            out.add(new ModReportMessage.Entry(e.getKey(), e.getValue()));
        }
        return out;
    }

    private static byte[] sha256(Path file) {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) != -1) {
                md.update(buffer, 0, read);
            }
            return md.digest();
        } catch (Throwable t) {
            WFCore.LOGGER.debug("[wfcore-modaudit] could not hash {}: {}", file, t.toString());
            return null;
        }
    }
}
