package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.ModListRequestMessage;
import com.norwood.wfcore.diagnostics.ModReportMessage;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.locating.IModFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Client half of the soft mod audit. On a {@link ModListRequestMessage} it hashes every jar the client actually
 * loaded from its mods folder and replies with a {@link ModReportMessage}. Hashing is done off the render thread
 * (~hundreds of MB of jars); only physical mods-folder files are hashed, so jar-in-jar / library entries and the
 * minecraft & forge jars are skipped, matching how the server-side manifest is generated.
 */
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

    private static List<ModReportMessage.Entry> collect() {
        Path modsDir;
        try {
            modsDir = FMLPaths.MODSDIR.get().toAbsolutePath().normalize();
        } catch (Throwable t) {
            modsDir = null;
        }

        // Dedup by file name: a jar that ships several mods appears once in getModFiles(), but be defensive.
        Map<String, byte[]> byName = new LinkedHashMap<>();
        for (IModFileInfo info : ModList.get().getModFiles()) {
            IModFile file = info == null ? null : info.getFile();
            if (file == null) {
                continue;
            }
            Path path;
            try {
                path = file.getFilePath();
            } catch (Throwable t) {
                continue;
            }
            if (path == null) {
                continue;
            }
            Path norm = path.toAbsolutePath().normalize();
            if (modsDir != null && !norm.startsWith(modsDir)) {
                continue; // library jar, the game jar, or a jar-in-jar union path -> not a mods-folder file
            }
            if (!Files.isRegularFile(norm)) {
                continue;
            }
            String name = norm.getFileName().toString();
            if (byName.containsKey(name)) {
                continue;
            }
            byte[] digest = sha256(norm);
            if (digest != null) {
                byName.put(name, digest);
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
