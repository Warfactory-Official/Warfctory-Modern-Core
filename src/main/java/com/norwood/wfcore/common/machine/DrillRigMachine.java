package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability;
import com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.recipe.RecipeLogic;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.RecipeCondition;

import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;

import brachy.modularui.api.widget.IWidget;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;

import com.norwood.wfcore.common.mui.WFMuiWidgets;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.common.block.DepositBlock;
import com.norwood.wfcore.common.data.WFRecipeTypes;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import net.minecraft.world.phys.AABB;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;


public class DrillRigMachine extends WorkableMultiblockMachine implements IAnimatedMachine, IMuiMachine, IWideRenderBoundsMachine {


    private static final int RESCAN_INTERVAL = 40;
    // Flood-fill bound: covers explicit patches up to MAX_EXPLICIT_SIZE squared (16*16) with headroom.
    private static final int MAX_CLUSTER = 1024;
    // How far horizontally (Chebyshev radius) from the drill-head column we hunt for a deposit. The vein
    // doesn't always generate dead-centre under the head, so a single-column scan was missing it.
    private static final int SCAN_RADIUS = 6;
    // Deposits sit at bedrock cap+1 (within [minY+1, minY+9]); the cluster walk scans each column across this
    // band, from minY+DEPOSIT_BAND down to minY, so it finds neighbours even where the bedrock floor - and thus
    // the deposit's exact Y - steps between adjacent columns (the flat-plane walk only found the start's Y).
    private static final int DEPOSIT_BAND = 12;

    @Nullable
    private BlockPos centralPos;
    private long lastScanTick = Long.MIN_VALUE;

    // Cached cluster walk. Deposit blocks are bedrock-anchored and never move; a drained one only turns to
    // bedrock (the cluster shrinks, never grows), so the flood-fill position list is computed once per
    // (center, type) and reused - callers skip now-bedrock positions rather than re-walking every cycle.
    @Nullable
    private BlockPos clusterCacheCenter;
    @Nullable
    private ResourceLocation clusterCacheType;
    @Nullable
    private List<BlockPos> clusterCache;

    // Server-computed deposit readout, mirrored to the client so the multiblock screen can show it (the
    // scan/flood-fill run server-side only; onStructureFormed doesn't fire on the client). null id = none.
    @SyncToClient
    @Nullable
    private ResourceLocation displayDepositId;
    @SyncToClient
    private int displayCentralYield;
    @SyncToClient
    private int displayClusterYield;
    @SyncToClient
    private int displayClusterBlocks;

    @Nullable
    private TickableSubscription displayTickSub;

    public DrillRigMachine(BlockEntityCreationInfo info) {
        // 8.0.0 takes the recipe logic as a constructor argument instead of a createRecipeLogic override,
        // and keepSubscribing moved onto RecipeLogic itself (the base multiblock ctor clears it, and the
        // drill rig wants it on: it has to keep looking for a deposit recipe even while idle).
        super(info, new DrillRigRecipeLogic());
        getRecipeLogic().setKeepSubscribing(true);
    }


    @Override
    public void formStructure(String substructureName) {
        super.formStructure(substructureName);
        this.centralPos = null;
        this.lastScanTick = Long.MIN_VALUE;
        invalidateClusterCache();
        if (!isRemote()) {
            this.displayTickSub = subscribeServerTick(this::refreshDisplayTick);
        }
    }

    @Override
    public void invalidateStructure(String substructureName) {
        super.invalidateStructure(substructureName);
        this.centralPos = null;
        invalidateClusterCache();
        setDisplayDeposit(null, 0, 0, 0);
        if (displayTickSub != null) {
            displayTickSub.unsubscribe();
            displayTickSub = null;
        }
    }


    @Nullable
    public DepositBlockEntity getCentralDeposit() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return null;
        }
        if (centralPos != null) {
            if (level.getBlockEntity(centralPos) instanceof DepositBlockEntity dbe) {
                return dbe;
            }
            centralPos = null;
        }
        long now = level.getGameTime();
        if (lastScanTick != Long.MIN_VALUE && now - lastScanTick < RESCAN_INTERVAL) {
            return null;
        }
        lastScanTick = now;
        DepositBlockEntity found = scanForDeposit(level);
        centralPos = found == null ? null : found.getBlockPos();
        return found;
    }

    @Nullable
    public ResourceLocation getActiveDepositTypeId() {
        DepositBlockEntity central = getCentralDeposit();
        return central == null ? null : central.getDepositTypeId();
    }


    @Nullable
    public DepositType getDisplayDepositType() {
        return displayDepositId == null ? null : WFDeposits.get(displayDepositId);
    }


    public List<BlockPos> getDepositBlocksNearHead(Level level, int radius) {
        List<BlockPos> result = new ArrayList<>();
        BlockPos head = getDrillHeadWorldPos();
        if (head == null) {
            return result;
        }
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int y = minY + DEPOSIT_BAND; y >= minY; y--) {
                    cursor.set(head.getX() + dx, y, head.getZ() + dz);
                    if (level.getBlockState(cursor).getBlock() instanceof DepositBlock) {
                        result.add(cursor.immutable());
                        break;
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    public GTRecipe findBaseDrillingRecipe() {
        ResourceLocation active = getActiveDepositTypeId();
        Level level = getLevel();
        if (active == null || level == null) {
            return null;
        }
        // 1.21 wraps every recipe in a RecipeHolder carrying its id; unwrap with value().
        for (RecipeHolder<GTRecipe> holder : level.getRecipeManager().getAllRecipesFor(WFRecipeTypes.DRILLING.get())) {
            GTRecipe recipe = holder.value();
            boolean hasInputs = !recipe.inputs.getOrDefault(ItemRecipeCapability.CAP, List.of()).isEmpty() ||
                    !recipe.inputs.getOrDefault(FluidRecipeCapability.CAP, List.of()).isEmpty();
            if (hasInputs) {
                continue;
            }
            for (RecipeCondition<?> condition : recipe.conditions) {
                if (condition instanceof DepositRecipeCondition deposit && deposit.matches(active)) {
                    return recipe;
                }
            }
        }
        return null;
    }


    @Nullable
    private DepositBlockEntity scanForDeposit(Level level) {
        BlockPos head = getDrillHeadWorldPos();
        if (head == null) {
            return null;
        }
        DepositBlockEntity nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int dist = Math.max(Math.abs(dx), Math.abs(dz));
                if (dist >= nearestDist) {
                    continue; // a farther column can't beat the deposit we already found
                }
                int x = head.getX() + dx;
                int z = head.getZ() + dz;
                for (int y = head.getY() - 1; y >= minY; y--) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).getBlock() instanceof DepositBlock) {
                        if (level.getBlockEntity(cursor) instanceof DepositBlockEntity dbe) {
                            nearest = dbe;
                            nearestDist = dist;
                        }
                        break; // first deposit down this column wins it; move to the next column
                    }
                }
            }
        }
        return nearest;
    }


    public void onDrillCycleFinished() {
        Level level = getLevel();
        DepositBlockEntity central = getCentralDeposit();
        if (level == null || central == null) {
            refreshDepositDisplay(level);
            return;
        }
        BlockPos center = central.getBlockPos();
        ResourceLocation type = central.getDepositTypeId();

        BlockPos target = null;
        int bestDist = -1;
        long bestKey = Long.MIN_VALUE;
        for (BlockPos pos : getCluster(level, center, type)) {
            if (!(level.getBlockEntity(pos) instanceof DepositBlockEntity dbe) || dbe.getRemainingYield() <= 0) {
                continue;
            }
            int dist = chebyshev(center, pos);
            long key = pos.asLong();
            if (dist > bestDist || (dist == bestDist && key > bestKey)) {
                bestDist = dist;
                bestKey = key;
                target = pos;
            }
        }
        if (target != null && level.getBlockEntity(target) instanceof DepositBlockEntity dbe) {
            dbe.deplete(1);
        }
        refreshDepositDisplay(level);
    }

    /**
     * The cluster walk for {@code (center, type)}, computed once and cached. Deposit blocks never move, so a
     * cache hit returns the original full position list; drained members simply become bedrock and are filtered
     * out by callers. A different center/type (the head found a new vein) transparently refills the cache.
     */
    private List<BlockPos> getCluster(Level level, BlockPos center, @Nullable ResourceLocation type) {
        if (clusterCache != null && center.equals(clusterCacheCenter) && Objects.equals(type, clusterCacheType)) {
            return clusterCache;
        }
        clusterCache = floodFill(level, center, type);
        clusterCacheCenter = center.immutable();
        clusterCacheType = type;
        return clusterCache;
    }

    private void invalidateClusterCache() {
        clusterCache = null;
        clusterCacheCenter = null;
        clusterCacheType = null;
    }

    /**
     * All same-type deposit blocks 4-connected (in x/z, at ANY Y within the bedrock band) to {@code start} -
     * i.e. the whole patch. Walks by COLUMN and finds each neighbour's deposit wherever it sits in the band,
     * so per-column bedrock-height variation no longer strands most of the vein (the old flat-plane walk kept
     * only blocks sharing the start's exact Y, which is why a 200-block patch drilled as ~6).
     */
    private List<BlockPos> floodFill(Level level, BlockPos start, @Nullable ResourceLocation type) {
        List<BlockPos> result = new ArrayList<>();
        LongOpenHashSet seenColumns = new LongOpenHashSet();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seenColumns.add(columnKey(start.getX(), start.getZ()));
        int minY = level.getMinBuildHeight();
        int[] dx = { 1, -1, 0, 0 };
        int[] dz = { 0, 0, 1, -1 };
        while (!queue.isEmpty() && result.size() < MAX_CLUSTER) {
            BlockPos pos = queue.poll();
            result.add(pos);
            for (int i = 0; i < 4; i++) {
                int nx = pos.getX() + dx[i];
                int nz = pos.getZ() + dz[i];
                if (seenColumns.add(columnKey(nx, nz))) {
                    BlockPos next = columnDepositPos(level, nx, nz, minY, type);
                    if (next != null) {
                        queue.add(next);
                    }
                }
            }
        }
        return result;
    }

    @Nullable
    private BlockPos columnDepositPos(Level level, int x, int z, int minY, @Nullable ResourceLocation type) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int y = minY + DEPOSIT_BAND; y >= minY; y--) {
            cursor.set(x, y, z);
            if (level.getBlockState(cursor).getBlock() instanceof DepositBlock) {
                return level.getBlockEntity(cursor) instanceof DepositBlockEntity dbe &&
                        Objects.equals(dbe.getDepositTypeId(), type) ? cursor.immutable() : null;
            }
        }
        return null;
    }

    private static long columnKey(int x, int z) {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }


    protected void refreshDisplayTick() {
        Level level = getLevel();
        if (level == null || level.isClientSide()) {
            return;
        }
        if (getOffsetTimer() % RESCAN_INTERVAL == 0) {
            refreshDepositDisplay(level);
        }
    }

    private void refreshDepositDisplay(@Nullable Level level) {
        if (level == null || level.isClientSide()) {
            return;
        }
        DepositBlockEntity central = getCentralDeposit();
        if (central == null || central.getDepositTypeId() == null) {
            setDisplayDeposit(null, 0, 0, 0);
            return;
        }
        int clusterYield = 0;
        int clusterBlocks = 0;
        for (BlockPos pos : getCluster(level, central.getBlockPos(), central.getDepositTypeId())) {
            if (level.getBlockEntity(pos) instanceof DepositBlockEntity dbe && dbe.getRemainingYield() > 0) {
                clusterYield += dbe.getRemainingYield();
                clusterBlocks++;
            }
        }
        setDisplayDeposit(central.getDepositTypeId(), central.getRemainingYield(),
                clusterYield, clusterBlocks);
    }

    private void setDisplayDeposit(@Nullable ResourceLocation id, int centralYield, int clusterYield,
                                   int clusterBlocks) {
        this.displayDepositId = id;
        this.displayCentralYield = centralYield;
        this.displayClusterYield = clusterYield;
        this.displayClusterBlocks = clusterBlocks;
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        mainWidget.child(WFMuiWidgets.displayBox(getWidgetsForDisplay(syncManager)).margin(4, 2));
    }

    /**
     * WorkableMultiblockMachine already contributes the unformed warning, progress, working status and
     * recipe-failure lines, so only the deposit readout is added here. It goes in at index 1, directly under
     * the unformed warning and above the progress line, because it is the headline information for this
     * machine and the LDLib version showed it near the top too.
     */
    @Override
    public List<IWidget> getWidgetsForDisplay(PanelSyncManager syncManager) {
        List<IWidget> widgets = super.getWidgetsForDisplay(syncManager);
        BooleanSyncValue isFormedSync = syncManager.getOrCreateSyncHandler("isFormed", BooleanSyncValue.class,
                () -> new BooleanSyncValue(this::isFormed));

        // All four display* fields are @SyncToClient, so they can be read straight off the machine here.
        widgets.add(1, WFMuiWidgets.conditionalLine(
                () -> isFormedSync.getBoolValue() && displayDepositId == null,
                () -> Component.translatable("wfcore.machine.drill_rig.no_deposit").withStyle(ChatFormatting.RED)));
        widgets.add(2, WFMuiWidgets.conditionalLine(
                () -> isFormedSync.getBoolValue() && displayDepositId != null,
                () -> Component.translatable("wfcore.machine.drill_rig.drilling", depositName())
                        .withStyle(ChatFormatting.GREEN)));
        widgets.add(3, WFMuiWidgets.conditionalLine(
                () -> isFormedSync.getBoolValue() && displayDepositId != null,
                () -> Component.translatable("wfcore.machine.drill_rig.central_yield", displayCentralYield)
                        .withStyle(ChatFormatting.GRAY)));
        widgets.add(4, WFMuiWidgets.conditionalLine(
                () -> isFormedSync.getBoolValue() && displayDepositId != null,
                () -> Component.translatable("wfcore.machine.drill_rig.cluster_yield",
                        displayClusterYield, displayClusterBlocks).withStyle(ChatFormatting.GRAY)));
        return widgets;
    }

    private Component depositName() {
        DepositType type = WFDeposits.get(displayDepositId);
        return type != null ? Component.translatable(type.nameKey())
                : Component.literal(String.valueOf(displayDepositId));
    }

    //animated model


    @Override
    public String getAnimState() {
        return "wholespin";
    }

    @Override
    public boolean isAnimationRunning() {
        return isActive();
    }

    @Override
    public boolean shouldRenderModel() {
        return isFormed();
    }

    @Override
    public Collection<BlockPos> getHiddenBlocks() {
        BlockPos drillHead = getDrillHeadWorldPos();
        if (drillHead == null) {
            return List.of();
        }
        Direction stringDir = RelativeDirection.UP.getRelativeFacing(getFrontFacing(), getUpwardsFacing(), isFlipped());
        return List.of(drillHead, drillHead.relative(stringDir));
    }


    @Nullable
    public BlockPos getDrillHeadWorldPos() {
        String[][] aisles = DrillRigStructure.AISLES;

        int cChar = -1, cString = -1, cAisle = -1;
        int fChar = -1, fString = -1, fAisle = -1;
        for (int a = 0; a < aisles.length; a++) {
            for (int s = 0; s < aisles[a].length; s++) {
                int sIdx = aisles[a][s].indexOf('S');
                if (sIdx >= 0) {
                    cAisle = a;
                    cString = s;
                    cChar = sIdx;
                }
                int fIdx = aisles[a][s].indexOf('F');
                if (fIdx >= 0) {
                    fAisle = a;
                    fString = s;
                    fChar = fIdx;
                }
            }
        }
        if (cChar < 0 || fChar < 0) {
            return null;
        }

        Direction front = getFrontFacing();
        Direction up = getUpwardsFacing();
        boolean flipped = isFlipped();
        Direction charDir = RelativeDirection.FRONT.getRelativeFacing(front, up, flipped);
        Direction stringDir = RelativeDirection.UP.getRelativeFacing(front, up, flipped);
        Direction aisleDir = RelativeDirection.RIGHT.getRelativeFacing(front, up, flipped);

        return getBlockPos().relative(charDir, fChar - cChar).relative(stringDir, fString - cString)
                .relative(aisleDir, fAisle - cAisle);
    }


    public record DebugScan(List<BlockPos> hits, @Nullable BlockPos nearest, @Nullable BlockPos head) {}


    public DebugScan debugScan(Level level) {
        BlockPos head = getDrillHeadWorldPos();
        if (head == null) {
            return new DebugScan(List.of(), null, null);
        }
        List<BlockPos> hits = new ArrayList<>();
        BlockPos nearest = null;
        int nearestDist = Integer.MAX_VALUE;
        int minY = level.getMinBuildHeight();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -SCAN_RADIUS; dx <= SCAN_RADIUS; dx++) {
            for (int dz = -SCAN_RADIUS; dz <= SCAN_RADIUS; dz++) {
                int x = head.getX() + dx;
                int z = head.getZ() + dz;
                for (int y = head.getY() - 1; y >= minY; y--) {
                    cursor.set(x, y, z);
                    if (level.getBlockState(cursor).getBlock() instanceof DepositBlock) {
                        BlockPos hit = cursor.immutable();
                        hits.add(hit);
                        int dist = Math.max(Math.abs(dx), Math.abs(dz));
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = hit;
                        }
                        break; // first deposit down this column, matching scanForDeposit
                    }
                }
            }
        }
        return new DebugScan(hits, nearest, head);
    }

    @Override
    public Vec3 getModelTransform() {
        // Placeholder offsets - tune in-world with ModelTransformDebug (numpad live editor) and paste the
        // exported switch block here, mirroring how RadarMachine's transform was tuned.
        return switch (getFrontFacing()) {
            case WEST -> new Vec3(0.5, -4.25, 1.5);
            case EAST -> new Vec3(0.5, -4.25, -0.5);
            case NORTH -> new Vec3(-0.5, -4.25, 0.5);
            case SOUTH -> new Vec3(1.5, -4.25, 0.5);
            default -> Vec3.ZERO;
        };
    }

    @Override
    public Vec3 getModelScale() {
        return new Vec3(2, 2, 2);
    }

    // ---------------------------------------------------------------------------------------------
    // Merged in from the former DrillRigBlockEntity. GT 8.0.0 folded MetaMachineBlockEntity into
    // MetaMachine, so the machine *is* the BlockEntity and these overrides belong here.
    // ---------------------------------------------------------------------------------------------

    @Override
    public AABB getRenderBounds() {
        // The derrick rises well above the controller; inflate so the glTF model is never frustum-culled.
        return new AABB(getBlockPos()).inflate(32);
    }

    @Override
    public void onUnload() {
        // Drop the render mask when the controller is broken or its chunk unloads (the BER, which would
        // otherwise unregister it, stops being called once this block entity is gone).
        if (level != null && level.isClientSide) {
            RenderMaskManager.removeDisableModel(getBlockPos());
        }
        super.onUnload();
    }

}
