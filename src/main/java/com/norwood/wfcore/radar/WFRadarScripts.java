package com.norwood.wfcore.radar;

import java.util.ArrayList;
import java.util.List;


public final class WFRadarScripts {

    private static final List<Runnable> PENDING = new ArrayList<>();
    private static boolean applied = false;

    private WFRadarScripts() {}

    public static synchronized void enqueue(Runnable op) {
        if (op == null) {
            return;
        }
        if (applied) {
            op.run();
        } else {
            PENDING.add(op);
        }
    }

    public static synchronized void apply() {
        applied = true;
        for (Runnable op : PENDING) {
            op.run();
        }
        PENDING.clear();
    }
}
