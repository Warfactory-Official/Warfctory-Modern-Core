package com.norwood.wfcore.network;

import java.io.ByteArrayOutputStream;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public final class PayloadCompression {

    private PayloadCompression() {}

    public static byte[] deflate(byte[] input) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(input);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length / 3));
        byte[] chunk = new byte[8192];
        while (!deflater.finished()) {
            out.write(chunk, 0, deflater.deflate(chunk));
        }
        deflater.end();
        return out.toByteArray();
    }

    public static byte[] inflate(byte[] input) {
        Inflater inflater = new Inflater();
        inflater.setInput(input);
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, input.length * 4));
        byte[] chunk = new byte[8192];
        try {
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    break;
                }
                out.write(chunk, 0, n);
            }
        } catch (DataFormatException e) {
            throw new IllegalStateException("wfcore: failed to inflate compressed superbwarfare data payload", e);
        } finally {
            inflater.end();
        }
        return out.toByteArray();
    }
}
