package com.norwood.wfcore.mixin;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.OreVeinWorldEntry;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = OreVeinWorldEntry.class, remap = false)
public interface GTOreVeinWorldEntryAccessor {

    @Accessor("oreYield")
    void wfcore$setOreYield(int oreYield);
}
