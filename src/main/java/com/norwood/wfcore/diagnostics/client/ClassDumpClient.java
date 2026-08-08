package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.ClassDumpChunkMessage;
import com.norwood.wfcore.diagnostics.ClassDumpRequestMessage;
import com.norwood.wfcore.diagnostics.DiagNet;

import net.minecraft.client.Minecraft;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.zip.GZIPOutputStream;


public final class ClassDumpClient {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-classdump");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private ClassDumpClient() {}

    public static void onRequest(ClassDumpRequestMessage req) {
        WORKER.submit(() -> {
            try {
                ClassEnumerator.Result result = ClassEnumerator.enumerate(req.includePlatform(), req.includeDefaultPackage());
                ClassEnumerator.logSummary(result);
                String text = ClassEnumerator.render(result, playerName());
                byte[] gz = gzip(text);
                if (gz.length > Math.max(1, req.maxBytes())) {
                    WFCore.LOGGER.debug("[wfcore-classdump] dump too large ({} gz bytes > {} cap); not sending",
                            gz.length, req.maxBytes());
                    return;
                }
                sendChunked(req.nonce(), gz);
            } catch (Throwable t) {
                WFCore.LOGGER.debug("[wfcore-classdump] failed to build class dump: {}", t.toString());
            }
        });
    }

    private static String playerName() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.player != null) {
                return mc.player.getGameProfile().getName();
            }
        } catch (Throwable ignored) {
        }
        return "unknown";
    }

    private static byte[] gzip(String text) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(Math.max(1024, text.length() / 6));
        try (GZIPOutputStream gz = new GZIPOutputStream(bytes)) {
            gz.write(text.getBytes(StandardCharsets.UTF_8));
        }
        return bytes.toByteArray();
    }

    private static void sendChunked(long nonce, byte[] blob) {
        int chunk = ClassDumpChunkMessage.MAX_BLOB;
        int parts = Math.max(1, (blob.length + chunk - 1) / chunk);
        if (parts > ClassDumpChunkMessage.MAX_PARTS) {
            WFCore.LOGGER.debug("[wfcore-classdump] dump needs {} parts (> {} cap); not sending",
                    parts, ClassDumpChunkMessage.MAX_PARTS);
            return;
        }
        for (int i = 0; i < parts; i++) {
            int from = i * chunk;
            int to = Math.min(from + chunk, blob.length);
            byte[] slice = new byte[to - from];
            System.arraycopy(blob, from, slice, 0, slice.length);
            DiagNet.CHANNEL.sendToServer(new ClassDumpChunkMessage(nonce, i, parts, slice));
        }
    }
}
