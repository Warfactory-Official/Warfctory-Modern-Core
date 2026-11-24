package com.norwood.wfcore.tessellate.rhino;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

/**
 * Implemented onto {@code dev.latvian.mods.rhino.ContextFactory} by {@code rhino.ContextFactoryMixin}. The deferrer
 * returns true when it took ownership of the call and runs it later; consulted only for void interface methods.
 */
public interface CallDeferral {

    BiPredicate<Method, Runnable> tessellate$callDeferrer();

    void tessellate$setCallDeferrer(BiPredicate<Method, Runnable> deferrer);
}
