package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.common.block.WFBlockResistances;

/**
 * KubeJS binding exposed as {@code WFBlocks} in startup scripts. Overrides are staged immediately and applied
 * to the real block once every mod's blocks are registered. Any block left with a blast resistance over 30
 * (vanilla or overridden) automatically gets a golden tooltip showing the value.
 *
 * <pre>{@code
 * // startup_scripts: raise cobblestone's blast resistance so it gets the golden tooltip too
 * WFBlocks.setResistance('minecraft:cobblestone', 60)
 * }</pre>
 */
public class WFBlockBindings {

    public void setResistance(String blockId, float resistance) {
        ResourceLocation id = ResourceLocation.tryParse(blockId);
        if (id == null) throw new IllegalArgumentException("Invalid block id: " + blockId);
        WFBlockResistances.set(id, resistance);
    }
}
