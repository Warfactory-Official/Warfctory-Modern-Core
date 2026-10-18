package com.norwood.wfcore.client;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Flashback replay state, for hiding overlays that do not know about replays. Flashback is optional, so this is
 * reflective; absent or failing => not a replay.
 */
public final class FlashbackReplay {
    private static final MethodHandle IN_REPLAY = resolve();

    private FlashbackReplay() {}

    /** A Flashback replay is playing or exporting. */
    public static boolean active() {
        if (IN_REPLAY == null) {
            return false;
        }
        try {
            return (boolean) IN_REPLAY.invokeExact();
        } catch (Throwable t) {
            return false;
        }
    }

    private static MethodHandle resolve() {
        try {
            return MethodHandles.publicLookup().findStatic(Class.forName("com.moulberry.flashback.Flashback"),
                    "isInReplay", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }
}
