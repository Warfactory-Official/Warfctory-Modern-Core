package com.norwood.wfcore.client;

import com.norwood.wfcore.common.rank.PlayerRankUtil;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;


import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.particle.BlockDebrisParticle;
import com.norwood.wfcore.client.particle.SmokePlumeParticle;
import com.norwood.wfcore.client.render.gltf.MachineModel;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.integration.gt.GTIntegration;
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
        if (com.norwood.wfcore.integration.warforge.WarforgeIntegration.isLoaded()) {
            NamePlateVisibility.installWarforgePolicy();
        }

        if (GTIntegration.isLoaded()) {
            WFClientEventsGT.visuals(event);
        }
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        if (GTIntegration.isLoaded()) {
            WFClientEventsGT.renderers(event);
        }
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpecial(WFParticles.BLOCK_DEBRIS.get(), new BlockDebrisParticle.Provider());
        event.registerSpriteSet(WFParticles.SMOKE_PLUME.get(), SmokePlumeParticle.Provider::new);
        event.registerSpriteSet(WFParticles.STRANDCASTER_STEAM.get(),
                com.norwood.wfcore.client.particle.SteamPlumeParticle.Provider::new);
    }
}
