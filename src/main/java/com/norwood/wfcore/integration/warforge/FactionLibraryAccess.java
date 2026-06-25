package com.norwood.wfcore.integration.warforge;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.utils.ResearchManager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;

import com.flansmod.warforge.api.WarforgeAPI;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import com.norwood.wfcore.api.research.ResearchDataItem;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;

import java.util.UUID;

/**
 * Bridges WarForge factions with WFCore's research/data systems: a machine gains access to the entire
 * blueprint library when the faction that owns its chunk keeps some loaded data storage with data inside it
 * (a research unit with stored research, or any inventory holding research data sticks) anywhere in its
 * claimed, loaded territory.
 *
 * <p>
 * Only referenced once WarForge is confirmed loaded, so the hard {@code com.flansmod.warforge} imports are
 * safe.
 */
public final class FactionLibraryAccess {

    private FactionLibraryAccess() {}

    /** Whether the faction owning the chunk at {@code pos} has qualifying data storage in its loaded claims. */
    public static boolean hasLibraryAccess(Level world, BlockPos pos) {
        if (world == null || world.isClientSide || pos == null) return false;
        UUID faction = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(world.dimension(), pos));
        if (faction == null || Faction.nullUuid.equals(faction)) return false;
        return WarforgeAPI.anyLoadedClaimedTile(faction, FactionLibraryAccess::tileHoldsData);
    }

    /**
     * Whether the faction owning the chunk at {@code pos} holds the given research - the primary unlock route
     * for research-gated recipes. A provider qualifies when it is a research unit whose state has the full
     * research path complete, or any inventory holding a data item imprinted with that research id.
     */
    public static boolean hasResearch(Level world, BlockPos pos, String researchId) {
        if (world == null || world.isClientSide || pos == null || researchId == null) return false;
        UUID faction = WarForgeMod.FACTIONS.getClaim(new DimChunkPos(world.dimension(), pos));
        if (faction == null || Faction.nullUuid.equals(faction)) return false;
        return WarforgeAPI.anyLoadedClaimedTile(faction, be -> tileHasResearch(be, researchId));
    }

    private static boolean tileHoldsData(BlockEntity be) {
        // A research unit that has stored/completed research counts as data storage with data.
        if (be instanceof IMachineBlockEntity mbe && mbe.getMetaMachine() instanceof ResearchUnitMachine unit &&
                unit.getResearchState().hasAnyData()) {
            return true;
        }
        // Any reachable inventory holding a research data stick (GregTech assembly-line or WFCore research).
        if (scanHandler(be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null))) return true;
        for (Direction side : Direction.values()) {
            if (scanHandler(be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null))) {
                return true;
            }
        }
        return false;
    }

    private static boolean scanHandler(IItemHandler inv) {
        if (inv == null) return false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty()) continue;
            if (ResearchManager.readResearchId(stack) != null) return true;
            if (stack.is(GTItems.TOOL_DATA_STICK.asItem()) && stack.hasTag() &&
                    !stack.getTag().getString(ResearchUnitMachine.STICK_KEY).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean tileHasResearch(BlockEntity be, String researchId) {
        if (be instanceof IMachineBlockEntity mbe && mbe.getMetaMachine() instanceof ResearchUnitMachine unit &&
                unit.getResearchState().isPathComplete(researchId)) {
            return true;
        }
        if (handlerHasResearch(be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElse(null),
                researchId)) {
            return true;
        }
        for (Direction side : Direction.values()) {
            if (handlerHasResearch(be.getCapability(ForgeCapabilities.ITEM_HANDLER, side).resolve().orElse(null),
                    researchId)) {
                return true;
            }
        }
        return false;
    }

    private static boolean handlerHasResearch(IItemHandler inv, String researchId) {
        if (inv == null) return false;
        for (int slot = 0; slot < inv.getSlots(); slot++) {
            if (ResearchDataItem.matches(inv.getStackInSlot(slot), researchId)) return true;
        }
        return false;
    }
}
