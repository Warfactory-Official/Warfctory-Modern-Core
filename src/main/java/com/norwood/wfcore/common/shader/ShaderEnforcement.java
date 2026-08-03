package com.norwood.wfcore.common.shader;


public final class ShaderEnforcement {

    private static volatile boolean blockShaders = false;

    private ShaderEnforcement() {}


    public static boolean set(boolean block) {
        if (blockShaders == block) {
            return false;
        }
        blockShaders = block;
        return true;
    }

    public static boolean blockShaders() {
        return blockShaders;
    }
}
