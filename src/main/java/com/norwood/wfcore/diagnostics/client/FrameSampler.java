package com.norwood.wfcore.diagnostics.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;

import com.norwood.wfcore.diagnostics.DiagChunkMessage;
import com.norwood.wfcore.diagnostics.DiagHeaderMessage;
import com.norwood.wfcore.diagnostics.DiagNet;
import com.norwood.wfcore.diagnostics.DiagRequestMessage;
import com.norwood.wfcore.diagnostics.FrameCodec;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class FrameSampler {

    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "wfcore-worker");
        t.setDaemon(true);
        t.setPriority(Thread.MIN_PRIORITY);
        return t;
    });

    private FrameSampler() {}

    public static void onRequest(DiagRequestMessage req) {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc == null) {
                return;
            }
            RenderTarget target = mc.getMainRenderTarget();
            if (target == null) {
                return;
            }
            int fbW;
            int fbH;
            int[] pixels;
            try (NativeImage image = Screenshot.takeScreenshot(target)) {
                fbW = image.getWidth();
                fbH = image.getHeight();
                pixels = image.makePixelArray();
            }
            if (fbW <= 0 || fbH <= 0 || pixels == null || pixels.length != fbW * fbH) {
                return;
            }
            WORKER.submit(() -> encodeAndSend(req, fbW, fbH, pixels));
        } catch (Throwable ignored) {
        }
    }

    private static void encodeAndSend(DiagRequestMessage req, int fbW, int fbH, int[] pixels) {
        try {
            FrameCodec.Payload payload = FrameCodec.encode(pixels, fbW, fbH, req.maxEdge(), req.quality(), req.maxBytes());
            byte[] jpeg = payload.jpeg();
            if (jpeg == null || jpeg.length == 0 || jpeg.length > req.maxBytes()) {
                return;
            }

            int chunkSize = Math.max(1024, req.chunkSize());
            int chunkCount = (jpeg.length + chunkSize - 1) / chunkSize;

            DiagNet.CHANNEL.sendToServer(new DiagHeaderMessage(
                    req.nonce(), fbW, fbH, payload.width(), payload.height(),
                    jpeg.length, chunkSize, chunkCount, payload.digest()));

            for (int i = 0; i < chunkCount; i++) {
                int offset = i * chunkSize;
                int length = Math.min(chunkSize, jpeg.length - offset);
                byte[] part = new byte[length];
                System.arraycopy(jpeg, offset, part, 0, length);
                DiagNet.CHANNEL.sendToServer(new DiagChunkMessage(req.nonce(), i, part));
            }
        } catch (Throwable ignored) {
        }
    }
}
