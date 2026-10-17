package com.norwood.wfcore.client;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.norwood.wfcore.common.rank.PlayerRankUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.particle.BlockDebrisParticle;
import com.norwood.wfcore.client.particle.SmokePlumeParticle;
import com.norwood.wfcore.client.render.DepositBlockEntityRenderer;
import com.norwood.wfcore.client.render.FoundryCastingRenderer;
import com.norwood.wfcore.client.render.MissileLauncherRenderer;
import com.norwood.wfcore.client.render.gltf.GemRenderMachineVisual;
import com.norwood.wfcore.client.render.gltf.MachineModel;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import com.norwood.wfcore.common.particle.WFParticles;

@EventBusSubscriber(modid = WFCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFClientEvents {

    // Declaring these in static fields is the whole of the registration: GemRender treats a declared
    // handle as an asset it must import, and does the import at the end of a resource reload on the
    // render thread. Nothing is loaded here, and nothing is loaded by whichever visual asks first.

    /** Shared radar dish model; the mesh, atlas and clips load once and every radar visual reuses them. */
    public static final MachineModel RADAR_MODEL = new MachineModel(WFCore.id("model/radar.gltf"));

    /** Shared drilling-rig model; states {@code idle}/{@code running} driven by the drill rig machine. */
    public static final MachineModel DRILL_RIG_MODEL = new MachineModel(WFCore.id("model/drill_rig.gltf"));

    /** Shared research-unit model; every research unit visual reuses the same imported asset. */
    public static final MachineModel RESEARCH_MODEL = new MachineModel(WFCore.id("model/research_render.gltf"));

    /** Shared iron-dome model for the interceptor battery; rides above the controller's casing. */
    public static final MachineModel IRON_DOME_MODEL = new MachineModel(WFCore.id("model/iron_dome.gltf"));

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        PlayerRankUtil.init();

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
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<MissileLauncherMachine>) WFMachines.MISSILE_LAUNCHER.getBlockEntityType(),
                MissileLauncherRenderer::new);
        event.registerBlockEntityRenderer(WFBlocks.DEPOSIT_BE.get(), DepositBlockEntityRenderer::new);
        event.registerBlockEntityRenderer(WFBlocks.FOUNDRY_CASTING_BE.get(), FoundryCastingRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpecial(WFParticles.BLOCK_DEBRIS.get(), new BlockDebrisParticle.Provider());
        event.registerSpriteSet(WFParticles.SMOKE_PLUME.get(), SmokePlumeParticle.Provider::new);
        event.registerSpriteSet(WFParticles.STRANDCASTER_STEAM.get(),
                com.norwood.wfcore.client.particle.SteamPlumeParticle.Provider::new);
    }
}
