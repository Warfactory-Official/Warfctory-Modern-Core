package com.norwood.wfcore.client;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.norwood.wfcore.client.render.FoundryCastingRenderer;
import com.norwood.wfcore.client.render.MissileLauncherRenderer;
import com.norwood.wfcore.client.render.gltf.GemRenderMachineVisual;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

import static com.norwood.wfcore.client.WFClientEvents.DRILL_RIG_MODEL;
import static com.norwood.wfcore.client.WFClientEvents.IRON_DOME_MODEL;
import static com.norwood.wfcore.client.WFClientEvents.RADAR_MODEL;
import static com.norwood.wfcore.client.WFClientEvents.RESEARCH_MODEL;

/** The client side of the GregTech machines: their animated visuals and block entity renderers. Called from {@link WFClientEvents} only with GregTech loaded. */
final class WFClientEventsGT {
    private WFClientEventsGT() {
    }

    static void visuals(FMLClientSetupEvent event) {
        // The animated machines are drawn by Flywheel visuals rather than block entity renderers, so
        // they are registered here instead of in RegisterRenderers. neverSkipVanillaRender because the
        // model is an addition to the machine block, not a replacement for it — the structure blocks it
        // covers are hidden through the render mask instead.
        event.enqueueWork(() -> {
            visualize(WFMachines.RADAR.getBlockEntityType(),
                    GemRenderMachineVisual.of(RADAR_MODEL, new BlockPos(0, 15, 0)));
            visualize(WFMachines.DRILL_RIG.getBlockEntityType(),
                    GemRenderMachineVisual.of(DRILL_RIG_MODEL, new BlockPos(0, -2, 1)));
            visualize(WFMachines.RESEARCH_UNIT.getBlockEntityType(),
                    GemRenderMachineVisual.of(RESEARCH_MODEL, new BlockPos(4, 0, 3)));
            visualize(WFMachines.INTERCEPTOR.getBlockEntityType(),
                    GemRenderMachineVisual.of(IRON_DOME_MODEL, new BlockPos(0, 1, 1), new BlockPos(0, 1, 3),
                            new BlockPos(0, 3, 6)));
        });
    }

    @SuppressWarnings("unchecked")
    private static <T extends MetaMachine> void visualize(BlockEntityType<?> type,
                                                          SimpleBlockEntityVisualizer.Factory<T> factory) {
        SimpleBlockEntityVisualizer.builder((BlockEntityType<T>) type)
                .factory(factory)
                .neverSkipVanillaRender()
                .apply();
    }


    @SuppressWarnings("unchecked")
    static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(WFBlocks.DEPOSIT_BE.get(), com.norwood.wfcore.client.render.DepositBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(
                (BlockEntityType<MissileLauncherMachine>) WFMachines.MISSILE_LAUNCHER.getBlockEntityType(),
                MissileLauncherRenderer::new);
        event.registerBlockEntityRenderer((BlockEntityType<com.norwood.wfcore.common.machine.FoundryCastingBlockEntity>) WFBlocks.FOUNDRY_CASTING_BE.get(), FoundryCastingRenderer::new);
    }
}
