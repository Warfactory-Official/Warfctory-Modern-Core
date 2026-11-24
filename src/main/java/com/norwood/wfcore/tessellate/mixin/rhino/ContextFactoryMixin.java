package com.norwood.wfcore.tessellate.mixin.rhino;

import com.norwood.wfcore.tessellate.rhino.CallDeferral;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.lang.reflect.Method;
import java.util.function.BiPredicate;

@Mixin(targets = "dev.latvian.mods.rhino.ContextFactory", remap = false)
public abstract class ContextFactoryMixin implements CallDeferral {

    @Unique
    private volatile BiPredicate<Method, Runnable> tessellate$callDeferrer;

    @Override
    public BiPredicate<Method, Runnable> tessellate$callDeferrer() {
        return tessellate$callDeferrer;
    }

    @Override
    public void tessellate$setCallDeferrer(BiPredicate<Method, Runnable> deferrer) {
        tessellate$callDeferrer = deferrer;
    }
}
