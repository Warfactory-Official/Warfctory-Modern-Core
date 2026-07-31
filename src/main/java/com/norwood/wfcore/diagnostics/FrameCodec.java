package com.norwood.wfcore.diagnostics;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Iterator;

public final class FrameCodec {

    public record Payload(byte[] jpeg, int width, int height, byte[] digest) {}

    private FrameCodec() {}

    public static Payload encode(int[] argb, int srcWidth, int srcHeight, int maxEdge, int quality, int maxBytes)
            throws Exception {
        BufferedImage source = new BufferedImage(srcWidth, srcHeight, BufferedImage.TYPE_INT_RGB);
        source.setRGB(0, 0, srcWidth, srcHeight, argb, 0, srcWidth);
        BufferedImage scaled = scaleToMaxEdge(source, Math.max(64, maxEdge));
        byte[] jpeg = encodeUnder(scaled, quality, maxBytes);
        return new Payload(jpeg, scaled.getWidth(), scaled.getHeight(), sha256(jpeg));
    }

    public static BufferedImage scaleToMaxEdge(BufferedImage src, int maxEdge) {
        int w = src.getWidth();
        int h = src.getHeight();
        int longest = Math.max(w, h);
        if (longest <= maxEdge) {
            return src;
        }
        double scale = (double) maxEdge / (double) longest;
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static byte[] encodeUnder(BufferedImage image, int quality, int maxBytes) throws Exception {
        BufferedImage current = image;
        float q = clampQuality(quality);
        for (int attempt = 0; attempt < 6; attempt++) {
            byte[] bytes = writeJpeg(current, q);
            if (bytes.length <= maxBytes) {
                return bytes;
            }
            if (q > 0.32f) {
                q = Math.max(0.3f, q - 0.12f);
                continue;
            }
            int longest = Math.max(current.getWidth(), current.getHeight());
            int reduced = (int) Math.round(longest * 0.8);
            if (reduced < 64) {
                return bytes;
            }
            current = scaleToMaxEdge(current, reduced);
        }
        return writeJpeg(current, 0.3f);
    }

    private static float clampQuality(int quality) {
        float q = quality / 100.0f;
        if (q < 0.1f) {
            return 0.1f;
        }
        if (q > 0.95f) {
            return 0.95f;
        }
        return q;
    }

    private static byte[] writeJpeg(BufferedImage image, float quality) throws Exception {
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpeg");
        if (!writers.hasNext()) {
            throw new IllegalStateException();
        }
        ImageWriter writer = writers.next();
        ImageWriteParam param = writer.getDefaultWriteParam();
        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        param.setCompressionQuality(quality);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }

    public static byte[] sha256(byte[] data) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(data);
    }
}
