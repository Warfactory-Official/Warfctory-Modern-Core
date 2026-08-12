package com.norwood.wfcore.client;

import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class BoatCameraLock {

    private static boolean locked;

    private static CameraType restoreType;

    private BoatCameraLock() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;

        Minecraft mc = Minecraft.getInstance();
        boolean inBoat = mc.player != null && mc.player.getVehicle() instanceof Boat;

        if (inBoat && WFCoreConfig.isBoatThirdPersonDisabled()) {
            if (!locked) {
                locked = true;
                restoreType = mc.options.getCameraType();
            }
            setCameraType(mc, CameraType.FIRST_PERSON);
            return;
        }

        if (locked) {
            locked = false;
            CameraType wanted = restoreType;
            restoreType = null;
            if (wanted != null && mc.options.getCameraType() == CameraType.FIRST_PERSON) {
                setCameraType(mc, wanted);
            }
        }
    }

    private static void setCameraType(Minecraft mc, CameraType type) {
        CameraType previous = mc.options.getCameraType();
        if (previous == type) return;

        mc.options.setCameraType(type);
        if (mc.level == null) return;

        if (previous.isFirstPerson() != type.isFirstPerson()) {
            mc.gameRenderer.checkEntityPostEffect(type.isFirstPerson() ? mc.getCameraEntity() : null);
        }
        mc.levelRenderer.needsUpdate();
    }
}
