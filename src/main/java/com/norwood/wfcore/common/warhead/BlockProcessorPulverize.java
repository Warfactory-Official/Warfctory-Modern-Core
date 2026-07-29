package com.norwood.wfcore.common.warhead;

import com.wf.wfballistics.aef.ExplosionAEF;
import com.wf.wfballistics.aef.interfaces.IBlockProcessor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;

/**
 * Bunker-buster block processor: everything the (shaped-charge) allocator reaches is either <em>broken</em> or
 * <em>cracked</em>, split by explosion resistance.
 *
 * <ul>
 *   <li>Blocks below {@code crackThreshold} — soft enough to defeat — are destroyed outright (no drops).</li>
 *   <li>Blocks at or above {@code crackThreshold} — armour the jet can't punch clean through — are converted to
 *       {@code cracked} (gravel by default) instead of surviving intact. That's the bunker buster's signature:
 *       it can't finish hardened defences alone, but it <em>weakens</em> them so a follow-up HE/demolition round
 *       levels what's left.</li>
 * </ul>
 *
 * <p>Raising {@code crackThreshold} across tiers is what lets a heavier bunker buster break blocks a lighter one
 * could only crack (e.g. a Mk1 cracks hardened steel + tungsten to gravel; a Mk2 breaks the steel and only
 * cracks the tungsten). Resistance is read live from the world so it honours any exploder/claim overrides the
 * allocator already applied.
 */
public class BlockProcessorPulverize implements IBlockProcessor {

    private final float crackThreshold;
    private final BlockState cracked;

    public BlockProcessorPulverize(float crackThreshold) {
        this(crackThreshold, Blocks.GRAVEL.defaultBlockState());
    }

    public BlockProcessorPulverize(float crackThreshold, BlockState cracked) {
        this.crackThreshold = crackThreshold;
        this.cracked = cracked;
    }

    @Override
    public void process(ExplosionAEF explosion, Level level, double x, double y, double z, Set<BlockPos> affectedBlocks) {
        for (BlockPos pos : affectedBlocks) {
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            float resistance = state.getExplosionResistance(level, pos, explosion.compat);
            if (resistance >= crackThreshold) {
                // Too tough to punch through — crack it to rubble so a follow-up charge can finish the job.
                if (!state.is(cracked.getBlock())) {
                    level.setBlock(pos, cracked, 3);
                }
            } else {
                // Defeated outright: remove it, no drops (matches the destructive warheads' setNoDrop).
                level.destroyBlock(pos, false);
            }
        }
    }
}
