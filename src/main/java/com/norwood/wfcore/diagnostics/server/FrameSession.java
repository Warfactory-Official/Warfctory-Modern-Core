package com.norwood.wfcore.diagnostics.server;

import java.util.UUID;

final class FrameSession {

    final UUID playerId;
    final String username;
    final int fbWidth;
    final int fbHeight;
    final int imgWidth;
    final int imgHeight;
    final int totalLen;
    final int chunkSize;
    final int chunkCount;
    final byte[] digest;
    final long deadlineTick;

    private final byte[] buffer;
    private final boolean[] received;
    private int receivedChunks;
    private int receivedBytes;

    FrameSession(UUID playerId, String username, int fbWidth, int fbHeight, int imgWidth, int imgHeight,
            int totalLen, int chunkSize, int chunkCount, byte[] digest, long deadlineTick) {
        this.playerId = playerId;
        this.username = username;
        this.fbWidth = fbWidth;
        this.fbHeight = fbHeight;
        this.imgWidth = imgWidth;
        this.imgHeight = imgHeight;
        this.totalLen = totalLen;
        this.chunkSize = chunkSize;
        this.chunkCount = chunkCount;
        this.digest = digest;
        this.deadlineTick = deadlineTick;
        this.buffer = new byte[totalLen];
        this.received = new boolean[chunkCount];
    }

    boolean accept(int index, byte[] data) {
        if (index < 0 || index >= chunkCount || received[index]) {
            return false;
        }
        int offset = index * chunkSize;
        int expected = Math.min(chunkSize, totalLen - offset);
        if (offset < 0 || offset > totalLen || data.length != expected) {
            return false;
        }
        System.arraycopy(data, 0, buffer, offset, expected);
        received[index] = true;
        receivedChunks++;
        receivedBytes += expected;
        return true;
    }

    boolean complete() {
        return receivedChunks == chunkCount && receivedBytes == totalLen;
    }

    byte[] data() {
        return buffer;
    }
}
