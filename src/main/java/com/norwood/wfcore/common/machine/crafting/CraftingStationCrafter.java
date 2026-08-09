package com.norwood.wfcore.common.machine.crafting;

import com.gregtechceu.gtceu.api.item.tool.GTToolType;

import net.minecraft.core.NonNullList;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

import org.jetbrains.annotations.Nullable;

import java.util.*;


public final class CraftingStationCrafter {

    public static final Set<GTToolType> BAY_TOOLS = Set.of(
            GTToolType.SAW, GTToolType.HARD_HAMMER, GTToolType.SOFT_MALLET, GTToolType.WRENCH, GTToolType.FILE,
            GTToolType.CROWBAR, GTToolType.SCREWDRIVER, GTToolType.MORTAR, GTToolType.WIRE_CUTTER, GTToolType.KNIFE);

    private static final AbstractContainerMenu DUMMY_MENU = new AbstractContainerMenu(null, -1) {
        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }
    };

    private CraftingStationCrafter() {}

    public record Match(CraftingRecipe recipe, ItemStack result, int[] injectedBaySlotForCell) {}

    private static int[] noInjection() {
        int[] a = new int[9];
        Arrays.fill(a, -1);
        return a;
    }

    private static ItemStack single(ItemStack stack) {
        return stack.copyWithCount(1);
    }

    private static CraftingContainer build(ItemStack[] cells) {
        CraftingContainer container = new TransientCraftingContainer(DUMMY_MENU, 3, 3);
        for (int i = 0; i < 9; i++) {
            container.setItem(i, cells[i] == null ? ItemStack.EMPTY : cells[i]);
        }
        return container;
    }

    @Nullable
    private static Match tryRecipe(Level level, ItemStack[] cells, int[] injection) {
        CraftingContainer container = build(cells);
        Optional<CraftingRecipe> opt = level.getRecipeManager().getRecipeFor(RecipeType.CRAFTING, container, level);
        if (opt.isEmpty()) return null;
        CraftingRecipe recipe = opt.get();
        ItemStack result = recipe.assemble(container, level.registryAccess());
        if (result.isEmpty()) return null;
        return new Match(recipe, result, injection);
    }


    @Nullable
    public static Match findMatch(Level level, ItemStack[] grid, IItemHandler toolBay) {
        return findMatch(level, grid, toolBay, null);
    }


    @Nullable
    public static Match findMatch(Level level, ItemStack[] grid, IItemHandler toolBay, int[] hint) {
        if (hint != null) {
            Match hinted = tryInjection(level, grid, toolBay, hint);
            if (hinted != null) return hinted;
        }

        Match direct = tryRecipe(level, grid, noInjection());
        if (direct != null) return direct;


        List<Integer> empties = new ArrayList<>();
        boolean anyItem = false;
        for (int i = 0; i < 9; i++) {
            if (grid[i] == null || grid[i].isEmpty()) empties.add(i);
            else anyItem = true;
        }
        if (!anyItem || empties.isEmpty()) return null;

        for (int cell : empties) {
            for (int bay = 0; bay < toolBay.getSlots(); bay++) {
                if (toolBay.getStackInSlot(bay).isEmpty()) continue;
                int[] injection = noInjection();
                injection[cell] = bay;
                Match m = tryInjection(level, grid, toolBay, injection);
                if (m != null) return m;
            }
        }
        return null;
    }

    @Nullable
    private static Match tryInjection(Level level, ItemStack[] grid, IItemHandler toolBay, int[] injection) {
        ItemStack[] cells = grid.clone();
        for (int i = 0; i < 9; i++) {
            if (injection[i] < 0) continue;
            ItemStack tool = toolBay.getStackInSlot(injection[i]);
            if (tool.isEmpty()) return null;
            cells[i] = single(tool);
        }
        return tryRecipe(level, cells, injection.clone());
    }


    public static ItemStack craftOnce(ServerPlayer player, Match match, Container grid, IItemHandlerModifiable toolBay) {
        int[] injection = match.injectedBaySlotForCell();
        ItemStack[] cells = new ItemStack[9];
        for (int i = 0; i < 9; i++) {
            cells[i] = injection[i] >= 0 ? single(toolBay.getStackInSlot(injection[i])) : grid.getItem(i);
        }
        CraftingContainer live = build(cells);

        NonNullList<ItemStack> remainders;
        ItemStack output;
        ForgeHooks.setCraftingPlayer(player);
        try {
            remainders = match.recipe().getRemainingItems(live);
            output = match.recipe().assemble(live, player.level().registryAccess());
        } finally {
            ForgeHooks.setCraftingPlayer(null);
        }

        for (int i = 0; i < 9; i++) {
            ItemStack remainder = remainders.get(i);
            if (injection[i] >= 0) {
                toolBay.setStackInSlot(injection[i], remainder.isEmpty() ? ItemStack.EMPTY : remainder);
                continue;
            }
            ItemStack current = grid.getItem(i);
            if (!current.isEmpty()) {
                current.shrink(1);
            }
            if (current.isEmpty()) {
                grid.setItem(i, remainder.isEmpty() ? ItemStack.EMPTY : remainder);
            } else if (!remainder.isEmpty()) {
                giveOrDrop(player, remainder);
            }
        }
        grid.setChanged();
        return output;
    }

    private static void giveOrDrop(ServerPlayer player, ItemStack stack) {
        if (stack.isEmpty()) return;
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }
}
