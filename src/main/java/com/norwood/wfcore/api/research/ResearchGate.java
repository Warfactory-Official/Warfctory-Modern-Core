package com.norwood.wfcore.api.research;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiController;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;

import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import com.norwood.wfcore.integration.warforge.FactionLibraryAccess;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;

/**
 * Central check deciding whether a research-gated recipe may run at a machine. A recipe is unlocked when its
 * full research path is reachable from one of, in order:
 *
 * <ol>
 * <li>a data item (the library blueprint) carrying the research in the machine's own inventory or any of its
 * parts - the "write a recipe into a data item / NAS" route;</li>
 * <li>the primary route: the WarForge faction owning the machine's chunk keeps a research provider (research
 * unit or data bank) that holds the full research path anywhere in its loaded claims.</li>
 * </ol>
 *
 * <p>
 * Server-side only; on the client (JEI, recipe preview) it never blocks. Used by {@code ResearchRecipeCondition}
 * so the gate applies uniformly to every GregTech crafting machine (assembler, assembly line, ...).
 */
public final class ResearchGate {

    private ResearchGate() {}

    public static boolean isUnlocked(MetaMachine machine, String researchId) {
        if (machine == null || researchId == null) return true;
        Level level = machine.getLevel();
        if (level == null || level.isClientSide) return true;
        // An unknown research id (e.g. a script typo) fails open: it can never be completed, so blocking would
        // permanently brick the recipe.
        if (ResearchRegistry.get(researchId) == null) return true;
        if (machineHoldsResearch(machine, researchId)) return true;
        return WarforgeIntegration.isLoaded() &&
                FactionLibraryAccess.hasResearch(level, machine.getPos(), researchId);
    }

    /** True if a data item carrying the research sits in the machine's own inventory (or any of its parts). */
    private static boolean machineHoldsResearch(MetaMachine machine, String researchId) {
        if (scan(machine.getHolder().self(), researchId)) return true;
        if (machine instanceof IMultiController controller) {
            for (IMultiPart part : controller.getParts()) {
                if (scan(part.self().getHolder().self(), researchId)) return true;
            }
        }
        return false;
    }

    private static boolean scan(BlockEntity be, String researchId) {
        if (be == null) return false;
        if (handlerHas(be.getCapability(ForgeCapabilities.ITEM_HANDLER).resolve().orElse(null), researchId)) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (handlerHas(be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null),
                    researchId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handlerHas(IItemHandler inv, String researchId) {
        if (inv == null) return false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty() && ResearchDataItem.matches(stack, researchId)) return true;
        }
        return false;
    }
}
