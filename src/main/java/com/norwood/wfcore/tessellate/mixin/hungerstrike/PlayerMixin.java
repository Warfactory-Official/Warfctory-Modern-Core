package com.norwood.wfcore.tessellate.mixin.hungerstrike;

import com.norwood.wfcore.tessellate.hungerstrike.StartHungerHolder;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(Player.class)
public abstract class PlayerMixin implements StartHungerHolder {

    @Unique
    private int wfcore$startHunger;

    @Override
    public int wfcore$startHunger() {
        return wfcore$startHunger;
    }

    @Override
    public void wfcore$startHunger(int level) {
        wfcore$startHunger = level;
    }
}
