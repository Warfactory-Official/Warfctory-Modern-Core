package com.norwood.wfcore.antistall;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;



public final class PilotLink {

    private static final Set<UUID> HEARD = ConcurrentHashMap.newKeySet();

    private static final Map<UUID, Integer> SILENT_TICKS = new ConcurrentHashMap<>();

    private PilotLink() {
    }


    public static void heard(UUID playerId) {
        HEARD.add(playerId);
    }


    public static int pollSilentTicks(UUID playerId) {
        if (HEARD.remove(playerId)) {
            SILENT_TICKS.put(playerId, 0);
            return 0;
        }
        return SILENT_TICKS.merge(playerId, 1, Integer::sum);
    }

    public static int peekSilentTicks(UUID playerId) {
        Integer silent = SILENT_TICKS.get(playerId);
        return silent == null ? 0 : silent;
    }

    public static void forget(UUID playerId) {
        HEARD.remove(playerId);
        SILENT_TICKS.remove(playerId);
    }

    public static void reset() {
        HEARD.clear();
        SILENT_TICKS.clear();
    }
}
