package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.notifiable.NotifiableItemStackHandler;

import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import com.gregtechceu.gtceu.common.mui.GTByteBufAdapters;

import brachy.modularui.utils.serialization.network.IByteBufAdapter;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.GenericSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widgets.layout.Flow;

import net.minecraft.ChatFormatting;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.common.data.WFDataComponents;
import com.norwood.wfcore.common.mui.WFMuiWidgets;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Missile factory controller. Finished missiles are far too large for a player to carry, so recipes
 * complete into {@link #missileStore} — a controller-owned {@code IO.OUT} handler with no export bus, GUI
 * slot or player-reachable capability (the vehicle factory's {@code vehicleOutput} pattern) — and only a
 * linked Missile Launch Silo can draw them out ({@link #extractMissile}). Shift right-click with a GT Data
 * Stick copies this factory's position onto the stick; using that stick on a silo links the two.
 */
public class MissileFactoryMachine extends WorkableElectricMultiblockMachine implements IDataStickInteractable {

    // The position + dimension a data stick carries for launcher linking now lives in the
    // WFDataComponents.MISSILE_FACTORY_LINK component; 1.21 removed the free-form item NBT this used.
    /** Core storage slots; missiles stack to 16, so 4 slots = up to 64 stored missiles. */
    public static final int STORAGE_SLOTS = 4;


    @SaveField
    protected final NotifiableItemStackHandler missileStore;

    public MissileFactoryMachine(BlockEntityCreationInfo info) {
        super(info);
        // handlerIO = OUT so recipes deposit finished missiles here; capabilityIO = NONE so the store is
        // NOT exposed to the world — hoppers/pipes under the controller can't pull missiles out. Only a
        // linked launcher reaches in via direct storage access (extractMissile / getStackInSlot).
        this.missileStore = attachTrait(new NotifiableItemStackHandler(STORAGE_SLOTS, IO.OUT, IO.NONE));
    }

    //////////////////// core storage (read by linked launchers) ////////////////////

    /** Stored launchable (strike) missiles by item registry id — what a linked Missile Launch Silo may draw. */
    public Map<String, Integer> storedMissiles() {
        return stored(MissileLauncherMachine::isLaunchableMissile);
    }

    /** Stored interceptor missiles by item registry id — what a linked Interceptor Battery may draw. */
    public Map<String, Integer> storedInterceptors() {
        return stored(InterceptorMachine::isInterceptorMissile);
    }

    /** Every stored missile (strike + interceptor) by item registry id, for the factory's own readout. */
    public Map<String, Integer> storedAll() {
        return stored(stack -> stack.getItem() instanceof com.wf.wflib.item.MissileItem);
    }

    private Map<String, Integer> stored(java.util.function.Predicate<ItemStack> filter) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int slot = 0; slot < missileStore.getSlots(); slot++) {
            ItemStack stack = missileStore.getStackInSlot(slot);
            if (!stack.isEmpty() && filter.test(stack)) {
                out.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(),
                        Integer::sum);
            }
        }
        return out;
    }

    /** Total missiles currently in the core, and its capacity (slots x 16). */
    public int storedCount() {
        int total = 0;
        for (int slot = 0; slot < missileStore.getSlots(); slot++) {
            total += missileStore.getStackInSlot(slot).getCount();
        }
        return total;
    }

    public int storageCapacity() {
        return STORAGE_SLOTS * 16; // missiles stack to 16
    }

    /** Extracts one launchable missile of the given registry id from the core, or EMPTY if none is stored. */
    public ItemStack extractMissile(String missileId) {
        return extract(missileId, MissileLauncherMachine::isLaunchableMissile);
    }

    /** Extracts one interceptor missile of the given registry id from the core, or EMPTY if none is stored. */
    public ItemStack extractInterceptor(String missileId) {
        return extract(missileId, InterceptorMachine::isInterceptorMissile);
    }

    private ItemStack extract(String missileId, java.util.function.Predicate<ItemStack> filter) {
        for (int slot = 0; slot < missileStore.getSlots(); slot++) {
            ItemStack stack = missileStore.getStackInSlot(slot);
            if (!stack.isEmpty() && filter.test(stack) &&
                    BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(missileId)) {
                return missileStore.storage.extractItem(slot, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    //////////////////// multiblock display (stored-missile readout) ////////////////////

    /** How many stored-missile rows the readout can show; one per distinct missile item. */
    private static final int MAX_STORED_LINES = 16;

    /** GregTech ships no CompoundTag adapter, but its factory is public and CompoundTag has a codec. */
    private static final IByteBufAdapter<FriendlyByteBuf, CompoundTag> TAG_ADAPTER =
            GTByteBufAdapters.makeAdapter(CompoundTag.CODEC);

    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = super.getWidgetsForDisplay(syncManager);

        BooleanSyncValue isFormedSync = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));
        IntSyncValue storedSync = syncManager.getOrCreateSyncHandler("storedCount", IntSyncValue.class,
                () -> new IntSyncValue(this::storedCount));
        GenericSyncValue<FriendlyByteBuf, CompoundTag> storedByIdSync = syncManager.getOrCreateSyncHandler(
                "storedById", GenericSyncValue.class,
                () -> GenericSyncValue.<FriendlyByteBuf, CompoundTag>builder(CompoundTag.class)
                        .adapter(TAG_ADAPTER)
                        .getter(this::storedAllAsTag)
                        .build());

        // WorkableMultiblockMachine appends exactly two recipe-failure widgets last: the
        // "gtceu.recipe_logic.setup_fail" header and its reason bullet. A full core makes recipes fail on
        // output insertion, which GT reports as the cryptic "Insufficient Outputs: Item", so those two are
        // moved into a group that hides itself while the core is full and the plain reason is shown instead.
        // The LDLib version did this by string-matching the finished component list.
        List<IWidget> failLines = new ArrayList<>(widgets.subList(widgets.size() - 2, widgets.size()));
        widgets.subList(widgets.size() - 2, widgets.size()).clear();
        Flow failGroup = Flow.column().coverChildren().collapseDisabledChildren();
        failLines.forEach(failGroup::child);
        widgets.add(failGroup.setEnabledIf($ -> storedSync.getIntValue() < storageCapacity()));

        widgets.add(WFMuiWidgets.conditionalLine(
                () -> isFormedSync.getBoolValue() && storedSync.getIntValue() >= storageCapacity(),
                () -> Component.translatable("wfcore.machine.missile_factory.full")
                        .withStyle(ChatFormatting.RED)));
        widgets.add(WFMuiWidgets.formedLine(isFormedSync::getBoolValue,
                () -> Component.translatable("wfcore.machine.missile_factory.stored",
                        storedSync.getIntValue(), storageCapacity()).withStyle(ChatFormatting.GOLD)));
        for (int i = 0; i < MAX_STORED_LINES; i++) {
            final int index = i;
            widgets.add(WFMuiWidgets.conditionalLine(
                    () -> isFormedSync.getBoolValue() && index < storedByIdSync.getValue().size(),
                    () -> storedLine(storedByIdSync.getValue(), index)));
        }
        return widgets;
    }

    /**
     * The stored-missile map as a tag, so it can ride one sync handler. {@link #storedAll} walks the store
     * server-side; the client only ever sees this snapshot.
     */
    private CompoundTag storedAllAsTag() {
        CompoundTag tag = new CompoundTag();
        storedAll().forEach(tag::putInt);
        return tag;
    }

    /** One {@code " - <item> xN"} row of the stored-missile readout. */
    private static Component storedLine(CompoundTag stored, int index) {
        List<String> keys = new ArrayList<>(stored.getAllKeys());
        if (index >= keys.size()) return Component.empty();
        String id = keys.get(index);
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id));
        return Component.literal(" - ").append(item.getDescription())
                .append(Component.literal(" x" + stored.getInt(id)));
    }

    //////////////////// data stick linking (factory side: copy position) ////////////////////

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack stick) {
        if (isRemote() || getLevel() == null) {
            return InteractionResult.SUCCESS;
        }
        stick.set(WFDataComponents.MISSILE_FACTORY_LINK,
                GlobalPos.of(getLevel().dimension(), getBlockPos()));
        player.sendSystemMessage(Component.translatable("wfcore.machine.missile_factory.link_copied",
                getBlockPos().getX(), getBlockPos().getY(), getBlockPos().getZ()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack stick) {
        return InteractionResult.PASS;
    }
}
