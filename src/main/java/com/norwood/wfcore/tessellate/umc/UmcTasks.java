package com.norwood.wfcore.tessellate.umc;

import java.util.List;

public final class UmcTasks {

    private UmcTasks() {
    }

    /** Single consumer: removes the head and runs it until empty, so a task added mid-drain is neither lost nor cleared. */
    public static void drain(List<Runnable> tasks) {
        for (;;) {
            Runnable task;
            try {
                task = tasks.remove(0);
            } catch (IndexOutOfBoundsException empty) {
                return;
            }
            task.run();
        }
    }
}
