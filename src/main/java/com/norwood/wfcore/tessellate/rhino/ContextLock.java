package com.norwood.wfcore.tessellate.rhino;

import java.lang.reflect.Method;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiPredicate;

/** Implemented onto {@code dev.latvian.mods.rhino.Context} by {@code rhino.ContextMixin}. */
public interface ContextLock {

    /** Held for the whole of every JS call and JS-implemented interface call; order callLock -> Context.lock. */
    ReentrantLock tessellate$callLock();

    /** Of the owning {@code ContextFactory}; null => none installed. */
    BiPredicate<Method, Runnable> tessellate$callDeferrer();
}
