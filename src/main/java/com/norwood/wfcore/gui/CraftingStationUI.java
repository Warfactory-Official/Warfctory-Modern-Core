package com.norwood.wfcore.gui;

import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.mui.GTGuiTextures;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.IItemHandlerModifiable;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.StringValue;
import brachy.modularui.value.sync.ItemSlotSyncHandler;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.slot.ItemSlot;
import brachy.modularui.widgets.slot.ModularSlot;
import brachy.modularui.widgets.slot.SlotGroup;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import com.norwood.wfcore.common.machine.crafting.CraftingStationCrafter;
import com.norwood.wfcore.common.machine.crafting.CraftingStationCrafter.Match;
import com.norwood.wfcore.common.machine.crafting.CraftingStationMachine;

/**
 * The Crafting Station's ModularUI 3 panel: a 3x3 grid that borrows tools from the tool bay, the scrollable
 * 512-slot store, and the bay itself. The title bar and the player inventory come from GregTech's
 * {@code MachineUIPanelBuilder}, so this only builds the machine's own contents.
 *
 * <p>
 * The bespoke shift-click routing of the LDLib version — tools to the bay, then the player, then the store —
 * is now expressed as slot-group shift-click priorities instead of three {@code SlotWidget} subclasses that
 * each overrode {@code slotClick}. ModularUI 3 walks the target groups in ascending priority order and skips
 * the group the item came from, and the bay only accepts tools, so the ordering below reproduces every one
 * of the old rules without any per-slot code.
 */
public final class CraftingStationUI {

    private static final int SLOT = 18;
    private static final int STORAGE_COLS = 9;
    private static final int STORAGE_ROWS = 5;
    private static final int SCROLL_W = 8;
    private static final int SEARCH_H = 12;

    // Public because the JEI transfer handler locates the station's slots by group name rather than by the
    // hardcoded menu indices the LDLib version relied on.
    public static final String GRID_GROUP = "wfcore:crafting_grid";
    public static final String RESULT_GROUP = "wfcore:crafting_result";
    public static final String BAY_GROUP = "wfcore:tool_bay";
    public static final String STORE_GROUP = "wfcore:storage";

    // Shift-click destination order. MUI3 fixes the player inventory at SlotGroup.PLAYER_INVENTORY_PRIO (0),
    // so the bay has to sit below it to win tools, and the store above it to lose to the player.
    private static final int BAY_PRIORITY = -10;
    private static final int STORE_PRIORITY = SlotGroup.STORAGE_SLOT_PRIO;

    private CraftingStationUI() {}

    public static void build(CraftingStationMachine machine, ParentWidget<?> mainWidget,
                             PanelSyncManager syncManager) {
        IItemHandlerModifiable storage = machine.getStorage();
        IItemHandlerModifiable toolBay = machine.getToolBay();

        // The 3x3 grid and the result preview are per-player and per-open: they live for as long as this
        // panel does, on whichever side built it, and are emptied back to the player when it closes.
        ItemStackHandler gridInv = new ItemStackHandler(CraftingStationMachine.GRID_SIZE);
        ItemStackHandler resultInv = new ItemStackHandler(1);

        // The grid and the result never accept shift-clicked items, only give them up, so neither is
        // registered as a transfer target (allowShiftTransfer = false).
        SlotGroup gridGroup = new SlotGroup(GRID_GROUP, 3, false);
        SlotGroup resultGroup = new SlotGroup(RESULT_GROUP, 1, false);
        SlotGroup bayGroup = new SlotGroup(BAY_GROUP, CraftingStationMachine.BAY_SIZE, BAY_PRIORITY, true);
        SlotGroup storeGroup = new SlotGroup(STORE_GROUP, STORAGE_COLS, STORE_PRIORITY, true);

        // Auto-tool selection means the preview depends on the bay as well as the grid, so both drive it.
        // The listener fires on either side; only the server holds the recipe manager result that matters.
        Runnable recompute = () -> {
            if (machine.isRemote()) return;
            updateResult(machine.getLevel(), gridInv, toolBay, resultInv);
        };
        gridGroup.addSlotChangeListener(slot -> recompute.run());
        bayGroup.addSlotChangeListener(slot -> recompute.run());

        mainWidget.child(Flow.column()
                .coverChildren()
                .childPadding(4)
                .child(Flow.row()
                        .coverChildren()
                        .childPadding(6)
                        .crossAxisAlignment(Alignment.CrossAxis.START)
                        .child(craftingSection(gridInv, resultInv, toolBay, gridGroup, resultGroup, syncManager))
                        .child(storageSection(storage, storeGroup, syncManager)))
                .child(toolBaySection(toolBay, bayGroup, syncManager)));

        syncManager.addCloseListener(player -> {
            if (machine.isRemote()) return;
            for (int i = 0; i < CraftingStationMachine.GRID_SIZE; i++) {
                ItemStack stack = gridInv.getStackInSlot(i);
                if (stack.isEmpty()) continue;
                gridInv.setStackInSlot(i, ItemStack.EMPTY);
                returnGridItem(player, stack, toolBay, storage);
            }
        });
    }

    private static Flow craftingSection(IItemHandlerModifiable gridInv, IItemHandlerModifiable resultInv,
                                        IItemHandlerModifiable toolBay, SlotGroup gridGroup,
                                        SlotGroup resultGroup, PanelSyncManager syncManager) {
        Flow grid = Flow.column().coverChildren();
        for (int row = 0; row < 3; row++) {
            Flow line = Flow.row().coverChildren();
            for (int col = 0; col < 3; col++) {
                int index = row * 3 + col;
                line.child(boundSlot(new ModularSlot(gridInv, index), gridGroup, GRID_GROUP, index, syncManager));
            }
            grid.child(line);
        }

        ItemSlot result = boundSlot(new ResultSlot(resultInv, gridInv, toolBay), resultGroup, RESULT_GROUP, 0,
                syncManager);

        return Flow.column()
                .coverChildren()
                .childPadding(2)
                .child(Text.lang("container.crafting").asWidget())
                .child(Flow.row()
                        .coverChildren()
                        .childPadding(8)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(grid)
                        .child(result));
    }

    private static Flow storageSection(IItemHandlerModifiable storage, SlotGroup storeGroup,
                                       PanelSyncManager syncManager) {
        StorageGridWidget storageGrid = new StorageGridWidget(storage, STORAGE_COLS)
                .size(STORAGE_COLS * SLOT + SCROLL_W, STORAGE_ROWS * SLOT);
        for (int i = 0; i < storage.getSlots(); i++) {
            // ignoreMaxStackSize lets a slot merge up to the handler's 512 limit instead of the item's 64,
            // which is what the LDLib version's getMaxStackSize override was for.
            storageGrid.child(boundSlot(new ModularSlot(storage, i).ignoreMaxStackSize(true), storeGroup,
                    STORE_GROUP, i, syncManager));
        }

        // Purely a render concern, so the value is a plain client-side holder rather than a sync handler:
        // the server keeps an empty query and lays the grid out unfiltered.
        String[] query = { "" };
        TextFieldWidget search = new TextFieldWidget()
                .value(new StringValue.Dynamic(() -> query[0], text -> {
                    query[0] = text;
                    storageGrid.applyFilter(text);
                }))
                .autoUpdateOnChange(true)
                .setMaxLength(48)
                .size(STORAGE_COLS * SLOT + SCROLL_W - 46, SEARCH_H)
                .tooltipStatic(tooltip -> tooltip.addLine(Text.lang("wfcore.gui.crafting_station.search")));

        return Flow.column()
                .coverChildren()
                .childPadding(2)
                .child(Flow.row()
                        .coverChildren()
                        .childPadding(4)
                        .crossAxisAlignment(Alignment.CrossAxis.CENTER)
                        .child(Text.lang("wfcore.gui.crafting_station.storage").asWidget())
                        .child(search))
                .child(storageGrid);
    }

    private static Flow toolBaySection(IItemHandlerModifiable toolBay, SlotGroup bayGroup,
                                       PanelSyncManager syncManager) {
        Flow row = Flow.row().coverChildren();
        for (int i = 0; i < toolBay.getSlots(); i++) {
            row.child(boundSlot(new ModularSlot(toolBay, i), bayGroup, BAY_GROUP, i, syncManager));
        }
        return Flow.column()
                .coverChildren()
                .childPadding(2)
                .child(Text.lang("wfcore.gui.crafting_station.tools").asWidget()
                        .tooltipStatic(tooltip -> tooltip
                                .addLine(Text.lang("wfcore.gui.crafting_station.tool_slot"))))
                .child(row);
    }

    /**
     * Wires a slot into its group and the panel's sync manager, following the same shape as GregTech's
     * {@code GTMuiMachineUtil#createSlotGroupFromInventory}.
     */
    private static ItemSlot boundSlot(ModularSlot slot, SlotGroup group, String key, int index,
                                      PanelSyncManager syncManager) {
        ItemSlotSyncHandler handler = new ItemSlotSyncHandler(slot.slotGroup(group));
        syncManager.syncValue(key, index, handler);
        return new ItemSlot().syncHandler(handler).background(GTGuiTextures.SLOT);
    }

    /** Recomputes the preview from the grid plus whatever the bay can lend it. Server side only. */
    private static void updateResult(Level level, IItemHandlerModifiable gridInv, IItemHandler toolBay,
                                     IItemHandlerModifiable resultInv) {
        Match match = CraftingStationCrafter.findMatch(level, CraftingStationCrafter.readGrid(gridInv), toolBay);
        resultInv.setStackInSlot(0, match == null ? ItemStack.EMPTY : match.result().copy());
    }

    static boolean isTool(ItemStack stack) {
        return !stack.isEmpty() && !ToolHelper.getCraftingToolTypes(stack).isEmpty();
    }

    /**
     * Empties one grid cell when the panel closes. Tools head for the tool bay first, then the player, then
     * the store; everything else goes to the player then the store; whatever is left drops at their feet.
     */
    private static void returnGridItem(Player player, ItemStack stack, IItemHandlerModifiable toolBay,
                                       IItemHandlerModifiable storage) {
        if (isTool(stack)) {
            stack = ItemHandlerHelper.insertItemStacked(toolBay, stack, false);
        }
        if (!stack.isEmpty() && player.getInventory().add(stack)) {
            return;
        }
        if (!stack.isEmpty()) {
            stack = ItemHandlerHelper.insertItemStacked(storage, stack, false);
        }
        if (!stack.isEmpty()) {
            player.drop(stack, false);
        }
    }

    /**
     * The output preview. Unlike the LDLib version there is no craft-once / craft-all fork here: ModularUI 3
     * runs slots through vanilla's click semantics, so a normal click takes one result through
     * {@link #onTake} and a shift-click makes the container loop {@code quickMoveStack} for as long as the
     * slot refills with the same item — which is exactly craft-all, and it stops on its own when the
     * ingredients run out or there is nowhere left to put the output. That also retires the old
     * 1000-iteration safety cap and the {@code hasRoomFor} pre-check.
     */
    private static final class ResultSlot extends ModularSlot {

        private final IItemHandlerModifiable resultInv;
        private final IItemHandlerModifiable gridInv;
        private final IItemHandlerModifiable toolBay;

        ResultSlot(IItemHandlerModifiable resultInv, IItemHandlerModifiable gridInv,
                   IItemHandlerModifiable toolBay) {
            super(resultInv, 0);
            this.resultInv = resultInv;
            this.gridInv = gridInv;
            this.toolBay = toolBay;
            canPut(false);
        }

        /**
         * Lies to exactly two callers, on purpose. {@code ModularContainerMenu#clicked} reimplements
         * vanilla's {@code doClick}, and its {@code ClickType.THROW} branch shrinks the slot's stack and
         * drops it <em>without</em> calling {@link #onTake} — so on a crafting result that is a free item,
         * and the preview refills the next time the grid changes. {@code hasItem} gates that branch (and
         * the double-click collect sweep) and is used nowhere else in ModularUI 3, nor by its renderer, so
         * reporting empty here makes the throw key ignore this slot and leaves everything else untouched.
         */
        @Override
        public boolean hasItem() {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack taken) {
            if (!(player instanceof ServerPlayer serverPlayer)) return;
            Match match = CraftingStationCrafter.findMatch(serverPlayer.level(),
                    CraftingStationCrafter.readGrid(gridInv), toolBay);
            if (match != null && !match.result().isEmpty()) {
                CraftingStationCrafter.craftOnce(serverPlayer, match, gridInv, toolBay);
                taken.onCraftedBy(serverPlayer.level(), serverPlayer, taken.getCount());
            }
            updateResult(serverPlayer.level(), gridInv, toolBay, resultInv);
        }

        /** Whatever a shift-click could not fit anywhere, matching {@code ModularCraftingSlot}. */
        @Override
        public void onCraftShiftClick(Player player, ItemStack leftover) {
            if (!leftover.isEmpty()) {
                player.drop(leftover, false);
            }
        }
    }
}
