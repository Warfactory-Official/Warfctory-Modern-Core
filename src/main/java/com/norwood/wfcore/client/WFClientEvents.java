package com.norwood.wfcore.client;

import com.norwood.wfcore.common.rank.PlayerRankUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import com.modularmods.mcgltf.MCglTF;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.particle.BlockDebrisParticle;
import com.norwood.wfcore.client.particle.SmokePlumeParticle;
import com.norwood.wfcore.client.render.DepositBlockEntityRenderer;
import com.norwood.wfcore.client.render.FoundryCastingRenderer;
import com.norwood.wfcore.client.render.MissileLauncherRenderer;
import com.norwood.wfcore.client.render.gltf.GltfMachineRenderer;
import com.norwood.wfcore.client.render.gltf.MachineGltfModel;
import com.norwood.wfcore.common.data.WFBlocks;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.DrillRigMachine;
import com.norwood.wfcore.common.machine.InterceptorMachine;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import com.norwood.wfcore.common.machine.RadarMachine;
import com.norwood.wfcore.common.machine.ResearchUnitMachine;
import com.norwood.wfcore.common.particle.WFParticles;

@EventBusSubscriber(modid = WFCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFClientEvents {

    /** Shared radar dish model; the GLTF scene + animations load once and every radar BER reuses them. */
    public static final MachineGltfModel RADAR_MODEL = new MachineGltfModel(WFCore.id("model/radar.gltf"));

    /** Shared drilling-rig model; states {@code idle}/{@code running} driven by the drill rig machine. */
    public static final MachineGltfModel DRILL_RIG_MODEL = new MachineGltfModel(WFCore.id("model/drill_rig.gltf"));

    /** Shared research-unit model; every research unit BER reuses the same loaded GLTF scene. */
    public static final MachineGltfModel RESEARCH_MODEL = new MachineGltfModel(WFCore.id("model/research_render.gltf"));

    /** Shared iron-dome model for the interceptor battery; rides above the controller's casing. */
    public static final MachineGltfModel IRON_DOME_MODEL = new MachineGltfModel(WFCore.id("model/iron_dome.gltf"));

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
       PlayerRankUtil.init();

        // Register the model receivers so McGLTF loads them on (re)load and hands back the GPU scenes.
        event.enqueueWork(() -> {
            MCglTF.getInstance().addGltfModelReceiver(RADAR_MODEL);
            MCglTF.getInstance().addGltfModelReceiver(DRILL_RIG_MODEL);
            MCglTF.getInstance().addGltfModelReceiver(RESEARCH_MODEL);
            MCglTF.getInstance().addGltfModelReceiver(IRON_DOME_MODEL);
        });
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<RadarMachine>) WFMachines.RADAR.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(RADAR_MODEL,new BlockPos(0,15,0)));
        event.registerBlockEntityRenderer(
                (BlockEntityType<DrillRigMachine>) WFMachines.DRILL_RIG.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(DRILL_RIG_MODEL,new BlockPos(0,-2,1)));
        event.registerBlockEntityRenderer(
                (BlockEntityType<ResearchUnitMachine>) WFMachines.RESEARCH_UNIT.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(RESEARCH_MODEL,new BlockPos(4,0,3)));
        event.registerBlockEntityRenderer(
                (BlockEntityType<MissileLauncherMachine>) WFMachines.MISSILE_LAUNCHER.getBlockEntityType(),
                MissileLauncherRenderer::new);
        event.registerBlockEntityRenderer(
                (BlockEntityType<InterceptorMachine>) WFMachines.INTERCEPTOR.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(IRON_DOME_MODEL,new BlockPos(0,1,1),new BlockPos(0,1,3),new BlockPos(0,3,6)));
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
