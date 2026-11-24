package com.norwood.wfcore.tessellate.hungerstrike;

/** Per-player slot for the value Hunger Strike carries from tick start to tick end. */
public interface StartHungerHolder {
    int wfcore$startHunger();

    void wfcore$startHunger(int level);
}
