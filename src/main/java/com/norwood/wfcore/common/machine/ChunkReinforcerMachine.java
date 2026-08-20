package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.part.MultiblockPartMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import com.norwood.wfcore.common.mui.WFMuiWidgets;

import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandlerModifiable;

import com.flansmod.warforge.api.WarForgeCapabilities;
import com.flansmod.warforge.api.interfaces.IChunkReinforcer;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import com.norwood.wfcore.common.data.WFMachines;

/**
 * Chunk Reinforcer: a 3x3x3 food-burning multiblock. While it sits in a claimed chunk and has food fuel
 * burning, it raises the siege difficulty of every claimed chunk within {@link #reinforcementRadius} (queried
 * by WarForge through the {@code CHUNK_REINFORCER} capability exposed by {@link ChunkReinforcerBlockEntity}).
 * Radius and the defence bonus are fixed per voltage tier at registration. Only constructed when WarForge is
 * loaded, so the hard {@code com.flansmod.warforge} imports are safe.
 */
public class ChunkReinforcerMachine extends MultiblockControllerMachine
                                    implements IChunkReinforcer, IControllable, IMuiMachine {


    /** Ticks of burn granted per point of food nutrition restored. */
    private static final int BURN_TICKS_PER_HUNGER = 200;
    private static final int CLAIM_RECHECK_INTERVAL = 20;

    private final int tier;
    private final int reinforcementRadius;
    private final int reinforcementBonus;

    private final List<IItemHandlerModifiable> inputInventories = new ArrayList<>();

    @SaveField
    private int burnTime;
    @SaveField
    @SyncToClient
    private boolean isWorkingEnabled = true;
    @SyncToClient
    private boolean isActive;
    @SyncToClient
    private boolean inClaimedChunk;
    @Nullable
    private TickableSubscription tickSub;
    private long tickCounter;

    public ChunkReinforcerMachine(BlockEntityCreationInfo info, int tier, int radius, int bonus) {
        super(info);
        this.tier = tier;
        this.reinforcementRadius = radius;
        this.reinforcementBonus = bonus;
    }

    public int getTier() {
        return tier;
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        this.inputInventories.clear();
        for (MultiblockPartMachine part : getParts()) {
            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(IO.IN)) continue;
                handlerList.getCapability(ItemRecipeCapability.CAP).stream()
                        .filter(IItemHandlerModifiable.class::isInstance)
                        .map(IItemHandlerModifiable.class::cast)
                        .forEach(inputInventories::add);
            }
        }
        if (!isRemote()) {
            tickSub = subscribeServerTick(this::tickReinforcer);
        }
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
        this.inputInventories.clear();
        this.burnTime = 0;
        this.isActive = false;
        this.inClaimedChunk = false;
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    //////////////////// logic ////////////////////

    protected void tickReinforcer() {
        if (isRemote() || !isFormed()) return;

        if (tickCounter++ % CLAIM_RECHECK_INTERVAL == 0) {
            inClaimedChunk = computeClaimed();
        }

        if (!inClaimedChunk || !isWorkingEnabled) {
            burnTime = 0;
            isActive = false;
            return;
        }
        if (burnTime > 0) burnTime--;
        if (burnTime <= 0) burnTime = consumeFood();
        isActive = burnTime > 0;
    }

    private boolean computeClaimed() {
        if (getLevel() == null) return false;
        DimChunkPos pos = new DimChunkPos(getLevel().dimension(), getBlockPos());
        var claim = WarForgeMod.FACTIONS.getClaim(pos);
        return claim != null && !Faction.nullUuid.equals(claim);
    }

    /** Consumes one food item from an input bus, returning the burn ticks it grants (0 if none). */
    private int consumeFood() {
        for (IItemHandlerModifiable inv : inputInventories) {
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                // 1.21 moved food onto a data component and made FoodProperties a record, so isEdible() is
                // gone (a null lookup means "not food") and getNutrition() is now nutrition().
                FoodProperties food = stack.getFoodProperties(null);
                if (food == null || food.nutrition() <= 0) continue;
                inv.extractItem(slot, 1, false);
                return food.nutrition() * BURN_TICKS_PER_HUNGER;
            }
        }
        return 0;
    }

    //////////////////// IChunkReinforcer ////////////////////

    @Override
    public boolean isReinforcementActive() {
        return isFormed() && isActive;
    }

    @Override
    public int getReinforcementRadius() {
        return reinforcementRadius;
    }

    @Override
    public int getReinforcementBonus() {
        return reinforcementBonus;
    }

    @Override
    public boolean stacksWithOthers() {
        return false;
    }

    //////////////////// IControllable ////////////////////

    @Override
    public boolean isWorkingEnabled() {
        return isWorkingEnabled;
    }

    @Override
    public void setWorkingEnabled(boolean workingEnabled) {
        this.isWorkingEnabled = workingEnabled;
    }

    //////////////////// UI ////////////////////

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(WFMuiWidgets.displayBox(getWidgetsForDisplay(syncManager)).margin(4, 2));
    }

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = super.getWidgetsForDisplay(syncManager);

        BooleanSyncValue isFormedSync = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        // radius/bonus come from the machine definition, so both sides already agree on them; isActive,
        // isWorkingEnabled and inClaimedChunk are @SyncToClient. Only the fuel countdown needs a handler.
        IntSyncValue burnTimeSync = syncManager.getOrCreateSyncHandler("burnTime", IntSyncValue.class,
                () -> new IntSyncValue(() -> burnTime));

        widgets.add(WFMuiWidgets.unformedWarning(this, syncManager));
        widgets.add(WFMuiWidgets.formedLine(isFormedSync::getBoolValue,
                () -> Component.translatable("wfcore.machine.chunk_reinforcer.radius", reinforcementRadius)));
        widgets.add(WFMuiWidgets.formedLine(isFormedSync::getBoolValue,
                () -> Component.translatable("wfcore.machine.chunk_reinforcer.bonus", reinforcementBonus)));
        widgets.add(WFMuiWidgets.formedLine(isFormedSync::getBoolValue,
                () -> Component.translatable(inClaimedChunk ? "wfcore.machine.chunk_reinforcer.claimed" :
                        "wfcore.machine.chunk_reinforcer.unclaimed")));
        widgets.add(WFMuiWidgets.formedLine(isFormedSync::getBoolValue,
                () -> Component.translatable("wfcore.machine.chunk_reinforcer.fuel",
                        burnTimeSync.getIntValue() / 20)));
        widgets.add(WFMuiWidgets.workingStatusLine(isFormedSync::getBoolValue, () -> isWorkingEnabled,
                () -> isActive));
        return widgets;
    }

    /**
     * Merged in from the former ChunkReinforcerBlockEntity. NeoForge registers capabilities per-block
     * rather than from a BlockEntity {@code getCapability} override, and GT 8.0.0 removed the separate
     * BlockEntity class this used to live on, so the provider is registered from {@link WFMachines}.
     */
    public static void attachCapabilities(RegisterCapabilitiesEvent event, Block block) {
        event.registerBlock(WarForgeCapabilities.CHUNK_REINFORCER,
                (level, pos, state, blockEntity, side) ->
                        blockEntity instanceof ChunkReinforcerMachine machine ? machine : null,
                block);
    }
}
