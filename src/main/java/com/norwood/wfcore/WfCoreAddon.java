package com.norwood.wfcore;

import net.minecraft.data.recipes.RecipeOutput;

import com.gregtechceu.gtceu.api.addon.GTAddon;
import com.gregtechceu.gtceu.api.addon.IGTAddon;
import com.gregtechceu.gtceu.api.registry.registrate.GTRegistrate;

import com.norwood.wfcore.common.data.VehicleFactoryRecipes;
import com.norwood.wfcore.common.data.WFCovers;
import com.norwood.wfcore.common.data.WFRecipeTypes;
import com.norwood.wfcore.common.worldgen.transplant.VeinTransplant;

/**
 * WFCore's GregTech addon entry point.
 *
 * <p>GT 8.0.0 slimmed {@link IGTAddon} down considerably:
 * <ul>
 * <li>{@code addonModId()} is gone — the mod id is the {@link GTAddon} annotation's value, and
 * {@code AddonFinder} matches it against the owning mod file, so it must equal the {@code @Mod} id.</li>
 * <li>{@code initializeAddon()} became {@link #gtInitComplete()}, which runs after GregTech has set up its
 * own content. Registering our own content here would attribute it to GregTech, so it stays empty and
 * everything registers from {@code WFCore#onRegister} instead.</li>
 * <li>{@code registerTagPrefixes()} {@code registerElements()} and {@code registerCovers()} are gone.
 * Covers move to the same {@code RegisterEvent} path as the rest.</li>
 * <li>{@code addRecipes} takes a {@link RecipeOutput} rather than a {@code Consumer<FinishedRecipe>}.</li>
 * </ul>
 */
@SuppressWarnings("unused")
@GTAddon(WFCore.MOD_ID)
public class WfCoreAddon implements IGTAddon {

    @Override
    public GTRegistrate getRegistrate() {
        return WFCore.WF_MACHINES;
    }

    @Override
    public void gtInitComplete() {
        // Covers must exist before WFItems builds their placer items; GTCovers.init() has run by now.
        WFCovers.init();
        VeinTransplant.registerLayer();
    }

    @Override
    public void addRecipes(RecipeOutput provider) {
        VehicleFactoryRecipes.addDefaultRecipes(provider);
        WFRecipeTypes.addDefaultRecipes(provider);
    }
}
