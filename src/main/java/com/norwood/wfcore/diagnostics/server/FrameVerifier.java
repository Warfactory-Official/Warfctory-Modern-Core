package com.norwood.wfcore.diagnostics.server;

import com.norwood.wfcore.config.WFCoreConfig;

import javax.imageio.ImageIO;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.security.MessageDigest;
import java.util.Arrays;

final class FrameVerifier {

    record Result(boolean ok, String reason) {
        static Result pass() {
            return new Result(true, "ok");
        }

        static Result fail(String reason) {
            return new Result(false, reason);
        }
    }

    private FrameVerifier() {}

    static Result verify(FrameSession session, byte[] jpeg) {
        return verify(session, jpeg,
                WFCoreConfig.getDiagMaxImageBytes(),
                WFCoreConfig.getDiagMaxImageEdge(),
                WFCoreConfig.getDiagMinVariance());
    }

    static Result verify(FrameSession session, byte[] jpeg, int maxBytes, int maxEdge, double minVariance) {
        if (jpeg.length != session.totalLen) {
            return Result.fail("length mismatch (declared " + session.totalLen + ", got " + jpeg.length + ")");
        }
        if (jpeg.length > maxBytes) {
            return Result.fail("oversize payload " + jpeg.length + " > " + maxBytes);
        }
        if (!digestMatches(session.digest, jpeg)) {
            return Result.fail("digest mismatch");
        }
        if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
            return Result.fail("not a jpeg (bad SOI)");
        }
        if ((jpeg[jpeg.length - 2] & 0xFF) != 0xFF || (jpeg[jpeg.length - 1] & 0xFF) != 0xD9) {
            return Result.fail("not a jpeg (bad EOI)");
        }

        int[] sof = readSofDimensions(jpeg);
        if (sof == null) {
            return Result.fail("no SOF marker");
        }
        if (sof[0] != session.imgWidth || sof[1] != session.imgHeight) {
            return Result.fail("declared dims " + session.imgWidth + "x" + session.imgHeight
                    + " != header dims " + sof[0] + "x" + sof[1]);
        }

        int imgW = session.imgWidth;
        int imgH = session.imgHeight;
        if (imgW < 64 || imgH < 64) {
            return Result.fail("degenerate dims " + imgW + "x" + imgH);
        }
        if (Math.max(imgW, imgH) > maxEdge) {
            return Result.fail("dimensions exceed cap (" + imgW + "x" + imgH + " > edge " + maxEdge + ")");
        }

        int fbW = session.fbWidth;
        int fbH = session.fbHeight;
        if (fbW < 320 || fbH < 240 || fbW > 8192 || fbH > 8192) {
            return Result.fail("implausible framebuffer " + fbW + "x" + fbH);
        }
        double sent = (double) imgW / (double) imgH;
        double frame = (double) fbW / (double) fbH;
        if (Math.abs(sent - frame) > 0.04 * frame) {
            return Result.fail("aspect mismatch (frame " + fbW + "x" + fbH + ", image " + imgW + "x" + imgH + ")");
        }
        int expectedLongest = Math.min(Math.max(fbW, fbH), maxEdge);
        if (Math.abs(Math.max(imgW, imgH) - expectedLongest) > Math.max(4, expectedLongest / 32)) {
            return Result.fail("scale mismatch (expected longest ~" + expectedLongest + ", got " + Math.max(imgW, imgH) + ")");
        }

        BufferedImage decoded;
        try {
            decoded = ImageIO.read(new ByteArrayInputStream(jpeg));
        } catch (Exception e) {
            return Result.fail("decode failed: " + e.getMessage());
        }
        if (decoded == null) {
            return Result.fail("decode returned null");
        }
        if (decoded.getWidth() != imgW || decoded.getHeight() != imgH) {
            return Result.fail("decoded dims " + decoded.getWidth() + "x" + decoded.getHeight() + " != header");
        }

        double variance = luminanceVariance(decoded);
        if (variance < minVariance) {
            return Result.fail("near-uniform frame (variance " + String.format("%.2f", variance) + " < " + minVariance + ")");
        }

        return Result.pass();
    }

    private static boolean digestMatches(byte[] declared, byte[] jpeg) {
        try {
            byte[] actual = MessageDigest.getInstance("SHA-256").digest(jpeg);
            return Arrays.equals(actual, declared);
        } catch (Exception e) {
            return false;
        }
    }

    private static int[] readSofDimensions(byte[] d) {
        int i = 2;
        while (i + 9 < d.length) {
            if ((d[i] & 0xFF) != 0xFF) {
                i++;
                continue;
            }
            int marker = d[i + 1] & 0xFF;
            if (marker == 0xD8 || marker == 0xD9 || marker == 0x01 || (marker >= 0xD0 && marker <= 0xD7)) {
                i += 2;
                continue;
            }
            int len = ((d[i + 2] & 0xFF) << 8) | (d[i + 3] & 0xFF);
            if (marker >= 0xC0 && marker <= 0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC) {
                int h = ((d[i + 5] & 0xFF) << 8) | (d[i + 6] & 0xFF);
                int w = ((d[i + 7] & 0xFF) << 8) | (d[i + 8] & 0xFF);
                return new int[] { w, h };
            }
            if (len < 2) {
                return null;
            }
            i += 2 + len;
        }
        return null;
    }

    private static double luminanceVariance(BufferedImage image) {
        int w = image.getWidth();
        int h = image.getHeight();
        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 64);
        double sum = 0.0;
        double sumSq = 0.0;
        long count = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int rgb = image.getRGB(x, y);
                int r = (rgb >> 16) & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = rgb & 0xFF;
                double luma = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                sum += luma;
                sumSq += luma * luma;
                count++;
            }
        }
        if (count == 0) {
            return 0.0;
        }
        double mean = sum / count;
        return (sumSq / count) - (mean * mean);
    }
}
