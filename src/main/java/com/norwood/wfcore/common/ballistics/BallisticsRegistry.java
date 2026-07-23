package com.norwood.wfcore.common.ballistics;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import com.norwood.wfcore.WFCore;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class BallisticsRegistry {

    private static final CopyOnWriteArrayList<BallisticsAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final ConcurrentHashMap<ResourceLocation, BallisticsAdapter> BY_ID = new ConcurrentHashMap<>();

    private BallisticsRegistry() {}

    public static void register(BallisticsAdapter a) {
        if (BY_ID.putIfAbsent(a.id(), a) == null) {
            ADAPTERS.add(a);
            WFCore.LOGGER.info("Ballistics: registered adapter {}", a.id());
        }
    }

    public static BallisticsAdapter byId(ResourceLocation id) {
        return BY_ID.get(id);
    }

    public static BallisticsAdapter find(Entity e) {

        CopyOnWriteArrayList<BallisticsAdapter> adapters = ADAPTERS;
        for (int i = 0, n = adapters.size(); i < n; i++) {
            BallisticsAdapter a = adapters.get(i);
            if (a.matches(e)) {
                return a;
            }
        }
        return null;
    }
}
