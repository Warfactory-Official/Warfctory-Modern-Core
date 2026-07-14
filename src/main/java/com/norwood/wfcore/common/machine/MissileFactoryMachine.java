package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableElectricMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.NotifiableItemStackHandler;

import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.LiteralContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Missile factory controller. Finished missiles are far too large for a player to carry, so recipes
 * complete into {@link #missileStore} — a controller-owned {@code IO.OUT} handler with no export bus, GUI
 * slot or player-reachable capability (the vehicle factory's {@code vehicleOutput} pattern) — and only a
 * linked Missile Launch Silo can draw them out ({@link #extractMissile}). Shift right-click with a GT Data
 * Stick copies this factory's position onto the stick; using that stick on a silo links the two.
 */
public class MissileFactoryMachine extends WorkableElectricMultiblockMachine implements IDataStickInteractable {

    /** NBT key on a data stick carrying this factory's position + dimension for launcher linking. */
    public static final String LINK_TAG = "wfcore.missile_factory_link";
    /** Core storage slots; missiles stack to 16, so 4 slots = up to 64 stored missiles. */
    public static final int STORAGE_SLOTS = 4;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MissileFactoryMachine.class, WorkableElectricMultiblockMachine.MANAGED_FIELD_HOLDER);

    @Persisted
    protected final NotifiableItemStackHandler missileStore;

    public MissileFactoryMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
        // handlerIO = OUT so recipes deposit finished missiles here; capabilityIO = NONE so the store is
        // NOT exposed to the world — hoppers/pipes under the controller can't pull missiles out. Only a
        // linked launcher reaches in via direct storage access (extractMissile / getStackInSlot).
        this.missileStore = new NotifiableItemStackHandler(this, STORAGE_SLOTS, IO.OUT, IO.NONE);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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
        return stored(stack -> stack.getItem() instanceof com.wf.wfballistics.item.MissileItem);
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

    @Override
    public void addDisplayText(java.util.List<Component> textList) {
        super.addDisplayText(textList); // keep the stock energy/recipe/progress lines
        if (!isFormed()) {
            return;
        }
        boolean full = storedCount() >= storageCapacity();
        if (full) {
            // A full core makes recipes fail on output insertion, which GT reports as the cryptic
            // "Fail to setup recipe: Insufficient Outputs: Item". Strip that and say plainly what's wrong.
            removeRecipeFailLines(textList);
            textList.add(Component.translatable("wfcore.machine.missile_factory.full")
                    .withStyle(ChatFormatting.RED));
        }
        textList.add(Component.translatable("wfcore.machine.missile_factory.stored",
                storedCount(), storageCapacity())
                .withStyle(ChatFormatting.GOLD));
        storedAll().forEach((id, count) -> {
            var item = BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.tryParse(id));
            textList.add(Component.literal(" - ").append(item.getDescription())
                    .append(Component.literal(" x" + count)));
        });
    }

    /**
     * Removes GT's "Fail to setup recipe:" header ({@code gtceu.recipe_logic.setup_fail}) and the reason
     * bullets that follow it (each a {@code " - "} literal added by {@code addRecipeFailReasonLine}) from the
     * display list, so a full-core stall doesn't show the misleading generic output-insufficient reason.
     */
    private static void removeRecipeFailLines(java.util.List<Component> textList) {
        int idx = -1;
        for (int i = 0; i < textList.size(); i++) {
            if (textList.get(i).getContents() instanceof TranslatableContents tc
                    && "gtceu.recipe_logic.setup_fail".equals(tc.getKey())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            return;
        }
        textList.remove(idx); // the "Fail to setup recipe:" header
        while (idx < textList.size() && isReasonBullet(textList.get(idx))) {
            textList.remove(idx); // its indented reason lines
        }
    }

    /** A recipe-failure reason bullet: a component whose own contents are the literal {@code " - "} prefix. */
    private static boolean isReasonBullet(Component component) {
        return component.getContents() instanceof LiteralContents lc && lc.text().stripLeading().startsWith("-");
    }

    //////////////////// data stick linking (factory side: copy position) ////////////////////

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack stick) {
        if (isRemote() || getLevel() == null) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag link = new CompoundTag();
        link.put("Pos", NbtUtils.writeBlockPos(getPos()));
        link.putString("Dim", getLevel().dimension().location().toString());
        stick.getOrCreateTag().put(LINK_TAG, link);
        player.sendSystemMessage(Component.translatable("wfcore.machine.missile_factory.link_copied",
                getPos().getX(), getPos().getY(), getPos().getZ()));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack stick) {
        return InteractionResult.PASS;
    }
}
