package com.norwood.wfcore.tessellate.hungerstrike;

import net.minecraft.world.entity.player.Player;

import java.util.AbstractMap;
import java.util.Map;
import java.util.Set;

/** Stands in for Hunger Strike's static {@code Map<Player,Integer>}; only the three calls it makes are supported. */
public final class StartHungerView extends AbstractMap<Player, Integer> {

    public static final StartHungerView INSTANCE = new StartHungerView();

    private StartHungerView() {
    }

    @Override
    public boolean containsKey(Object player) {
        return true;
    }

    @Override
    public Integer put(Player player, Integer level) {
        ((StartHungerHolder) player).wfcore$startHunger(level);
        return null;
    }

    @Override
    public Integer getOrDefault(Object player, Integer fallback) {
        return ((StartHungerHolder) player).wfcore$startHunger();
    }

    @Override
    public Set<Entry<Player, Integer>> entrySet() {
        throw new UnsupportedOperationException();
    }
}
