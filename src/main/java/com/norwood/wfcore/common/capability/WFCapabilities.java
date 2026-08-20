package com.norwood.wfcore.common.capability;

import com.gregtechceu.gtceu.api.machine.MachineDefinition;

import net.minecraft.core.Direction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.api.capability.IACEnergyContainer;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.ACHatchPartMachine;
import com.norwood.wfcore.common.machine.ChunkReinforcerMachine;
import com.norwood.wfcore.common.machine.FoundryCastingBlockEntity;
import com.norwood.wfcore.common.pipenet.ac.ACPipeBlock;
import com.norwood.wfcore.integration.warforge.WarforgeMachines;

/**
 * WFCore-registered capabilities, and the one place every wfcore block's capability provider is attached.
 *
 * <p>NeoForge replaced Forge's {@code Capability} + {@code CapabilityToken} + {@code LazyOptional} trio
 * with typed capability objects created up front. There is no registration event for the capability
 * <em>itself</em> any more — only for the blocks that provide it, and a block entity can no longer answer
 * a capability query from its own {@code getCapability} override. Everything that used to do that is
 * registered from {@link #registerCapabilities} instead.
 */
@EventBusSubscriber(modid = WFCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WFCapabilities {

    /** Sided because AC cables are point-to-point: a face either carries the net or it does not. */
    public static final BlockCapability<IACEnergyContainer, Direction> CAPABILITY_AC_ENERGY = BlockCapability
            .createSided(WFCore.id("ac_energy"), IACEnergyContainer.class);

    private WFCapabilities() {}

    @SubscribeEvent
    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        for (var pipe : WFBlocks.AC_PIPES) {
            if (pipe != null) pipe.get().attachCapabilities(event);
        }

        ACHatchPartMachine.attachCapabilities(event, WFMachines.AC_INPUT_HATCH.getBlock());
        ACHatchPartMachine.attachCapabilities(event, WFMachines.AC_OUTPUT_HATCH.getBlock());

        for (MachineDefinition definition : WarforgeMachines.chunkReinforcers()) {
            ChunkReinforcerMachine.attachCapabilities(event, definition.getBlock());
        }

        // The foundry casting basin used to answer ForgeCapabilities.FLUID_HANDLER/ITEM_HANDLER from its own
        // getCapability override; those are now block-level providers keyed off its block entity type.
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, WFBlocks.FOUNDRY_CASTING_BE.get(),
                (blockEntity, side) -> blockEntity.getFluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, WFBlocks.FOUNDRY_CASTING_BE.get(),
                (blockEntity, side) -> blockEntity.getItemHandler());
    }
}
