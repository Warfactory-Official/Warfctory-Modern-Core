package com.norwood.wfcore.common.cover;

import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.cover.CoverBehavior;
import com.gregtechceu.gtceu.api.cover.CoverDefinition;

import net.minecraft.core.Direction;

import org.jetbrains.annotations.NotNull;

/**
 * A tiered cooling-fan cover. Placed on a mainframe cooling fan's exposed face, it boosts that hatch's passive
 * cooling proportionally to its tier (one cover definition per voltage tier, LV..EV). The cooling component
 * reads {@link #getTier()} via {@code getCoverAtSide}.
 */
public class CoolingFanCover extends CoverBehavior {

    public final int tier;

    public CoolingFanCover(@NotNull CoverDefinition definition, @NotNull ICoverable coverable,
                           @NotNull Direction attachedSide, int tier) {
        super(definition, coverable, attachedSide);
        this.tier = tier;
    }

    public int getTier() {
        return tier;
    }
}
