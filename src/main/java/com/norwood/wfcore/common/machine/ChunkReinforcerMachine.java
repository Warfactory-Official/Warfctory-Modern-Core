package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.IControllable;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IFancyUIMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockDisplayText;

import com.lowdragmc.lowdraglib.gui.widget.ComponentPanelWidget;
import com.lowdragmc.lowdraglib.gui.widget.DraggableScrollableWidgetGroup;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.Widget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.network.chat.Component;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandlerModifiable;

import com.flansmod.warforge.api.interfaces.IChunkReinforcer;
import com.flansmod.warforge.common.WarForgeMod;
import com.flansmod.warforge.common.util.DimChunkPos;
import com.flansmod.warforge.server.Faction;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Chunk Reinforcer: a 3x3x3 food-burning multiblock. While it sits in a claimed chunk and has food fuel
 * burning, it raises the siege difficulty of every claimed chunk within {@link #reinforcementRadius} (queried
 * by WarForge through the {@code CHUNK_REINFORCER} capability exposed by {@link ChunkReinforcerBlockEntity}).
 * Radius and the defence bonus are fixed per voltage tier at registration. Only constructed when WarForge is
 * loaded, so the hard {@code com.flansmod.warforge} imports are safe.
 */
public class ChunkReinforcerMachine extends MultiblockControllerMachine
                                    implements IChunkReinforcer, IControllable, IFancyUIMachine {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            ChunkReinforcerMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    /** Ticks of burn granted per point of food nutrition restored. */
    private static final int BURN_TICKS_PER_HUNGER = 200;
    private static final int CLAIM_RECHECK_INTERVAL = 20;

    private final int tier;
    private final int reinforcementRadius;
    private final int reinforcementBonus;

    private final List<IItemHandlerModifiable> inputInventories = new ArrayList<>();

    @Persisted
    private int burnTime;
    @Persisted
    @DescSynced
    private boolean isWorkingEnabled = true;
    @DescSynced
    private boolean isActive;
    @DescSynced
    private boolean inClaimedChunk;
    @Nullable
    private TickableSubscription tickSub;
    private long tickCounter;

    public ChunkReinforcerMachine(IMachineBlockEntity holder, int tier, int radius, int bonus) {
        super(holder);
        this.tier = tier;
        this.reinforcementRadius = radius;
        this.reinforcementBonus = bonus;
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    public int getTier() {
        return tier;
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.inputInventories.clear();
        for (IMultiPart part : getParts()) {
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
    public void onStructureInvalid() {
        super.onStructureInvalid();
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
        DimChunkPos pos = new DimChunkPos(getLevel().dimension(), getPos());
        var claim = WarForgeMod.FACTIONS.getClaim(pos);
        return claim != null && !Faction.nullUuid.equals(claim);
    }

    /** Consumes one food item from an input bus, returning the burn ticks it grants (0 if none). */
    private int consumeFood() {
        for (IItemHandlerModifiable inv : inputInventories) {
            for (int slot = 0; slot < inv.getSlots(); slot++) {
                ItemStack stack = inv.getStackInSlot(slot);
                if (stack.isEmpty() || !stack.isEdible()) continue;
                FoodProperties food = stack.getFoodProperties(null);
                if (food == null || food.getNutrition() <= 0) continue;
                inv.extractItem(slot, 1, false);
                return food.getNutrition() * BURN_TICKS_PER_HUNGER;
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

    public void addDisplayText(List<Component> textList) {
        MultiblockDisplayText.builder(textList, isFormed())
                .setWorkingStatus(isWorkingEnabled, isActive)
                .addCustom(tl -> {
                    if (!isFormed()) return;
                    tl.add(Component.translatable("wfcore.machine.chunk_reinforcer.radius", reinforcementRadius));
                    tl.add(Component.translatable("wfcore.machine.chunk_reinforcer.bonus", reinforcementBonus));
                    tl.add(Component.translatable(inClaimedChunk ? "wfcore.machine.chunk_reinforcer.claimed" :
                            "wfcore.machine.chunk_reinforcer.unclaimed"));
                    tl.add(Component.translatable("wfcore.machine.chunk_reinforcer.fuel", burnTime / 20));
                })
                .addWorkingStatusLine();
    }

    @Override
    public Widget createUIWidget() {
        WidgetGroup group = new WidgetGroup(0, 0, 190, 125);
        group.addWidget(new DraggableScrollableWidgetGroup(4, 4, 182, 117).setBackground(GuiTextures.DISPLAY)
                .addWidget(new LabelWidget(4, 5, self().getBlockState().getBlock().getDescriptionId()))
                .addWidget(new ComponentPanelWidget(4, 17, this::addDisplayText).setMaxWidthLimit(150)));
        group.setBackground(GuiTextures.BACKGROUND_INVERSE);
        return group;
    }
}
