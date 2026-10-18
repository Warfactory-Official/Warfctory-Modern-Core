package com.norwood.wfcore.common.camera;

/**
 * The client's copy of the server's third-person rule. Locked (no F5) until a server says otherwise, so a client on a
 * server without WFCore's packet, or before its first one arrives, behaves as WFCore always has. Reset on disconnect.
 *
 * <p>Holds a flag only: no client classes, so it is safe to load on a dedicated server.
 */
public final class ThirdPersonPolicy {
    private static volatile boolean allowed;

    private ThirdPersonPolicy() {}

    public static boolean allowed() {
        return allowed;
    }

    public static void set(boolean value) {
        allowed = value;
    }

    public static void reset() {
        allowed = false;
    }

    /** Whether the camera is held in first person. A rider keeps the camera it chose, as before. */
    public static boolean forcesFirstPerson(boolean riding) {
        return !riding && !allowed;
    }
}
