package com.norwood.wfcore;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import net.minecraft.data.recipes.FinishedRecipe;

import java.util.function.Consumer;

@SuppressWarnings("unused")
@GTAddon
public class WfCoreAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return WFCore.WF_MACHINES;
    }

    @Override
    public void initializeAddon() {}

    @Override
    public String addonModId() {
        return WFCore.MOD_ID;
    }

    @Override
    public void registerTagPrefixes() {
        // CustomTagPrefixes.init();
    }

    @Override
    public void addRecipes(Consumer<FinishedRecipe> provider) {
        com.norwood.wfcore.common.data.VehicleFactoryRecipes.addDefaultRecipes(provider);
        com.norwood.wfcore.common.data.WFRecipeTypes.addDefaultRecipes(provider);
    }

    @Override
    public void registerElements() {
        // CustomElements.init();
    }

    @Override
    public void registerCovers() {
        // gtceu calls this from GTCovers.init() (its constructor) between COVERS.unfreeze() and
        // COVERS.freeze(), which is the only window the cover registry is writable. It runs before
        // WFCore's own constructor (we depend on gtceu), so WFCovers.COOLING_FANS is populated by the
        // time WFItems builds the placer items.
        com.norwood.wfcore.common.data.WFCovers.init();
    }

    // If you have custom ingredient types, uncomment this & change to match your capability.
    // KubeJS WILL REMOVE YOUR RECIPES IF THESE ARE NOT REGISTERED.
    /*
     * public static final ContentJS<Double> PRESSURE_IN = new ContentJS<>(NumberComponent.ANY_DOUBLE,
     * CustomRecipeCapabilities.PRESSURE, false);
     * public static final ContentJS<Double> PRESSURE_OUT = new ContentJS<>(NumberComponent.ANY_DOUBLE,
     * CustomRecipeCapabilities.PRESSURE, true);
     * 
     * @Override
     * public void registerRecipeKeys(KJSRecipeKeyEvent event) {
     * event.registerKey(CustomRecipeCapabilities.PRESSURE, Pair.of(PRESSURE_IN, PRESSURE_OUT));
     * }
     */
}
