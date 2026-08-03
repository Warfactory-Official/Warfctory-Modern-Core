package com.norwood.wfcore.client.shader;

import com.norwood.wfcore.common.shader.ShaderEnforcement;

import net.minecraftforge.fml.ModList;


public final class ShaderClientApply {

    private ShaderClientApply() {}

    public static void apply(boolean block) {
        boolean changed = ShaderEnforcement.set(block);
        if (changed && ModList.get().isLoaded("oculus")) {
            OculusBridge.reload();
        }
    }
}
