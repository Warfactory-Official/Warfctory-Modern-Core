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

import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.ChatFormatting;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import brachy.modularui.factory.BlockEntityUIFactory;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.common.data.LoiterUntilDryStage;
import com.norwood.wfcore.common.data.WFMissiles;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.norwood.wfcore.integration.warforge.FactionNotifier;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.compat.WarforgeCompat;
import com.wf.wfballistics.flight.FlightStageRegistry;
import com.wf.wfballistics.item.MissileItem;
import com.wf.wfballistics.item.MissilePreset;
import com.wf.wfballistics.sim.MissileSimConfig;
import com.wf.wfballistics.warhead.WarheadRegistry;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Interceptor battery controller. A point-defense installation that automatically engages incoming hostile
 * missiles: each cycle it scans a {@link #DETECT_RANGE}-block sphere for enemy {@link MissileEntity}s (any
 * missile whose team isn't friendly to the faction claiming the battery's chunk and that isn't one of its own
 * rounds), picks the nearest un-claimed, detectable one, and fires an interceptor missile locked onto it for
 * an EU cost. Interceptor missiles are drawn from {@link MissileFactoryMachine}s linked with a GT Data Stick
 * (shift-click a factory to copy its position, click the battery to link; shift-click the battery to clear) —
 * the same "missiles are too large to carry" premise as the Missile Launch Silo. The engage/launch algorithm
 * mirrors WF-Ballistics' own {@code TurretInterceptorBlockEntity}. A small status GUI (hosted by
 * {@link InterceptorBlockEntity}) shows links, ammo and state.
 */
public class InterceptorMachine extends MultiblockControllerMachine
                                implements IInteractedMachine, IDataStickInteractable, IAnimatedMachine {

    /** Lowest energy-hatch tier the battery will operate at. */
    public static final int MIN_TIER = GTValues.EV;
    /** Maximum factory-to-battery link distance, blocks (checked at link time and re-checked on resolve). */
    public static final int LINK_RANGE = 128;
    /** Maximum number of factories linkable to one battery. */
    public static final int MAX_LINKS = 8;
    /** EU drained per interception (a burst; must fit inside a single EV+ energy hatch's buffer). */
    public static final long EU_PER_INTERCEPT = 40_000L;
    /** Ticks between interception attempts (matches WF-Ballistics' turret FIRE_INTERVAL). */
    public static final int FIRE_COOLDOWN = 40;
    /** Engagement radius in blocks (matches WF-Ballistics' turret RANGE). */
    public static final double DETECT_RANGE = 200.0;
    /** Blocks above the controller the interceptor is released from. */
    public static final int MUZZLE_HEIGHT = 6;
    /** Ticks between refreshes of the cached owning-faction id. */
    private static final int TEAM_REFRESH_INTERVAL = 100;
    /** Ticks the dome takes to fully deploy (or retract): the forward/backward animation play time. */
    private static final int DEPLOY_TICKS = 12;
    /** Ticks the dome stays deployed after the last engageable target before it retracts. */
    private static final int DEPLOY_HOLD = 60;
    /** Ticks between target scans while operational (deploy/retract responsiveness vs entity-scan cost). */
    private static final int SCAN_INTERVAL = 5;
    /** Minimum ticks between team warnings, so a sustained attack doesn't spam the faction (10s). */
    private static final int WARN_INTERVAL = 200;

    // ---- best-fit interceptor selection ----
    /** Estimated single-shot kill probability the auto-picker treats as "good enough" (a confident kill). At or
     *  above it, the CHEAPEST clearing round is taken (conserve premium interceptors); below it for every round,
     *  the highest-odds shot available is taken instead. */
    private static final double KILL_FLOOR = 0.6;
    /** Mirror of {@code MissileEntity.BOOST_SPEED_MULT} (private there): an evading target boosts to ~2x its
     *  cruise speed, so the auto-picker weighs an interceptor's speed against that boosted figure, not cruise. */
    private static final double TARGET_BOOST_SPEED_MULT = 2.0;
    /** WF-B's inert warhead id — an inert round is transparently harmless, so the auto-picker ignores it (the
     *  {@link com.norwood.wfcore.common.data.WFMissiles#DUMMY dummy}) unless it's a loitering decoy (below). */
    private static final ResourceLocation INERT_WARHEAD_ID = WarheadRegistry.rl("inert");
    /** The loiter-until-dry cruise stage id ({@link com.norwood.wfcore.common.data.WFMissiles#LOITER_DRONE}):
     *  an inert missile flying it reads as a live loitering munition, so the battery deliberately engages it —
     *  wasting a round on the decoy is exactly what it's for. */
    private static final ResourceLocation LOITER_STAGE_ID = FlightStageRegistry.rl(LoiterUntilDryStage.ID);

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            InterceptorMachine.class, MultiblockControllerMachine.MANAGED_FIELD_HOLDER);

    // Positions of the linked missile factories, server-authoritative; persisted by hand (see the silo).
    protected final List<BlockPos> linkedFactories = new ArrayList<>();
    // Synced readouts for the status GUI (linkedFactories itself is server-only).
    @DescSynced
    protected int linkCount;
    @DescSynced
    protected int cooldown;
    @DescSynced
    protected int displayState = State.UNFORMED.ordinal();
    // Dome deploy state: ramps 0 -> 1 while engaging (fires only once fully deployed) and 1 -> 0 after the
    // hold window with no target. Synced so the client renderer drives the iron dome's open/close animation
    // directly ({@link #getAnimationOverride()}). deployTimer is the server-side retract countdown.
    @DescSynced
    protected float deployProgress;
    protected int deployTimer;
    protected int scanTimer;
    protected int warnCooldown;
    // The operator's chosen interceptor type (item registry id); the battery fires only this one. Synced so
    // the GUI highlight follows it; auto-healed to the first available type when empty or sold out.
    @Persisted
    @DescSynced
    protected String selectedInterceptorId = "";
    // When true (default), the battery ignores the manual pick and fires the best-fit interceptor for each
    // incoming missile ({@link #attemptBestFit}); when false, it fires the operator's {@link #selectedInterceptorId}.
    // Synced so the GUI toggle reflects it; persisted so the mode survives a reload.
    @Persisted
    @DescSynced
    protected boolean autoBestFit = true;
    // Snapshot of the interceptors available across all linked factories ({Id, Count} list), rebuilt
    // server-side every AVAILABILITY_INTERVAL ticks and synced whole for the GUI pick-list (reuses the silo's
    // syncable tag wrapper). Client-side lazily parsed into {@link #clientAvailable}.
    @Persisted
    @DescSynced
    protected final MissileLauncherMachine.AvailabilitySync availability = new MissileLauncherMachine.AvailabilitySync();

    /** Ticks between availability snapshot rebuilds (walking linked factories is not free). */
    private static final int AVAILABILITY_INTERVAL = 10;
    private int availabilityTimer;
    private final Map<String, Integer> clientAvailable = new LinkedHashMap<>();
    @Nullable
    private CompoundTag parsedFrom;

    // Stable per-battery identity stamped onto every interceptor it fires (its "control id"), so it never
    // engages its own rounds. Lazy-init + persisted by hand (LDLib has no UUID field serializer).
    @Nullable
    protected UUID controlId;
    // The faction claiming this battery's chunk; interceptors inherit it and friendly missiles are ignored.
    @Nullable
    protected UUID cachedTeamId;
    protected int teamRefresh;

    @Nullable
    protected EnergyContainerList energyContainer;
    /** The structure's maintenance hatch part, gathered on form; the battery stands down while it has problems. */
    @Nullable
    protected IMaintenanceMachine maintenance;
    @Nullable
    protected TickableSubscription tickSub;
    protected int voltageTier = -1;

    public InterceptorMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    /** Interceptors are the point-defense rounds this battery fires (the inverse of the silo's filter). */
    public static boolean isInterceptorMissile(ItemStack stack) {
        return stack.getItem() instanceof MissileItem missile && missile.preset().isInterceptor();
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
            this.displayState = computeState().ordinal();
            tickSub = subscribeServerTick(this::tickBattery);
        }
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.energyContainer = null;
        this.maintenance = null;
        this.voltageTier = -1;
        this.displayState = State.UNFORMED.ordinal();
        this.deployProgress = 0f;
        this.deployTimer = 0;
        if (tickSub != null) {
            tickSub.unsubscribe();
            tickSub = null;
        }
    }

    protected void tickBattery() {
        if (isRemote() || !(getLevel() instanceof ServerLevel level)) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        if (++teamRefresh >= TEAM_REFRESH_INTERVAL || cachedTeamId == null) {
            teamRefresh = 0;
            cachedTeamId = WarforgeCompat.factionClaiming(level, getPos());
        }
        this.linkCount = linkedFactories.size();
        if (++availabilityTimer >= AVAILABILITY_INTERVAL) {
            availabilityTimer = 0;
            rebuildAvailability();
        }
        this.displayState = computeState().ordinal();

        // Operational = formed, powered, serviced, and a stocked interceptor selected (auto-healed by
        // rebuildAvailability to a stocked type, so this is non-empty whenever any interceptor is available).
        boolean operational = isFormed() && voltageTier >= MIN_TIER && !hasMaintenanceProblems()
                && getAvailableInterceptors().containsKey(selectedInterceptorId) && drainEnergy(true);
        if (operational && maintenance != null) {
            maintenance.calculateMaintenance(maintenance); // armed + watching counts as active time -> problems
        }

        // Scan for a threat periodically; a live target refreshes the deploy hold so the dome stays open.
        MissileEntity target = null;
        if (operational && ++scanTimer >= SCAN_INTERVAL) {
            scanTimer = 0;
            target = acquireTarget(level);
            if (target != null) {
                deployTimer = DEPLOY_HOLD;
                // Warn the whole owning faction that a missile is inbound (throttled, WarForge-gated).
                if (warnCooldown <= 0 && WarforgeIntegration.isLoaded() && cachedTeamId != null) {
                    FactionNotifier.warnIncomingMissile(cachedTeamId, getPos());
                    warnCooldown = WARN_INTERVAL;
                }
            }
        }
        if (deployTimer > 0) {
            deployTimer--;
        }
        if (warnCooldown > 0) {
            warnCooldown--;
        }

        // Ramp the dome open while engaging, closed otherwise. It only fires once fully deployed, and it
        // retracts once the hold window elapses with no target ("play backwards to the start frame").
        float step = 1f / DEPLOY_TICKS;
        deployProgress = deployTimer > 0
                ? Math.min(1f, deployProgress + step)
                : Math.max(0f, deployProgress - step);

        if (target != null && cooldown <= 0 && deployProgress >= 1f) {
            fire(level, target);
        }
    }

    //////////////////// targeting + firing (mirrors TurretInterceptorBlockEntity) ////////////////////

    private Vec3 center() {
        return Vec3.atCenterOf(getPos());
    }

    /** Nearest un-claimed, detectable hostile missile within range, or null when the sky is clear. */
    @Nullable
    private MissileEntity acquireTarget(ServerLevel level) {
        Vec3 center = center();
        AABB box = new AABB(getPos()).inflate(DETECT_RANGE);
        List<MissileEntity> missiles = level.getEntitiesOfClass(MissileEntity.class, box, m -> !m.isRemoved());
        // Missiles already locked by another interceptor, so batteries don't dogpile one target.
        Set<UUID> claimed = MissileEntity.claimedTargets(missiles, Integer.MAX_VALUE);
        MissileEntity best = null;
        double bestDistSqr = DETECT_RANGE * DETECT_RANGE;
        for (MissileEntity missile : missiles) {
            if (missile.isRemoved() || !isEngageable(missile) || claimed.contains(missile.getUUID())) {
                continue;
            }
            double distSqr = center.distanceToSqr(missile.getBoundingBox().getCenter());
            if (distSqr <= bestDistSqr && missile.detectableAt(distSqr, level.random)) {
                best = missile;
                bestDistSqr = distSqr;
            }
        }
        return best;
    }

    /** A hostile, wfcore-built strike round (not another interceptor, and worth a shot) is a valid target. */
    private boolean isEngageable(MissileEntity missile) {
        return !missile.isInterceptor() && isWfcoreThreat(missile) && isHostile(missile);
    }

    /**
     * Whether the battery should spend a round on {@code missile}. Only wfcore-built rounds are engaged — every
     * one carries a {@code wfcore:} damage response (see {@link com.norwood.wfcore.common.data.WFMissiles}), so
     * WF-B's own test/debug missiles are left alone. An inert round is transparently harmless and skipped (the
     * {@code dummy}), <b>unless</b> it's flying the loiter-until-dry cruise — an inert loiterer reads as a live
     * loitering munition, so the battery is deliberately baited into engaging it (a decoy that wastes ammo).
     */
    private boolean isWfcoreThreat(MissileEntity missile) {
        ResourceLocation response = missile.getDamageResponseId();
        if (response == null || !WFCore.MOD_ID.equals(response.getNamespace())) {
            return false; // not one of ours — a WF-B test missile, or something else entirely
        }
        boolean inert = INERT_WARHEAD_ID.equals(missile.getDetonationId());
        boolean loiterer = LOITER_STAGE_ID.equals(missile.getCruiseStageId());
        return !inert || loiterer; // inert and not loitering == the dummy -> ignore
    }

    private boolean isHostile(MissileEntity missile) {
        UUID missileControl = missile.getControlId();
        if (missileControl != null && missileControl.equals(controlId())) {
            return false; // one of our own rounds
        }
        return !WarforgeCompat.areFactionsFriendly(cachedTeamId, missile.getTeamId());
    }

    /** Pulls one interceptor (best-fit for this target, or the manual pick) + {@link #EU_PER_INTERCEPT} and launches it. */
    private void fire(ServerLevel level, MissileEntity target) {
        // In auto mode, size the round to this specific threat; otherwise honour the operator's chosen type.
        String interceptorId = autoBestFit ? attemptBestFit(target) : selectedInterceptorId;
        // Extract before draining energy, so a pull that races out from under us costs nothing (see the silo).
        ItemStack taken = extractInterceptor(interceptorId);
        if (!(taken.getItem() instanceof MissileItem missileItem) || !missileItem.preset().isInterceptor()) {
            return;
        }
        drainEnergy(false);
        Vec3 muzzle = center().add(0.0, MUZZLE_HEIGHT, 0.0);
        MissileEntity interceptor = missileItem.preset().build(level, muzzle);
        interceptor.setControlId(controlId());
        interceptor.setTeamId(cachedTeamId);
        interceptor.setInterceptLock(target.getUUID());
        interceptor.moveTo(muzzle.x, muzzle.y, muzzle.z, 0.0f, 0.0f);
        level.addFreshEntity(interceptor);

        cooldown = FIRE_COOLDOWN;
        rebuildAvailability();
        displayState = computeState().ordinal();
    }

    //////////////////// factory links (same protocol as the Missile Launch Silo) ////////////////////

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
        MetaMachine target = MetaMachine.getMachine(getLevel(), factoryPos);
        if (target != null && !(target instanceof MissileFactoryMachine)) {
            player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_invalid"));
            return;
        }
        linkedFactories.add(factoryPos.immutable());
        this.linkCount = linkedFactories.size();
        player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.link_added",
                linkedFactories.size(), MAX_LINKS));
    }

    public void clearLinks(Player player) {
        linkedFactories.clear();
        this.linkCount = 0;
        player.sendSystemMessage(Component.translatable("wfcore.gui.launcher.links_cleared"));
    }

    /** Live linked factories; unloaded links are kept but skipped, broken/out-of-range ones self-heal away. */
    protected List<MissileFactoryMachine> resolveFactories() {
        List<MissileFactoryMachine> out = new ArrayList<>();
        if (getLevel() == null) {
            return out;
        }
        Iterator<BlockPos> it = linkedFactories.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!getLevel().isLoaded(pos)) {
                continue;
            }
            if (MetaMachine.getMachine(getLevel(), pos) instanceof MissileFactoryMachine factory &&
                    pos.distSqr(getPos()) <= (double) LINK_RANGE * LINK_RANGE) {
                out.add(factory);
            } else {
                it.remove();
            }
        }
        return out;
    }

    //////////////////// interceptor stock (across linked factories) ////////////////////

    /** Server: rebuild the synced availability snapshot and auto-heal the selection when it sold out. */
    protected void rebuildAvailability() {
        if (isRemote()) {
            return;
        }
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (MissileFactoryMachine factory : resolveFactories()) {
            factory.storedInterceptors().forEach((id, count) -> merged.merge(id, count, Integer::sum));
        }
        if ((selectedInterceptorId.isEmpty() || !merged.containsKey(selectedInterceptorId)) && !merged.isEmpty()) {
            selectedInterceptorId = merged.keySet().iterator().next();
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
        if (!tag.equals(availability.serializeNBT())) {
            availability.setTag(tag); // only on change, so @DescSynced traffic stays quiet
        }
    }

    private void ensureParsed() {
        CompoundTag tag = availability.serializeNBT();
        if (tag == parsedFrom) {
            return;
        }
        parsedFrom = tag;
        clientAvailable.clear();
        ListTag list = tag.getList("Missiles", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            clientAvailable.put(entry.getString("Id"), entry.getInt("Count"));
        }
    }

    /** Interceptors available across the linked factories by registry id (synced; safe on both sides). */
    public Map<String, Integer> getAvailableInterceptors() {
        ensureParsed();
        return clientAvailable;
    }

    public String getSelectedInterceptorId() {
        return selectedInterceptorId;
    }

    /** Server-side (from the GUI's selection sync): pick an interceptor type if it's in the snapshot. */
    public void selectInterceptor(String id) {
        if (id != null && getAvailableInterceptors().containsKey(id)) {
            selectedInterceptorId = id;
        }
    }

    public boolean isAutoBestFit() {
        return autoBestFit;
    }

    /** Server-side (from the GUI toggle): flip between auto best-fit and firing the manual pick. */
    public void toggleAutoBestFit() {
        if (!isRemote()) {
            autoBestFit = !autoBestFit;
        }
    }

    /** Extracts one interceptor of {@code id} from the first linked factory holding it, or EMPTY. */
    private ItemStack extractInterceptor(String id) {
        if (id == null || id.isEmpty()) {
            return ItemStack.EMPTY;
        }
        for (MissileFactoryMachine factory : resolveFactories()) {
            ItemStack taken = factory.extractInterceptor(id);
            if (!taken.isEmpty()) {
                return taken;
            }
        }
        return ItemStack.EMPTY;
    }

    //////////////////// best-fit interceptor selection (mirrors WF-B's own kill math) ////////////////////

    /**
     * Picks the interceptor in stock that best answers {@code target}, scoring each by its estimated single-shot
     * kill probability (see {@link #estimateKillProbability}). To conserve premium rounds it prefers the
     * <b>cheapest</b> type that still clears {@link #KILL_FLOOR} (a slow warhead doesn't warrant an Ace); when no
     * stocked round can clear it, it falls back to the highest-odds shot available. Cost is the round's crafting
     * tier (see {@link #interceptorCost}), so an HV interceptor is spent before the EV Mk2 before the IV Ace.
     *
     * @return the winning interceptor's item registry id, or {@code ""} when no interceptor is in stock.
     */
    public String attemptBestFit(MissileEntity target) {
        String bestId = "";
        double bestScore = -1.0;              // kill probability of the current pick (-1 = nothing picked yet)
        double bestCost = Double.MAX_VALUE;   // cruise speed of the current pick (cheapness proxy)
        boolean bestAdequate = false;         // whether the current pick clears KILL_FLOOR
        for (Map.Entry<String, Integer> entry : getAvailableInterceptors().entrySet()) {
            if (entry.getValue() <= 0) {
                continue;
            }
            MissilePreset preset = interceptorPreset(entry.getKey());
            if (preset == null) {
                continue; // unresolved id or, defensively, not actually an interceptor
            }
            double score = estimateKillProbability(preset, target);
            double cost = interceptorCost(entry.getKey());
            boolean adequate = score >= KILL_FLOOR;
            if (isBetterFit(adequate, score, cost, bestAdequate, bestScore, bestCost)) {
                bestId = entry.getKey();
                bestScore = score;
                bestCost = cost;
                bestAdequate = adequate;
            }
        }
        return bestId;
    }

    /**
     * Fit comparison: an adequate round (clears {@link #KILL_FLOOR}) always beats an inadequate one; among two
     * adequate rounds the cheaper wins (ties broken by higher odds); among two inadequate rounds the higher-odds
     * "best shot we've got" wins (ties broken by the cheaper).
     */
    private static boolean isBetterFit(boolean adequate, double score, double cost,
                                       boolean bestAdequate, double bestScore, double bestCost) {
        if (bestScore < 0.0) {
            return true;
        }
        if (adequate != bestAdequate) {
            return adequate;
        }
        if (adequate) {
            return cost < bestCost || (cost == bestCost && score > bestScore);
        }
        return score > bestScore || (score == bestScore && cost < bestCost);
    }


    private static double estimateKillProbability(MissilePreset interceptor, MissileEntity target) {
        double interceptorSpeed = Math.max(1.0E-3, interceptor.cruiseSpeed());
        double targetSpeed = Math.max(0.0, target.getCruiseSpeed());

        double base = interceptor.interceptChance();
        if (interceptorSpeed <= targetSpeed) {
            base *= MissileSimConfig.INTERCEPTOR_CROSSING_HIT_FACTOR; // couldn't overtake it -> unreliable crossing shot
        }

        double escape = 0.0;
        float evasion = target.effectiveEvasion(); // dive-amplified when the target is already in its terminal dive
        if (evasion > 0.0f) {
            double boostedSpeed = targetSpeed * TARGET_BOOST_SPEED_MULT;
            double speedFactor = Math.min(1.0, boostedSpeed / interceptorSpeed);
            escape = Math.min(1.0, evasion * speedFactor);
        }
        return base * (1.0 - escape);
    }

    /** Resolves an interceptor item id to its preset, or null if the id isn't a stocked interceptor missile. */
    @Nullable
    private MissilePreset interceptorPreset(String id) {
        if (BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(id)) instanceof MissileItem missile
                && missile.preset().isInterceptor()) {
            return missile.preset();
        }
        return null;
    }


    @Nullable
    private static Map<String, Integer> interceptorCosts;


    private static int interceptorCost(String id) {
        if (interceptorCosts == null) {
            interceptorCosts = Map.of(
                    itemId(WFMissiles.INTERCEPTOR), GTValues.HV,
                    itemId(WFMissiles.INTERCEPTOR_MK2), GTValues.EV,
                    itemId(WFMissiles.INTERCEPTOR_ACE), GTValues.IV,
                    itemId(WFMissiles.INTERCEPTOR_CLUSTER), GTValues.IV);
        }
        return interceptorCosts.getOrDefault(id, GTValues.IV);
    }

    private static String itemId(ItemEntry<MissileItem> entry) {
        return BuiltInRegistries.ITEM.getKey(entry.get()).toString();
    }

    protected boolean drainEnergy(boolean simulate) {
        if (energyContainer == null) {
            return false;
        }
        long result = energyContainer.getEnergyStored() - EU_PER_INTERCEPT;
        if (result >= 0L && result <= energyContainer.getEnergyCapacity()) {
            if (!simulate) {
                energyContainer.removeEnergy(EU_PER_INTERCEPT);
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

    //////////////////// synced state (read by the GUI, safe on both sides) ////////////////////

    public State computeState() {
        if (!isFormed()) return State.UNFORMED;
        if (voltageTier < MIN_TIER) return State.LOW_TIER;
        if (hasMaintenanceProblems()) return State.MAINTENANCE;
        if (getAvailableInterceptors().isEmpty()) return State.NO_INTERCEPTORS;
        if (!drainEnergy(true)) return State.NO_ENERGY;
        if (cooldown > 0) return State.RELOADING;
        return State.SCANNING;
    }

    /** True when a maintenance hatch is present and reporting unfixed problems (the battery stands down). */
    public boolean hasMaintenanceProblems() {
        return maintenance != null && maintenance.hasMaintenanceProblems();
    }

    public State getDisplayState() {
        return State.values()[displayState];
    }

    public int getLinkCount() {
        return linkCount;
    }

    public int getCooldown() {
        return cooldown;
    }

    //////////////////// status line (rendered by InterceptorGui) ////////////////////

    private static Component statusText(State state) {
        return switch (state) {
            case UNFORMED -> Component.translatable("wfcore.gui.interceptor.status_unformed")
                    .withStyle(ChatFormatting.RED);
            case LOW_TIER -> Component.translatable("wfcore.gui.interceptor.status_low_tier")
                    .withStyle(ChatFormatting.RED);
            case NO_INTERCEPTORS -> Component.translatable("wfcore.gui.interceptor.status_no_interceptors")
                    .withStyle(ChatFormatting.YELLOW);
            case NO_ENERGY -> Component.translatable("wfcore.gui.interceptor.status_no_energy")
                    .withStyle(ChatFormatting.RED);
            case RELOADING -> Component.translatable("wfcore.gui.interceptor.status_reloading")
                    .withStyle(ChatFormatting.YELLOW);
            case SCANNING -> Component.translatable("wfcore.gui.interceptor.status_scanning")
                    .withStyle(ChatFormatting.GREEN);
            case MAINTENANCE -> Component.translatable("wfcore.gui.interceptor.status_maintenance")
                    .withStyle(ChatFormatting.RED);
        };
    }

    /** Public wrapper so the GUI can render the same coloured status line. */
    public Component statusComponent() {
        return statusText(getDisplayState());
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
        this.linkCount = linkedFactories.size();
    }

    //////////////////// data stick linking (consume the factory position copied onto the stick) ////////////////////

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
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onDataStickShiftUse(Player player, ItemStack stick) {
        if (!isRemote()) {
            clearLinks(player);
        }
        return InteractionResult.SUCCESS;
    }

    //////////////////// animated model (mcgltf iron dome, rendered by GltfMachineRenderer) ////////////////////

    /** The iron dome's single baked deploy/retract animation. */
    @Override
    public String getAnimState() {
        return "animation";
    }

    /**
     * Drive the dome's pose directly from {@link #deployProgress}: held at the start frame while idle, played
     * forward to fully open as it engages, then back to the start frame after it stops firing.
     */
    @Override
    public float getAnimationOverride() {
        return deployProgress;
    }

    @Override
    public boolean shouldRenderModel() {
        return isFormed();
    }

    /**
     * Render-space offset of the dome from the controller: centred over the 7x5 footprint (the controller sits
     * on its front-right corner) and sitting on the ground at the controller's level. Per-facing like the
     * radar; hand-tune live with the model-transform debug tool if the dome needs nudging.
     */
    @Override
    public Vec3 getModelTransform() {
        return switch (getFrontFacing()) {
            case NORTH -> new Vec3(0.5, 0.4, 3.5);
            case SOUTH -> new Vec3(0.5, 0.4, -2.5);
            case EAST -> new Vec3(-2.5, 0.4, 0.5);
            case WEST -> new Vec3(3.5, 0.4, 0.5);
            default -> new Vec3(0.5, 0.4, 0.5);
        };
    }

    @Override
    public Vec3 getModelScale() {
        return new Vec3(1.0, 1.0, 1.0);
    }

    /**
     * The upper-layer blocks (everything above string 0 — the atomic-casing ramp + galvanised frames) that the
     * dome visually replaces; hidden from chunk rendering while formed so the solid-casing base (with the core
     * and energy hatch) and the GLTF dome show. Computed client-side from the controller position + facing and
     * {@link InterceptorStructure#AISLES} (same technique as the radar), so no server sync is needed.
     */
    @Override
    public Collection<BlockPos> getHiddenBlocks() {
        String[][] aisles = InterceptorStructure.AISLES;
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
            return List.of();
        }

        Direction front = getFrontFacing();
        Direction up = getUpwardsFacing();
        boolean flipped = isFlipped();
        Direction charDir = RelativeDirection.FRONT.getRelative(front, up, flipped);
        Direction stringDir = RelativeDirection.UP.getRelative(front, up, flipped);
        Direction aisleDir = RelativeDirection.RIGHT.getRelative(front, up, flipped);

        BlockPos controller = getPos();
        List<BlockPos> hidden = new ArrayList<>();
        for (int a = 0; a < aisles.length; a++) {
            // string 0 is the solid-casing base (kept visible); everything above it is the dome's footprint.
            for (int s = 1; s < aisles[a].length; s++) {
                String layer = aisles[a][s];
                for (int c = 0; c < layer.length(); c++) {
                    if (layer.charAt(c) == ' ') {
                        continue;
                    }
                    hidden.add(controller
                            .relative(charDir, c - cChar)
                            .relative(stringDir, s - cString)
                            .relative(aisleDir, a - cAisle));
                }
            }
        }
        return hidden;
    }

    //////////////////// interaction (open the status GUI) ////////////////////

    @Override
    public InteractionResult onUse(BlockState blockState, Level level, BlockPos pos, Player player,
                                   InteractionHand hand, BlockHitResult hit) {
        // Shift-click is reserved for the multiblock structure preview (empty hand + unformed), so hand it
        // back to the MultiblockControllerMachine/IMultiController default instead of swallowing it — that
        // default is what renders the in-world hologram. Returning PASS here (as before) short-circuited it.
        if (player.isShiftKeyDown()) return super.onUse(blockState, level, pos, player, hand, hit);
        if (!isRemote() && getHolder().self() instanceof InterceptorBlockEntity) {
            BlockEntityUIFactory.INSTANCE.open(player, pos);
        }
        return InteractionResult.SUCCESS;
    }

    public enum State {
        UNFORMED,
        LOW_TIER,
        NO_INTERCEPTORS,
        NO_ENERGY,
        RELOADING,
        SCANNING,
        MAINTENANCE
    }
}
