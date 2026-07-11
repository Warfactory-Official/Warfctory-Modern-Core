package com.norwood.wfcore.client;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.modularmods.mcgltf.MCglTF;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.particle.BlockDebrisParticle;
import com.norwood.wfcore.client.particle.SmokePlumeParticle;
import com.norwood.wfcore.client.render.DepositBlockEntityRenderer;
import com.norwood.wfcore.client.render.gltf.GltfMachineRenderer;
import com.norwood.wfcore.client.render.gltf.MachineGltfModel;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.DrillRigBlockEntity;
import com.norwood.wfcore.common.machine.RadarBlockEntity;
import com.norwood.wfcore.common.machine.ResearchUnitBlockEntity;
import com.norwood.wfcore.common.particle.WFParticles;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFClientEvents {

    /** Shared radar dish model; the GLTF scene + animations load once and every radar BER reuses them. */
    public static final MachineGltfModel RADAR_MODEL = new MachineGltfModel(WFCore.id("model/radar.gltf"));

    /** Shared drilling-rig model; states {@code idle}/{@code running} driven by the drill rig machine. */
    public static final MachineGltfModel DRILL_RIG_MODEL = new MachineGltfModel(WFCore.id("model/drill_rig.gltf"));

    /** Shared research-unit model; every research unit BER reuses the same loaded GLTF scene. */
    public static final MachineGltfModel RESEARCH_MODEL = new MachineGltfModel(WFCore.id("model/research_render.gltf"));

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Register the model receivers so McGLTF loads them on (re)load and hands back the GPU scenes.
        event.enqueueWork(() -> {
            MCglTF.getInstance().addGltfModelReceiver(RADAR_MODEL);
            MCglTF.getInstance().addGltfModelReceiver(DRILL_RIG_MODEL);
            MCglTF.getInstance().addGltfModelReceiver(RESEARCH_MODEL);
        });
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<RadarBlockEntity>) WFMachines.RADAR.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(RADAR_MODEL));
        event.registerBlockEntityRenderer(
                (BlockEntityType<DrillRigBlockEntity>) WFMachines.DRILL_RIG.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(DRILL_RIG_MODEL));
        event.registerBlockEntityRenderer(
                (BlockEntityType<ResearchUnitBlockEntity>) WFMachines.RESEARCH_UNIT.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(RESEARCH_MODEL));
        event.registerBlockEntityRenderer(WFBlocks.DEPOSIT_BE.get(), DepositBlockEntityRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticleProviders(RegisterParticleProvidersEvent event) {
        event.registerSpecial(WFParticles.BLOCK_DEBRIS.get(), new BlockDebrisParticle.Provider());
        event.registerSpriteSet(WFParticles.SMOKE_PLUME.get(), SmokePlumeParticle.Provider::new);
    }
}
