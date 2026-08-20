package com.norwood.wfcore.common.ballistics;

import net.minecraft.server.level.ServerLevel;

@FunctionalInterface
public interface DeferredImpact {

    void apply(ServerLevel level);
}
