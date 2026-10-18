package com.norwood.wfcore.client;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.camera.ThirdPersonPolicy;

/** One server's third-person rule must not carry into the next connection. */
@EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class ThirdPersonPolicyReset {

    private ThirdPersonPolicyReset() {}

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ThirdPersonPolicy.reset();
    }
}
