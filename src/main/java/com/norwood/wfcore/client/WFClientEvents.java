package com.norwood.wfcore.client;

import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import com.modularmods.mcgltf.MCglTF;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.gltf.GltfMachineRenderer;
import com.norwood.wfcore.client.render.gltf.MachineGltfModel;
import com.norwood.wfcore.common.data.WFMachines;
import com.norwood.wfcore.common.machine.RadarBlockEntity;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFClientEvents {

    /** Shared radar dish model; the GLTF scene + animations load once and every radar BER reuses them. */
    public static final MachineGltfModel RADAR_MODEL = new MachineGltfModel(WFCore.id("model/radar.gltf"));

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        // Register the model receiver so McGLTF loads the dish on (re)load and hands back the GPU scene.
        event.enqueueWork(() -> MCglTF.getInstance().addGltfModelReceiver(RADAR_MODEL));
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(
                (BlockEntityType<RadarBlockEntity>) WFMachines.RADAR.getBlockEntityType(),
                ctx -> new GltfMachineRenderer<>(RADAR_MODEL));
    }
}
