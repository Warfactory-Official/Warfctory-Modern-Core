package com.norwood.wfcore.common.ballistics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BallisticsManager {

    private static final int MAX_SUBSTEPS = 16;

    private static final Map<ResourceKey<Level>, BallisticsManager> LEVELS = new ConcurrentHashMap<>();

    private final ServerLevel level;
    private final AsyncTerrainSource terrain;
    private final BallisticsSavedData data;

    private final Long2ObjectOpenHashMap<List<DeferredImpact>> deferred = new Long2ObjectOpenHashMap<>();

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    private final ObjectArrayList<VirtualProjectile> tickScratch = new ObjectArrayList<>();

    private BallisticsManager(ServerLevel level) {
        this.level = level;
        this.terrain = new AsyncTerrainSource(level);
        this.data = BallisticsSavedData.get(level);
    }

    public static BallisticsManager get(ServerLevel level) {
        return LEVELS.computeIfAbsent(level.dimension(), k -> new BallisticsManager(level));
    }

    public static void forgetLevel(ServerLevel level) {
        LEVELS.remove(level.dimension());
    }

    public AsyncTerrainSource terrain() {
        return terrain;
    }

    public void addVirtual(VirtualProjectile v) {
        data.add(v);
        if (WFCoreConfig.isBallisticsDebugLogging()) {
            BlockPos b = BlockPos.containing(v.pos);
            WFCore.LOGGER.info(
                    "Ballistics: {} ({}) LEFT loaded chunks -> VIRTUAL at {} [chunk {},{}] vel {} in {} (now tracking {})",
                    v.id, v.adapterId, b, b.getX() >> 4, b.getZ() >> 4, v.vel, level.dimension().location(),
                    data.getInFlight().size());
        }
    }

    public int size() {
        return data.getInFlight().size();
    }

    private static long chunkKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    public void tick(int serverTick) {
        Map<java.util.UUID, VirtualProjectile> inFlight = data.getInFlight();
        if (inFlight.isEmpty()) {
            return;
        }

        ObjectArrayList<VirtualProjectile> snapshot = tickScratch;
        snapshot.clear();
        snapshot.addAll(inFlight.values());
        for (int i = 0, n = snapshot.size(); i < n; i++) {
            tickOne(snapshot.get(i), serverTick);
        }
        snapshot.clear();
    }

    private void tickOne(VirtualProjectile v, int serverTick) {

        if (v.impactTick == serverTick && v.impactPos != null) {
            resolveImpactNow(v);
            return;
        }

        BallisticsAdapter adapter = BallisticsRegistry.byId(v.adapterId);
        if (adapter == null) {

            data.remove(v.id);
            return;
        }

        int substeps = pathFullyUnloaded(v) ? MAX_SUBSTEPS : 1;

        for (int step = 0; step < substeps; step++) {
            StepResult r = advanceOneTick(v, adapter, serverTick);
            if (r != StepResult.COMMITTED) {

                return;
            }
        }
    }

    private boolean pathFullyUnloaded(VirtualProjectile v) {
        Vec3 pos = v.pos;
        Vec3 vel = v.vel;
        int cx0 = Mth.floor(pos.x) >> 4;
        int cz0 = Mth.floor(pos.z) >> 4;
        int cx1 = Mth.floor(pos.x + vel.x) >> 4;
        int cz1 = Mth.floor(pos.z + vel.z) >> 4;
        for (int cx = Math.min(cx0, cx1); cx <= Math.max(cx0, cx1); cx++) {
            for (int cz = Math.min(cz0, cz1); cz <= Math.max(cz0, cz1); cz++) {
                if (level.getChunkSource().getChunkNow(cx, cz) != null) {
                    return false;
                }
            }
        }
        return true;
    }

    private enum StepResult { COMMITTED, PROMOTED, IMPACTED, PENDING, EXPIRED }

    private StepResult advanceOneTick(VirtualProjectile v, BallisticsAdapter adapter, int serverTick) {
        final Vec3 from = v.pos;
        final double fx = from.x, fy = from.y, fz = from.z;

        final double dx = v.vel.x, dy = v.vel.y, dz = v.vel.z;

        int bx = Mth.floor(fx);
        int by = Mth.floor(fy);
        int bz = Mth.floor(fz);

        final int stepX = dx > 0 ? 1 : (dx < 0 ? -1 : 0);
        final int stepY = dy > 0 ? 1 : (dy < 0 ? -1 : 0);
        final int stepZ = dz > 0 ? 1 : (dz < 0 ? -1 : 0);

        double tMaxX = boundaryT(fx, dx, stepX);
        double tMaxY = boundaryT(fy, dy, stepY);
        double tMaxZ = boundaryT(fz, dz, stepZ);
        final double tDeltaX = stepX != 0 ? Math.abs(1.0 / dx) : Double.POSITIVE_INFINITY;
        final double tDeltaY = stepY != 0 ? Math.abs(1.0 / dy) : Double.POSITIVE_INFINITY;
        final double tDeltaZ = stepZ != 0 ? Math.abs(1.0 / dz) : Double.POSITIVE_INFINITY;

        Direction enteredFace = null;

        double tEntry = 0.0;

        final BlockPos.MutableBlockPos cursor = this.cursor;
        int curCx = Integer.MIN_VALUE, curCz = Integer.MIN_VALUE;
        boolean columnEntityTicking = false;
        int curSx = Integer.MIN_VALUE, curSy = Integer.MIN_VALUE, curSz = Integer.MIN_VALUE;
        SectionSolidity section = null;

        while (true) {
            final int ccx = bx >> 4;
            final int ccz = bz >> 4;

            if (ccx != curCx || ccz != curCz) {
                cursor.set(bx, by, bz);
                columnEntityTicking = level.isPositionEntityTicking(cursor);
                curCx = ccx;
                curCz = ccz;
            }
            if (columnEntityTicking) {
                // Only hand back to vanilla once the shell has actually flown through unloaded space and is now
                // RE-ENTERING the live frontier. On the cells it was demoted from (still loaded), step straight
                // through without promoting, otherwise a shell sitting on the boundary ping-pongs demote<->promote
                // forever and never advances.
                if (v.enteredUnloaded) {
                    promote(v, pointAt(from, dx, dy, dz, tEntry), enteredFace);
                    return StepResult.PROMOTED;
                }
            } else {
                v.enteredUnloaded = true;

                final int sy = by >> 4;
                if (ccx != curSx || sy != curSy || ccz != curSz) {
                    section = terrain.get(ccx, sy, ccz);
                    curSx = ccx;
                    curSy = sy;
                    curSz = ccz;
                }
                if (section == null) {
                    return StepResult.PENDING;
                }
                if (section.isSolid(bx & 15, by & 15, bz & 15)) {
                    Direction face = enteredFace != null ? enteredFace : Direction.UP;
                    scheduleImpact(v, cursor.set(bx, by, bz).immutable(), face, serverTick);
                    return StepResult.IMPACTED;
                }
            }

            if (tMaxX <= tMaxY && tMaxX <= tMaxZ) {
                if (tMaxX > 1.0) {
                    break;
                }
                tEntry = tMaxX;
                bx += stepX;
                tMaxX += tDeltaX;
                enteredFace = stepX > 0 ? Direction.WEST : Direction.EAST;
            } else if (tMaxY <= tMaxZ) {
                if (tMaxY > 1.0) {
                    break;
                }
                tEntry = tMaxY;
                by += stepY;
                tMaxY += tDeltaY;
                enteredFace = stepY > 0 ? Direction.DOWN : Direction.UP;
            } else {
                if (tMaxZ > 1.0) {
                    break;
                }
                tEntry = tMaxZ;
                bz += stepZ;
                tMaxZ += tDeltaZ;
                enteredFace = stepZ > 0 ? Direction.NORTH : Direction.SOUTH;
            }
        }

        if (!v.enteredUnloaded) {
            // The whole segment stayed inside the entity-ticking frontier (e.g. chunks streamed in since this
            // shell was demoted). It belongs to vanilla here -> hand it straight back rather than flying it
            // invisibly through loaded terrain.
            promote(v, new Vec3(fx + dx, fy + dy, fz + dz), enteredFace);
            return StepResult.PROMOTED;
        }

        v.pos = new Vec3(fx + dx, fy + dy, fz + dz);
        v.vel = adapter.advanceVelocity(v);
        v.age++;
        data.setDirty();

        int maxAge = adapter.maxAgeTicks(v);
        if (maxAge > 0 && v.age >= maxAge) {

            if (WFCoreConfig.isBallisticsDebugLogging()) {
                BlockPos b = BlockPos.containing(v.pos);
                WFCore.LOGGER.info("Ballistics: VIRTUAL {} ({}) EXPIRED after {} ticks at {} [chunk {},{}] — no impact",
                        v.id, v.adapterId, v.age, b, b.getX() >> 4, b.getZ() >> 4);
            }
            data.remove(v.id);
            return StepResult.EXPIRED;
        }
        return StepResult.COMMITTED;
    }

    private static double boundaryT(double origin, double d, int step) {
        if (step == 0) {
            return Double.POSITIVE_INFINITY;
        }
        double cell = Math.floor(origin);
        double boundary = step > 0 ? (cell + 1.0) : cell;
        return (boundary - origin) / d;
    }

    private static Vec3 pointAt(Vec3 from, double dx, double dy, double dz, double t) {
        return new Vec3(from.x + dx * t, from.y + dy * t, from.z + dz * t);
    }

    private void promote(VirtualProjectile v, Vec3 entry, Direction enteredFace) {
        BallisticsAdapter adapter = BallisticsRegistry.byId(v.adapterId);
        if (adapter == null) {
            data.remove(v.id);
            return;
        }

        Vec3 back = v.vel.lengthSqr() > 1.0e-6 ? v.vel.normalize().scale(0.5) : Vec3.ZERO;
        Vec3 start = entry.subtract(back);

        ClipContext ctx = new ClipContext(start, entry, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE,
                (net.minecraft.world.entity.Entity) null);
        BlockHitResult hit = level.clip(ctx);
        if (hit.getType() == HitResult.Type.BLOCK) {
            v.impactPos = hit.getLocation();
            v.impactFace = hit.getDirection();
            resolveImpactNow(v);
            return;
        }

        v.pos = entry;
        if (adapter.spawnLive(level, v) != null) {
            if (WFCoreConfig.isBallisticsDebugLogging()) {
                WFCore.LOGGER.debug("Ballistics: promoted virtual {} back to a live entity at {}", v.id, entry);
            }
        } else {
            WFCore.LOGGER.warn("Ballistics: adapter {} failed to spawn live entity for {}", v.adapterId, v.id);
        }
        data.remove(v.id);
    }

    private void scheduleImpact(VirtualProjectile v, BlockPos hitPos, Direction face, int serverTick) {

        v.impactPos = Vec3.atCenterOf(hitPos);
        v.impactFace = face;
        v.impactTick = serverTick;
        resolveImpactNow(v);
    }

    private void resolveImpactNow(VirtualProjectile v) {
        BallisticsAdapter adapter = BallisticsRegistry.byId(v.adapterId);
        if (adapter == null) {
            data.remove(v.id);
            return;
        }

        BlockPos hp = BlockPos.containing(v.impactPos);
        int cx = hp.getX() >> 4;
        int cz = hp.getZ() >> 4;
        boolean loaded = level.getChunkSource().getChunkNow(cx, cz) != null;

        BlockState hs = loaded ? level.getBlockState(hp) : Blocks.STONE.defaultBlockState();
        Direction face = v.impactFace != null ? v.impactFace : Direction.UP;

        DeferredImpact di = adapter.resolveImpact(level, v, hp, face, hs);
        if (WFCoreConfig.isBallisticsDebugLogging()) {

            String outcome = di == null
                    ? "read-only (no world change)"
                    : (loaded ? "mutation applied now" : "mutation DEFERRED until chunk loads");
            WFCore.LOGGER.info(
                    "Ballistics: VIRTUAL {} ({}) HIT block {} [center {}, face {}] in {} — impact chunk ({},{}) "
                            + "loaded={}, flew {} ticks -> {}",
                    v.id, v.adapterId, hp, v.impactPos, face, level.dimension().location(), cx, cz, loaded, v.age,
                    outcome);
        }
        if (di != null) {
            if (loaded) {
                di.apply(level);
            } else {
                long key = chunkKey(cx, cz);
                List<DeferredImpact> bucket = deferred.get(key);
                if (bucket == null) {
                    bucket = new ObjectArrayList<>();
                    deferred.put(key, bucket);
                }
                bucket.add(di);
            }
        }
        data.remove(v.id);
    }

    public void drainDeferred(int cx, int cz) {
        if (deferred.isEmpty()) {
            return;
        }
        List<DeferredImpact> bucket = deferred.remove(chunkKey(cx, cz));
        if (bucket == null) {
            return;
        }
        if (WFCoreConfig.isBallisticsDebugLogging()) {
            WFCore.LOGGER.info("Ballistics: chunk ({},{}) in {} loaded — firing {} deferred impact(s) that hit while unloaded",
                    cx, cz, level.dimension().location(), bucket.size());
        }
        for (int i = 0, n = bucket.size(); i < n; i++) {
            bucket.get(i).apply(level);
        }
    }
}
