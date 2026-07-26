package com.norwood.wfcore.common.pipenet.optical;

import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.common.block.OpticalPipeBlock;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeProperties;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeType;

import net.minecraft.world.level.block.entity.BlockEntityType;

import com.norwood.wfcore.common.data.WFBlocks;

/**
 * Copper Network Cable — a cheap, MV-tier alternative to GregTech's optical fibre.
 *
 * <p>
 * It extends {@link OpticalPipeBlock} and deliberately does NOT override {@code getWorldPipeNet}, so it joins
 * the very same dimension-wide {@code LevelOpticalPipeNet} as optical fibre. Consequences (all free):
 * <ul>
 *   <li>it carries both computation (CWU) and optical data;</li>
 *   <li>it interconnects with optical fibre (GregTech's {@code canPipesConnect} matches any
 *       {@code OpticalPipeBlockEntity}, which this block's BE is);</li>
 *   <li>it works with every existing computation/data machine — Computation Mainframe, Research Unit,
 *       Data Bank — with no extra wiring.</li>
 * </ul>
 * Only the block-entity type differs (so this block gets its own BE + item); the pipe net, connection rules,
 * properties and model are all inherited. Crafted from thin copper strands (fine copper wire) sheathed in
 * rubber.
 */
public class CopperNetworkCableBlock extends OpticalPipeBlock {

    public CopperNetworkCableBlock(Properties properties, OpticalPipeType type) {
        super(properties, type);
    }

    @Override
    public BlockEntityType<? extends PipeBlockEntity<OpticalPipeType, OpticalPipeProperties>> getBlockEntityType() {
        return WFBlocks.COPPER_NETWORK_CABLE_BE.get();
    }
}
