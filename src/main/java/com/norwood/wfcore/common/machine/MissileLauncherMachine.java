package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.capability.IEnergyContainer;
import com.gregtechceu.gtceu.api.capability.recipe.EURecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.IO;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IDataStickInteractable;
import com.gregtechceu.gtceu.api.machine.feature.IInteractedMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMaintenanceMachine;
import com.gregtechceu.gtceu.api.machine.feature.multiblock.IMultiPart;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.misc.EnergyContainerList;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;
import com.gregtechceu.gtceu.utils.GTUtil;

import com.lowdragmc.lowdraglib.syncdata.IContentChangeAware;
import com.lowdragmc.lowdraglib.syncdata.ITagSerializable;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import brachy.modularui.factory.BlockEntityUIFactory;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.compat.WarforgeCompat;
import com.wf.wfballistics.item.MissileItem;
import com.wf.wfballistics.item.MissilePreset;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Missile launch silo controller. Missiles are far too large for players to carry, so the silo draws them
 * from {@link MissileFactoryMachine}s linked to it with a GT Data Stick (shift-click the factory to copy its
 * position, click the silo to link; shift-click the silo to clear links). The operator picks a missile from
 * the aggregated stock of all linked factories, dials in target coordinates (typed, or picked off the chunk
 * map when WarForge is present), and Launch consumes one from the first factory holding it for an EU cost.
 * The GUI is a ModularUI (brachy fork) screen hosted by {@link MissileLauncherBlockEntity}, opened from
 * {@link #onUse} like the research unit's.
 */
public class MissileLauncherMachine extends MultiblockControllerMachine
                                    implements IInteractedMachine, IDataStickInteractable {

    /** Lowest energy-hatch tier the silo will fire at. */
    public static final int MIN_TIER = GTValues.EV;
    /** Maximum factory-to-silo link distance, blocks (checked at link time and re-checked on resolve). */
    public static final int LINK_RANGE = 128;
    /** Maximum number of factories linkable to one silo. */
    public static final int MAX_LINKS = 8;
    // EU drained per launch (a burst, not per-tick). Must fit inside a single energy hatch's buffer or it
    // can never accumulate: a 1A EV hatch holds V[EV]*64 = 131,072 EU, so keep this well under that. At
    // 100k it's a meaningful cost that a lone 1A EV+ hatch can bank between the 5s launch cooldowns.
    public static final long EU_PER_LAUNCH = 60_000L;
    /** Ticks between launches (silo doors + reload). */
    public static final int LAUNCH_COOLDOWN = 100;
    /**
     * Ticks the display missile takes to streak up from the pad to the spawn height on launch. Once it
     * reaches the top the real missile entity is spawned and the pad model hides for the rest of the cooldown.
     */
    public static final int ANIM_TICKS = 14;
    /** targetY value meaning "resolve to the world surface at launch time". */
    public static final int Y_AUTO = -10000;
    /** Blocks above the base launch pad (the 3x3 steel frame) the missile spawns / the display rises to. */
    public static final int MUZZLE_HEIGHT = 20;
    /** Blocks above the pad the display missile's base sits at rest (on top of the frame block). */
    public static final float BASE_LIFT = 1.0f;
    /** Rise speed of the display missile during launch, blocks/tick (drives the exhaust trail's direction). */
    public static final double RISE_SPEED = (MUZZLE_HEIGHT - BASE_LIFT) / (double) ANIM_TICKS;

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            MissileLauncherMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    // Positions of the linked missile factories, server-authoritative; persisted by hand in
    // saveCustomPersistedData (LDLib List<BlockPos> field serialization is unverified). Resolved lazily
    // each availability rebuild — unloaded factories are kept but contribute nothing, broken ones drop.
    protected final List<BlockPos> linkedFactories = new ArrayList<>();
    // The operator's missile pick (item registry id); synced so the GUI highlight + pad model follow it.
    @Persisted
    @DescSynced
    protected String selectedMissileId = "";
    // Snapshot of the missiles available across all linked factories ({Id, Count} list + LinkCount),
    // rebuilt server-side every AVAILABILITY_INTERVAL ticks and synced whole; both the GUI's pick-list and
    // the BER's pad model read it client-side (a panel-scoped sync could not reach the BER).
    @Persisted
    @DescSynced
    protected final AvailabilitySync availability = new AvailabilitySync();
    @Persisted
    @DescSynced
    protected int targetX;
    @Persisted
    @DescSynced
    protected int targetY = Y_AUTO;
    @Persisted
    @DescSynced
    protected int targetZ;
    @DescSynced
    protected int cooldown;
    // The missile currently being launched (registry id of the consumed item), kept for the launch
    // animation window + the delayed entity spawn. Synced so the client BER can render it streaking up the
    // silo after the slot has already been emptied. Empty string = no launch in progress.
    @Persisted
    @DescSynced
    protected String launchedMissileId = "";
    // Server-only: the real missile entity hasn't been spawned yet (the display is still animating up).
    @Persisted
    protected boolean pendingSpawn;
    // True once the operator has picked a target (typed a coord or clicked the map). Lets the map open
    // centred on the target instead of the silo. Synced so the GUI sees it the moment it opens.
    @Persisted
    @DescSynced
    protected boolean hasTarget;
    // The launch gate (tier/energy/missile/cooldown) depends on transient, server-only fields
    // (voltageTier, energyContainer) and the real inventory, so it can only be evaluated server-side.
    // Recompute it each server tick and sync the ordinal so the GUI (which renders on the client) can
    // colour the button and pick the status line without those fields.
    @DescSynced
    protected int displayState = LaunchState.UNFORMED.ordinal();

    // Stable per-silo identity stamped onto every missile it fires (its "control id"), so its own missiles
    // never intercept each other while still engaging other launchers'. Lazy-init + persisted by hand:
    // LDLib has no UUID field serializer.
    @Nullable
    protected UUID controlId;

    @Nullable
    protected EnergyContainerList energyContainer;
    /** The structure's maintenance hatch part, gathered on form; launching is blocked while it has problems. */
    @Nullable
    protected IMaintenanceMachine maintenance;
    @Nullable
    protected TickableSubscription tickSub;
    protected int voltageTier = -1;

    /** Ticks between availability snapshot rebuilds (walking linked factories is not free). */
    private static final int AVAILABILITY_INTERVAL = 10;
    private int availabilityTimer;
    // Client-side lazily parsed view of the availability snapshot (identity-cached on the tag).
    private final Map<String, Integer> clientAvailable = new LinkedHashMap<>();
    private int clientLinkCount;
    @Nullable
    private CompoundTag parsedFrom;

    public MissileLauncherMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** Interceptors are point-defense rounds, not strike weapons; everything else launches. */
    public static boolean isLaunchableMissile(ItemStack stack) {
        return stack.getItem() instanceof MissileItem missile && !missile.preset().isInterceptor();
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        List<IEnergyContainer> containers = new ArrayList<>();
        this.maintenance = null;
        for (IMultiPart part : getParts()) {
            if (part instanceof IMaintenanceMachine maintenanceMachine) {
                this.maintenance = maintenanceMachine;
            }
            for (var handlerList : part.getRecipeHandlers()) {
                if (!handlerList.isValid(IO.IN)) {
                    continue;
                }
                handlerList.getCapability(EURecipeCapability.CAP).stream()
                        .filter(IEnergyContainer.class::isInstance)
                        .map(IEnergyContainer.class::cast)
                        .forEach(containers::add);
            }
        }
        this.energyContainer = new EnergyContainerList(containers);
        this.voltageTier = GTUtil.getTierByVoltage(energyContainer.getInputVoltage());
        if (!isRemote()) {
            this.displayState = computeLaunchState().ordinal();
            tickSub = subscribeServerTick(this::tickLauncher);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = null;
        this.maintenance = null;
        this.voltageTier = -1;
        this.displayState = LaunchState.UNFORMED.ordinal();
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    protected void tickLauncher() {
        if (cooldown > 0) {
            cooldown--;
            if (maintenance != null) {
                maintenance.calculateMaintenance(maintenance); // the launch/reload cycle counts as active time
            }
            // Once the display missile has streaked up to the spawn height (ANIM_TICKS after the click),
            // release the real missile entity from the top of the silo.
            if (pendingSpawn && cooldown <= LAUNCH_COOLDOWN - ANIM_TICKS) {
                spawnLaunchedMissile();
                pendingSpawn = false;
            }
            // Cooldown finished: clear the launch so the pad shows the (next) selected missile again.
            if (cooldown == 0 && !launchedMissileId.isEmpty()) {
                launchedMissileId = "";
            }
        }
        if (++availabilityTimer >= AVAILABILITY_INTERVAL) {
            availabilityTimer = 0;
            rebuildAvailability();
        }
        this.displayState = computeLaunchState().ordinal();
    }

    //////////////////// targeting ////////////////////

    public int getTargetX() {
        return targetX;
    }

    public void setTargetX(int x) {
        this.targetX = x;
        this.hasTarget = true;
    }

    public int getTargetY() {
        return targetY;
    }

    public void setTargetY(int y) {
        this.targetY = y;
    }

    public int getTargetZ() {
        return targetZ;
    }

    public void setTargetZ(int z) {
        this.targetZ = z;
        this.hasTarget = true;
    }

    /** Whether a target has been dialed in (drives whether the map opens centred on it or on the silo). */
    public boolean hasTarget() {
        return hasTarget;
    }

    /** Map-pick entry point: sets X/Z and resets Y to auto (the client has no height data for far chunks). */
    public void setTargetXZFromMap(int blockX, int blockZ) {
        this.targetX = blockX;
        this.targetZ = blockZ;
        this.targetY = Y_AUTO;
        this.hasTarget = true;
    }

    //////////////////// factory links ////////////////////

    /**
     * Links a factory (server-side, from a data stick written by
     * {@link MissileFactoryMachine#onDataStickShiftUse}). Every failure tells the player why.
     */
    public void tryAddLink(BlockPos factoryPos, String dimId, Player player) {
        if (getLevel() == null) {
            return;
        }
        if (!getLevel().dimension().location().toString().equals(dimId)) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_wrong_dim"));
            return;
        }
        if (factoryPos.distSqr(getPos()) > (double) LINK_RANGE * LINK_RANGE) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_out_of_range", LINK_RANGE));
            return;
        }
        if (linkedFactories.contains(factoryPos)) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_duplicate"));
            return;
        }
        if (linkedFactories.size() >= MAX_LINKS) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_full", MAX_LINKS));
            return;
        }
        // Type-check when the target is loaded (it virtually always is — the player just clicked it within
        // range); an unloaded target is accepted and validated lazily on resolve.
        MetaMachine target = MetaMachine.getMachine(getLevel(), factoryPos);
        if (target != null && !(target instanceof MissileFactoryMachine)) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_invalid"));
            return;
        }
        linkedFactories.add(factoryPos.immutable());
        rebuildAvailability();
        player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_added",
                linkedFactories.size(), MAX_LINKS));
    }

    public void clearLinks(Player player) {
        linkedFactories.clear();
        rebuildAvailability();
        player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.links_cleared"));
    }

    /**
     * Live linked factories. Unloaded links are kept but skipped (never force-load); links whose block is
     * loaded but no longer a factory (broken/replaced) are dropped, so the list self-heals lazily.
     */
    protected List<MissileFactoryMachine> resolveFactories() {
        List<MissileFactoryMachine> out = new ArrayList<>();
        if (getLevel() == null) {
            return out;
        }
        Iterator<BlockPos> it = linkedFactories.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!getLevel().isLoaded(pos)) {
                continue; // temporarily unavailable; keep the link
            }
            if (MetaMachine.getMachine(getLevel(), pos) instanceof MissileFactoryMachine factory &&
                    pos.distSqr(getPos()) <= (double) LINK_RANGE * LINK_RANGE) {
                out.add(factory);
            } else {
                it.remove(); // broken, replaced, or out of range: drop the stale link
            }
        }
        return out;
    }

    //////////////////// availability (missiles stored across linked factories) ////////////////////

    /** Server: rebuild the synced snapshot; also auto-selects when the current pick vanished. */
    protected void rebuildAvailability() {
        if (isRemote()) {
            return;
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        List<MissileFactoryMachine> factories = resolveFactories();
        for (MissileFactoryMachine factory : factories) {
            factory.storedMissiles().forEach((id, count) -> merged.merge(id, count, Integer::sum));
        }
        // Auto-heal the selection: pick the first available type when none is selected or the selected
        // type ran out. Leave it empty only when nothing at all is available.
        if ((selectedMissileId.isEmpty() || !merged.containsKey(selectedMissileId)) && !merged.isEmpty()) {
            selectedMissileId = merged.keySet().iterator().next();
        }

        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        merged.forEach((id, count) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id);
            entry.putInt("Count", count);
            list.add(entry);
        });
        tag.put("Missiles", list);
        tag.putInt("LinkCount", linkedFactories.size());
        if (!tag.equals(availability.tag)) {
            availability.setTag(tag); // only on change, so @DescSynced traffic stays quiet
        }
    }

    /** Client-side lazily parsed availability (id -> count, insertion-ordered). */
    private void ensureParsed() {
        if (availability.tag == parsedFrom) {
            return;
        }
        parsedFrom = availability.tag;
        clientAvailable.clear();
        ListTag list = availability.tag.getList("Missiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            clientAvailable.put(entry.getString("Id"), entry.getInt("Count"));
        }
        clientLinkCount = availability.tag.getInt("LinkCount");
    }

    /** Available missiles by registry id (synced snapshot; safe on both sides). */
    public Map<String, Integer> getAvailableMissiles() {
        ensureParsed();
        return clientAvailable;
    }

    /** Number of linked factories (synced snapshot; safe on both sides). */
    public int getLinkCount() {
        ensureParsed();
        return clientLinkCount;
    }

    public String getSelectedMissileId() {
        return selectedMissileId;
    }

    /** Server-side (from the GUI's selection sync): picks a missile if it's in the current snapshot. */
    public void selectMissile(String missileId) {
        if (missileId != null && getAvailableMissiles().containsKey(missileId)) {
            selectedMissileId = missileId;
        }
    }

    /** Extracts one of the selected missile from the first linked factory holding it, or EMPTY. */
    protected ItemStack extractSelected() {
        if (selectedMissileId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (MissileFactoryMachine factory : resolveFactories()) {
            ItemStack taken = factory.extractMissile(selectedMissileId);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    /**
     * The selected missile for the BER's at-rest pad model: shown while at least one is available across
     * the linked factories. Client-safe (reads only synced state).
     */
    public ItemStack getDisplayStack() {
        if (selectedMissileId.isEmpty() || !getAvailableMissiles().containsKey(selectedMissileId)) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(selectedMissileId));
        return item instanceof MissileItem ? new ItemStack(item) : ItemStack.EMPTY;
    }

    /**
     * The world position of the display missile's tail (nozzle) during the launch rise, for the exhaust
     * trail to stream from; {@code null} when the silo isn't mid-launch. Uses the integer cooldown (the
     * flywheel trail bridges per-tick motion itself), so it's stable across frames.
     */
    @Nullable
    public Vec3 trailSourceWorld() {
        if (cooldown == 0 || launchedMissileId.isEmpty()) {
            return null;
        }
        int elapsed = LAUNCH_COOLDOWN - cooldown;
        if (elapsed > ANIM_TICKS) {
            return null; // missile has left the silo
        }
        float frac = Math.min(1f, elapsed / (float) ANIM_TICKS);
        float lift = BASE_LIFT + frac * (MUZZLE_HEIGHT - BASE_LIFT);
        BlockPos pad = launchPadPos();
        return new Vec3(pad.getX() + 0.5, pad.getY() + lift, pad.getZ() + 0.5);
    }

    /** The missile currently launching (for the client BER's rise animation), or EMPTY if none. */
    public ItemStack getLaunchedStack() {
        if (launchedMissileId.isEmpty()) {
            return ItemStack.EMPTY;
        }
        var item = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(launchedMissileId));
        return item instanceof MissileItem ? new ItemStack(item) : ItemStack.EMPTY;
    }

    public int getCooldown() {
        return cooldown;
    }

    //////////////////// launching ////////////////////

    /**
     * The authoritative launch gate, evaluated server-side only (it reads {@link #voltageTier},
     * {@link #energyContainer} and the real inventory, none of which exist client-side). The GUI must use
     * {@link #getDisplayState()} instead, which returns the synced result of this.
     */
    public LaunchState computeLaunchState() {
        if (!isFormed()) return LaunchState.UNFORMED;
        if (voltageTier < MIN_TIER) return LaunchState.LOW_TIER;
        if (hasMaintenanceProblems()) return LaunchState.MAINTENANCE;
        if (selectedMissileId.isEmpty() || !getAvailableMissiles().containsKey(selectedMissileId)) {
            return LaunchState.NO_MISSILE;
        }
        if (cooldown > 0) return LaunchState.COOLDOWN;
        if (!drainEnergy(true)) return LaunchState.NO_ENERGY;
        return LaunchState.READY;
    }

    /** The synced launch state, safe to read on the client (drives the GUI button colour + status line). */
    public LaunchState getDisplayState() {
        return LaunchState.values()[displayState];
    }

    /** True when a maintenance hatch is present and reporting unfixed problems (launching is blocked until fixed). */
    public boolean hasMaintenanceProblems() {
        return maintenance != null && maintenance.hasMaintenanceProblems();
    }

    /**
     * Server-side (invoked from the GUI's launch button through an interaction sync handler): pulls one of
     * the selected missile from the linked factories + {@link #EU_PER_LAUNCH} and starts the launch
     * sequence — the display missile streaks up the silo, then {@link #spawnLaunchedMissile()} releases the
     * real entity {@link #ANIM_TICKS} ticks later (see {@link #tickLauncher()}).
     */
    public void requestLaunch() {
        if (isRemote() || computeLaunchState() != LaunchState.READY ||
                !(getLevel() instanceof ServerLevel)) {
            return;
        }
        // Extract before draining energy, so a pull that races out from under us costs nothing.
        ItemStack taken = extractSelected();
        if (!(taken.getItem() instanceof MissileItem)) {
            return;
        }
        drainEnergy(false);
        launchedMissileId = BuiltInRegistries.ITEM.getKey(taken.getItem()).toString();
        pendingSpawn = true;
        cooldown = LAUNCH_COOLDOWN;
        rebuildAvailability();
    }

    /** Releases the real missile entity from the silo mouth once the display missile has risen to it. */
    private void spawnLaunchedMissile() {
        if (!(getLevel() instanceof ServerLevel serverLevel) || launchedMissileId.isEmpty()) {
            return;
        }
        if (!(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(launchedMissileId))
                instanceof MissileItem missileItem)) {
            return;
        }
        int y = targetY == Y_AUTO
                ? serverLevel.getHeight(Heightmap.Types.WORLD_SURFACE, targetX, targetZ)
                : targetY;
        MissilePreset preset = missileItem.preset();
        MissileEntity missile = preset.build(serverLevel, new Vec3(targetX + 0.5, y, targetZ + 0.5));
        missile.setControlId(controlId());
        // Own the missile for the WarForge faction claiming the silo's chunk, so friendly interceptor
        // batteries don't engage it. wfballistics' own compat no-ops safely without WarForge installed.
        missile.setTeamId(WarforgeCompat.factionClaiming(serverLevel, getPos()));

        Vec3 muzzle = muzzlePos();
        missile.moveTo(muzzle.x, muzzle.y, muzzle.z, 0.0f, 0.0f);
        serverLevel.addFreshEntity(missile);
    }

    /**
     * The launch point: {@link #MUZZLE_HEIGHT} above the launch pad, so the missile rises from the base.
     */
    private Vec3 muzzlePos() {
        return Vec3.atCenterOf(launchPadPos()).add(0.0, MUZZLE_HEIGHT, 0.0);
    }

    /**
     * The 3x3 galvanised-steel frame at the base centre (the silo's launch pad) — the geometric centre of
     * the bottom layer. Its offset from the controller is resolved through the pattern's relative directions
     * (same technique as the radar's hidden-block mapping), so it stays correct for any facing. Falls back to
     * the controller position if the controller symbol can't be located in the pattern. Also used by the
     * client BER to stand the missile model on the pad.
     */
    public BlockPos launchPadPos() {
        String[][] aisles = MissileLauncherStructure.AISLES;
        int cChar = -1, cString = -1, cAisle = -1;
        outer:
        for (int a = 0; a < aisles.length; a++) {
            for (int s = 0; s < aisles[a].length; s++) {
                int idx = aisles[a][s].indexOf('S');
                if (idx >= 0) {
                    cAisle = a;
                    cString = s;
                    cChar = idx;
                    break outer;
                }
            }
        }
        if (cChar < 0) {
            return getPos(); // controller not found; safe fallback
        }
        int padChar = aisles[0][0].length() / 2; // 9 wide -> centre char 4
        int padAisle = aisles.length / 2;         // 9 aisles -> centre aisle 4

        Direction front = getFrontFacing();
        Direction up = getUpwardsFacing();
        boolean flipped = isFlipped();
        Direction charDir = RelativeDirection.FRONT.getRelative(front, up, flipped);
        Direction stringDir = RelativeDirection.UP.getRelative(front, up, flipped);
        Direction aisleDir = RelativeDirection.RIGHT.getRelative(front, up, flipped);

        return getPos()
                .relative(charDir, padChar - cChar)
                .relative(stringDir, -cString) // pad is on the bottom layer (string 0)
                .relative(aisleDir, padAisle - cAisle);
    }

    protected boolean drainEnergy(boolean simulate) {
        if (energyContainer == null) {
            return false;
        }
        long result = energyContainer.getEnergyStored() - EU_PER_LAUNCH;
        if (result >= 0L && result <= energyContainer.getEnergyCapacity()) {
            if (!simulate) {
                energyContainer.removeEnergy(EU_PER_LAUNCH);
            }
            return true;
        }
        return false;
    }

    protected UUID controlId() {
        if (controlId == null) {
            controlId = UUID.randomUUID();
        }
        return controlId;
    }

    //////////////////// persistence (control id + factory links have no LDLib serializer) ////////////////////

    @Override
    public void saveCustomPersistedData(CompoundTag tag, boolean forDrop) {
        super.saveCustomPersistedData(tag, forDrop);
        if (controlId != null) {
            tag.putUUID("ControlId", controlId);
        }
        ListTag links = new ListTag();
        for (BlockPos pos : linkedFactories) {
            links.add(NbtUtils.writeBlockPos(pos));
        }
        tag.put("LinkedFactories", links);
    }

    @Override
    public void loadCustomPersistedData(CompoundTag tag) {
        super.loadCustomPersistedData(tag);
        if (tag.hasUUID("ControlId")) {
            controlId = tag.getUUID("ControlId");
        }
        linkedFactories.clear();
        ListTag links = tag.getList("LinkedFactories", Tag.TAG_COMPOUND);
        for (int i = 0; i < links.size(); i++) {
            linkedFactories.add(NbtUtils.readBlockPos(links.getCompound(i)));
        }
    }

    //////////////////// data stick linking (launcher side: consume the copied position) ////////////////////

    @Override
    public InteractionResult onDataStickUse(Player player, ItemStack stick) {
        if (isRemote()) {
            return InteractionResult.SUCCESS;
        }
        CompoundTag root = stick.getTag();
        if (root == null || !root.contains(MissileFactoryMachine.LINK_TAG, Tag.TAG_COMPOUND)) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_no_data"));
            return InteractionResult.SUCCESS;
        }
        CompoundTag link = root.getCompound(MissileFactoryMachine.LINK_TAG);
        tryAddLink(NbtUtils.readBlockPos(link.getCompound("Pos")), link.getString("Dim"), player);
        return InteractionResult.SUCCESS; // consume the click either way so the GUI doesn't open over it
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack stick) {
        if (!isRemote()) {
            clearLinks(player);
        }
        return InteractionResult.SUCCESS;
    }

    //////////////////// interaction (open the brachy launch GUI) ////////////////////

    @Override
    public InteractionResult onUse(BlockState blockState, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        // Shift-click is reserved for the multiblock structure preview (empty hand + unformed), so hand it
        // back to the MultiblockControllerMachine/IMultiController default instead of swallowing it — that
        // default is what renders the in-world hologram. Returning PASS here (as before) short-circuited it.
        if (player.isShiftKeyDown()) return super.onUse(blockState, level, pos, player, hand, hit);
        if (!isRemote() && getHolder().self() instanceof MissileLauncherBlockEntity) {
            // open(player, BlockPos) — NOT open(player, blockEntity): the latter verifies against the
            // client player (MCHelper.getPlayer()) and throws a dimension mismatch when called server-side.
            BlockEntityUIFactory.INSTANCE.open(player, pos);
        }
        return InteractionResult.SUCCESS;
    }

    public enum LaunchState {
        UNFORMED,
        LOW_TIER,
        NO_MISSILE,
        NO_ENERGY,
        COOLDOWN,
        READY,
        MAINTENANCE
    }

    /**
     * LDLib-syncable wrapper around the availability snapshot tag (mirrors the research unit's
     * {@code ResearchSyncData}): {@code @DescSynced} pushes the whole tag to watching clients whenever
     * {@link #setTag} fires the content-change hook.
     */
    public static final class AvailabilitySync implements ITagSerializable<CompoundTag>, IContentChangeAware {

        private CompoundTag tag = new CompoundTag();
        private Runnable onContentsChanged = () -> {};

        public void setTag(CompoundTag tag) {
            this.tag = tag;
            onContentsChanged.run();
        }

        @Override
        public CompoundTag serializeNBT() {
            return tag;
        }

        @Override
        public void deserializeNBT(CompoundTag nbt) {
            this.tag = nbt;
        }

        @Override
        public void setOnContentsChanged(Runnable onContentsChanged) {
            this.onContentsChanged = onContentsChanged;
        }

        @Override
        public Runnable getOnContentsChanged() {
            return onContentsChanged;
        }
    }
}
