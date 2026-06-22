package com.norwood.wfcore.mixin;

import com.gregtechceu.gtceu.api.capability.IDataAccessHatch;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.common.machine.multiblock.electric.AssemblyLineMachine;
import com.gregtechceu.gtceu.common.machine.multiblock.part.DataAccessHatchMachine;
import com.gregtechceu.gtceu.common.recipe.condition.ResearchCondition;

import com.norwood.wfcore.integration.warforge.FactionLibraryAccess;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;

/**
 * Grants an Assembly Line full research/blueprint access (as if every data stick were present) when the
 * faction owning its chunk keeps loaded data storage with data inside its claimed territory. Mirrors the
 * 1.12.2 Assembly Line mixin: in modern GregTech the research gate is the Data Access Hatch's
 * {@link IDataAccessHatch#isRecipeAvailable}, so we satisfy it here for research-gated recipes only,
 * leaving every other recipe check untouched. No-op unless WarForge is loaded.
 */
@Mixin(value = DataAccessHatchMachine.class, remap = false)
public abstract class DataAccessHatchResearchShareMixin {

    @Unique
    private long wfcore$libCacheTick = Long.MIN_VALUE;
    @Unique
    private boolean wfcore$libCacheValue = false;

    @Inject(method = "isRecipeAvailable", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$grantFactionLibrary(GTRecipe recipe, Collection<IDataAccessHatch> seen,
                                            CallbackInfoReturnable<Boolean> cir) {
        if (recipe.conditions.stream().noneMatch(ResearchCondition.class::isInstance)) return;
        if (!WarforgeIntegration.isLoaded()) return;

        DataAccessHatchMachine self = (DataAccessHatchMachine) (Object) this;
        if (self.getLevel() == null || self.getLevel().isClientSide) return;

        boolean servesAssemblyLine = false;
        for (IMultiController controller : self.getControllers()) {
            if (controller instanceof AssemblyLineMachine) {
                servesAssemblyLine = true;
                break;
            }
        }
        if (!servesAssemblyLine) return;

        // The faction scan walks every block entity in all loaded claims, so cache it for 60 ticks.
        long now = self.getLevel().getGameTime();
        if (wfcore$libCacheTick == Long.MIN_VALUE || now - wfcore$libCacheTick >= 60L) {
            wfcore$libCacheTick = now;
            wfcore$libCacheValue = FactionLibraryAccess.hasLibraryAccess(self.getLevel(), self.getPos());
        }
        if (wfcore$libCacheValue) {
            cir.setReturnValue(true);
        }
    }
}
