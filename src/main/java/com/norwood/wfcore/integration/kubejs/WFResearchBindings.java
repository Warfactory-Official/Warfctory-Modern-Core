package com.norwood.wfcore.integration.kubejs;

import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchCategory;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchRegistry;

/**
 * KubeJS binding exposed as {@code WFResearch} in startup scripts.
 *
 * <p>
 * Add a themed tab (category):
 *
 * <pre>{@code
 * WFResearch.category('logistics')
 *     .name('My Logistics')                       // optional lang key; defaults to wfcore.research.category.logistics
 *     .icon(Item.of('minecraft:chest'))           // optional tab icon
 *     .background('minecraft:gui/...')            // optional tiled background texture
 *     .backgroundColor(0xFF101814)                // optional solid background (used if no texture)
 *     .connectorColor(0xFF60C060)                 // colour of the connector lines
 *     .register()
 * }</pre>
 *
 * <p>
 * Add a research into a category (place several roots in one category to get parallel trees on one page):
 *
 * <pre>{@code
 * WFResearch.builder('my_research')
 *     .category('logistics').pos(0, 0)
 *     .runs(4).cwuPerRun(64).itemPerRun(Item.of('minecraft:redstone', 2))
 *     .unlocks(Item.of('minecraft:repeater'))
 *     .register()
 * }</pre>
 */
public class WFResearchBindings {

    public Research.Builder builder(String id) {
        return Research.builder(id);
    }

    public ResearchCategory.Builder category(String id) {
        return ResearchCategory.builder(id);
    }

    public void remove(String id) {
        ResearchRegistry.unregister(id);
    }

    public void removeCategory(String id) {
        ResearchCategoryRegistry.unregister(id);
    }
}
