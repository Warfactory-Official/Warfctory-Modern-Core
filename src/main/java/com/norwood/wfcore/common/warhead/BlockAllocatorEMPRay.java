package com.norwood.wfcore.common.warhead;

import com.wf.wfballistics.aef.ExplosionAEF;
import com.wf.wfballistics.aef.interfaces.IBlockAllocator;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;

/**
 * Pinpoint EMP allocator: instead of a spherical volume, it collects the block-entity positions inside a narrow
 * <em>ray</em> fired down the warhead's {@code axis} (the missile's impact heading). Paired with WF-B's
 * {@code BlockProcessorEMP}, this is a lance that reaches out {@code length} blocks and disables the machines
 * along its path — no area denial, no terrain damage — the penetrator-EMP role.
 *
 * <p>The beam has a <b>2&times;2</b> cross-section: at each step along the axis it samples the four columns of a
 * 2&times;2 grid taken in the plane perpendicular to the beam (via an orthonormal basis), so the width is a true
 * 2&times;2 regardless of the impact heading — not just an axis-aligned box.
 */
public class BlockAllocatorEMPRay implements IBlockAllocator {

    // Half-block offsets in the perpendicular plane: the four give a 2x2 footprint around the beam centre.
    private static final double[] CROSS = {-0.5, 0.5};

    private final Vec3 axis;
    private final double length;

    public BlockAllocatorEMPRay(Vec3 axis, double length) {
        this.axis = axis == null || axis.lengthSqr() < 1.0e-8 ? new Vec3(0, -1, 0) : axis.normalize();
        this.length = length;
    }

    @Override
    public Set<BlockPos> allocate(ExplosionAEF explosion, Level level, double x, double y, double z, float size) {
        Set<BlockPos> out = new HashSet<>();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

        // Orthonormal basis (u, v) spanning the plane perpendicular to the beam, so the 2x2 cross-section holds
        // at any heading (the lance dives near-vertical, but this stays correct for an angled strike too).
        Vec3 up = Math.abs(axis.y) < 0.999 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = up.cross(axis).normalize();
        Vec3 v = axis.cross(u);

        for (double t = 0.0; t <= length; t += 0.5) {
            double cx = x + axis.x * t;
            double cy = y + axis.y * t;
            double cz = z + axis.z * t;
            for (double ou : CROSS) {
                for (double ov : CROSS) {
                    int bx = Mth.floor(cx + u.x * ou + v.x * ov);
                    int by = Mth.floor(cy + u.y * ou + v.y * ov);
                    int bz = Mth.floor(cz + u.z * ou + v.z * ov);
                    cursor.set(bx, by, bz);
                    if (level.getBlockEntity(cursor) != null) {
                        out.add(cursor.immutable());
                    }
                }
            }
        }
        return out;
    }
}
