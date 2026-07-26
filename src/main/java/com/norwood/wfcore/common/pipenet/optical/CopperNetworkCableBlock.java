package com.norwood.wfcore.common.pipenet.optical;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.registry.registrate.provider.GTBlockstateProvider;
import com.gregtechceu.gtceu.client.model.pipe.ActivablePipeModel;
import com.gregtechceu.gtceu.client.model.pipe.PipeModel;
import com.gregtechceu.gtceu.common.block.OpticalPipeBlock;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeProperties;
import com.gregtechceu.gtceu.common.pipelike.optical.OpticalPipeType;

import net.minecraft.world.level.block.entity.BlockEntityType;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.WFBlocks;

import org.jetbrains.annotations.NotNull;

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

    /**
     * Mirrors {@link OpticalPipeBlock#createPipeModel} but swaps the cable body (the {@code side} texture) for our own
     * blue {@code copper_network_cable_side}. The connection ends ({@code in}) and the glowing grid overlay (plus its
     * animated {@code _active} variant) are deliberately kept from optical fibre so the cable still reads as part of the
     * optical/network family and retains the "data flowing" glow when active.
     */
    @Override
    public @NotNull PipeModel createPipeModel(GTBlockstateProvider provider) {
        ActivablePipeModel pipeModel = new ActivablePipeModel(this, pipeType.getThickness(),
                WFCore.id("block/pipe/copper_network_cable_side"), GTCEu.id("block/pipe/pipe_optical_in"),
                provider);
        pipeModel.setSideOverlay(GTCEu.id("block/pipe/pipe_optical_side_overlay"));
        pipeModel.setSideOverlayActive(GTCEu.id("block/pipe/pipe_optical_side_overlay_active"));
        return pipeModel;
    }
}
