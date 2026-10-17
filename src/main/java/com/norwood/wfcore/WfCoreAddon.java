package com.norwood.wfcore;

import net.minecraft.data.recipes.RecipeOutput;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.data.WFRecipeTypes;

/** {@link GTAddon} value must equal the {@code @Mod} id: {@code AddonFinder} matches it to the mod file. */
@SuppressWarnings("unused")
@GTAddon(WFCore.MOD_ID)
public class WfCoreAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return WFCore.WF_MACHINES;
    }

    @Override
    public void addRecipes(RecipeOutput provider) {
        VehicleFactoryRecipes.addDefaultRecipes(provider);
        WFRecipeTypes.addDefaultRecipes(provider);
    }
}
