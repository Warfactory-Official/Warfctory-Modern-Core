package com.norwood.wfcore.client.shader;

import com.norwood.wfcore.WFCore;

import net.irisshaders.iris.Iris;
import net.minecraft.client.Minecraft;


final class OculusBridge {

    private OculusBridge() {}

    static void reload() {
        Minecraft.getInstance().execute(() -> {
            try {
                Iris.reload();
            } catch (Throwable t) {
                WFCore.LOGGER.warn("[wfcore-shaderlock] Iris reload failed: {}", t.toString());
            }
        });
    }
}
