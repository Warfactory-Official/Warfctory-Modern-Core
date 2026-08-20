package com.norwood.wfcore.integration.kubejs;

import com.norwood.wfcore.api.research.Research;
import com.norwood.wfcore.api.research.ResearchCategory;
import com.norwood.wfcore.api.research.ResearchCategoryRegistry;
import com.norwood.wfcore.api.research.ResearchRegistry;
import com.norwood.wfcore.common.recipe.condition.ResearchRecipeCondition;

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
 *     .nodeColor(0xFF2F6BD8)                       // optional tile tint when the node is available
 *     .runs(4).cwuPerRun(64).itemPerRun(Item.of('minecraft:redstone', 2))
 *     .fluidPerRun('minecraft:water', 1000)        // optional: mB of fluid drawn from an Import Fluid Hatch/run
 *     .unlocks(Item.of('minecraft:repeater'))
 *     .register()
 * }</pre>
 *
 * <p>
 * Give {@code fluidPerRun} a fluid registry id and an amount in mB (not {@code Fluid.of(...)}, which yields a
 * KubeJS fluid the Java builder can't take); call it several times for multiple fluids.
 *
 * <p>
 * All colours are {@code 0xAARRGGBB} (alpha first). Full-alpha literals such as {@code 0xFF60C060} are
 * supported directly — the colour setters take a {@code long} so KubeJS/Rhino doesn't reject values above
 * {@code 0x7FFFFFFF}. A bare {@code 0x60C060} renders fully transparent (alpha 0); always include the alpha byte.
 */
public class WFResearchBindings {

    public Research.Builder builder(String id) {
        return Research.builder(id);
    }

    /**
     * A recipe condition gating a GregTech recipe behind a WFCore research. Attach it to any crafting recipe
     * so the machine refuses to run until the research is unlocked:
     *
     * <pre>{@code
     * event.recipes.gtceu.assembler('gated')
     *     .inputItems(Item.of('minecraft:iron_ingot'))
     *     .outputItems(Item.of('minecraft:iron_block'))
     *     .addCondition(WFResearch.condition('my_research'))
     * }</pre>
     */
    public ResearchRecipeCondition condition(String researchId) {
        return new ResearchRecipeCondition(researchId);
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
