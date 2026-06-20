package com.norwood.wfcore.integration.kubejs;

import com.norwood.wfcore.api.research.Research;

/**
 * KubeJS binding exposed as {@code WFResearch} in startup scripts. Lets packs add researches:
 * {@code WFResearch.builder('my_research').runs(4).cwuPerRun(64).itemPerRun(Item.of('minecraft:redstone', 2))
 * .unlocks(Item.of('minecraft:repeater')).register()}.
 */
public class WFResearchBindings {

    public Research.Builder builder(String id) {
        return Research.builder(id);
    }

    public void remove(String id) {
        com.norwood.wfcore.api.research.ResearchRegistry.unregister(id);
    }
}
