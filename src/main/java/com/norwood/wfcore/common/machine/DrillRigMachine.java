package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.multiblock.WorkableMultiblockMachine;
import com.gregtechceu.gtceu.api.machine.trait.RecipeLogic;

import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.client.render.gltf.AnimTransition;
import com.norwood.wfcore.client.render.gltf.IAnimatedMachine;
import com.norwood.wfcore.common.block.DepositBlock;
import com.norwood.wfcore.common.data.WFBlocks;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Drilling-rig controller. The deposit is not part of the structure: once formed, the rig locates its drill head
 * and projects straight down to the bedrock floor to find a {@link DepositBlock}. The deposit's type drives which
 * {@code wfcore:drilling} recipe runs (via DepositRecipeCondition + DrillingCustomRecipeLogic); each completed
 * cycle drains a connected deposit cluster from its outer blocks inward, leaving the central column under the head
 * for last and turning drained blocks to bedrock.
 */
public class DrillRigMachine extends WorkableMultiblockMachine implements IAnimatedMachine {

    protected static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            DrillRigMachine.class, WorkableMultiblockMachine.MANAGED_FIELD_HOLDER);

    private static final int RESCAN_INTERVAL = 40;
    // Flood-fill bound: covers explicit patches up to MAX_EXPLICIT_SIZE squared (16*16) with headroom.
    private static final int MAX_CLUSTER = 1024;

    @Nullable
    private BlockPos drillHeadPos;
    @Nullable
    private BlockPos centralPos;
    private long lastScanTick = Long.MIN_VALUE;

    public DrillRigMachine(IMachineBlockEntity holder, Object... args) {
        super(holder, args);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
    }

    @Override
    protected RecipeLogic createRecipeLogic(Object... args) {
        return new DrillRigRecipeLogic(this);
    }

    //////////////////// structure lifecycle ////////////////////

    @Override
    public void onStructureFormed() {
        super.onStructureFormed();
        this.drillHeadPos = locateDrillHead();
        this.centralPos = null;
        this.lastScanTick = Long.MIN_VALUE;
    }

    @Override
    public void onStructureInvalid() {
        super.onStructureInvalid();
        this.drillHeadPos = null;
        this.centralPos = null;
    }

    @Nullable
    private BlockPos locateDrillHead() {
        Level level = getLevel();
        var state = getMultiblockState();
        if (level == null || state == null) {
            return null;
        }
        var head = WFBlocks.DRILL_HEAD.get();
        for (BlockPos pos : state.getCache()) {
            if (level.getBlockState(pos).is(head)) {
                return pos.immutable();
            }
        }
        return null;
    }

    //////////////////// deposit detection ////////////////////

    /** The deposit directly under the drill head ("central" block), cached and re-scanned lazily. */
    @Nullable
    public DepositBlockEntity getCentralDeposit() {
        Level level = getLevel();
        if (level == null || level.isClientSide() || drillHeadPos == null) {
            return null;
        }
        if (centralPos != null) {
            if (level.getBlockEntity(centralPos) instanceof DepositBlockEntity dbe) {
                return dbe;
            }
            centralPos = null;
        }
        long now = level.getGameTime();
        if (now - lastScanTick < RESCAN_INTERVAL) {
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
    private DepositBlockEntity scanForDeposit(Level level) {
        if (drillHeadPos == null) {
            return null;
        }
        BlockPos.MutableBlockPos cursor = drillHeadPos.mutable();
        for (int y = drillHeadPos.getY() - 1; y >= level.getMinBuildHeight(); y--) {
            cursor.setY(y);
            if (level.getBlockState(cursor).getBlock() instanceof DepositBlock) {
                return level.getBlockEntity(cursor) instanceof DepositBlockEntity dbe ? dbe : null;
            }
        }
        return null;
    }

    //////////////////// cluster depletion ////////////////////

    /** One drill cycle finished: drain a single yield from the outermost block of the deposit cluster. */
    public void onDrillCycleFinished() {
        Level level = getLevel();
        DepositBlockEntity central = getCentralDeposit();
        if (level == null || central == null) {
            return;
        }
        BlockPos center = central.getBlockPos();
        ResourceLocation type = central.getDepositTypeId();

        BlockPos target = null;
        int bestDist = -1;
        long bestKey = Long.MIN_VALUE;
        for (BlockPos pos : floodFill(level, center, type)) {
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
    }

    /** 4-connected deposit blocks of the same type on the bedrock plane, starting at {@code start}. */
    private List<BlockPos> floodFill(Level level, BlockPos start, @Nullable ResourceLocation type) {
        List<BlockPos> result = new ArrayList<>();
        java.util.Set<Long> seen = new java.util.HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        queue.add(start);
        seen.add(start.asLong());
        int[] dx = { 1, -1, 0, 0 };
        int[] dz = { 0, 0, 1, -1 };
        while (!queue.isEmpty() && result.size() < MAX_CLUSTER) {
            BlockPos pos = queue.poll();
            if (!(level.getBlockEntity(pos) instanceof DepositBlockEntity dbe) ||
                    !Objects.equals(dbe.getDepositTypeId(), type)) {
                continue;
            }
            result.add(pos);
            for (int i = 0; i < 4; i++) {
                BlockPos next = pos.offset(dx[i], 0, dz[i]);
                if (seen.add(next.asLong()) && level.getBlockState(next).getBlock() instanceof DepositBlock) {
                    queue.add(next);
                }
            }
        }
        return result;
    }

    private static int chebyshev(BlockPos a, BlockPos b) {
        return Math.max(Math.abs(a.getX() - b.getX()), Math.abs(a.getZ() - b.getZ()));
    }

    //////////////////// animated model (mcgltf) ////////////////////

    @Override
    public String getAnimState() {
        return isActive() ? "running" : "idle";
    }

    @Override
    public AnimTransition getAnimTransition(String from, String to) {
        // spin up the moment drilling starts; play the current loop out before settling back to idle
        return "running".equals(to) ? AnimTransition.SNAP : AnimTransition.FINISH_LOOP;
    }

    @Override
    public boolean shouldRenderModel() {
        return isFormed();
    }
}
