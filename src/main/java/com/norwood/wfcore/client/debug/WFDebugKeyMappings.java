package com.norwood.wfcore.client.debug;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.norwood.wfcore.WFCore;
import org.lwjgl.glfw.GLFW;

/**
 * Numpad "D-pad" for {@link ModelTransformDebug}: 5 toggles, 4/6/8/2/7/9 nudge X/Y/Z, 0 cycles step
 * size, 1 switches between editing transform and scale, Enter exports, Decimal resets. Numpad is used
 * specifically because it's vanishingly unlikely to collide with any existing binding, so it needs no
 * rebinding to use out of the box.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class WFDebugKeyMappings {

    private static final String CATEGORY = "key.categories.wfcore.debug";

    public static final KeyMapping TOGGLE = key("toggle", GLFW.GLFW_KEY_KP_5);
    public static final KeyMapping X_NEG = key("x_neg", GLFW.GLFW_KEY_KP_4);
    public static final KeyMapping X_POS = key("x_pos", GLFW.GLFW_KEY_KP_6);
    public static final KeyMapping Y_POS = key("y_pos", GLFW.GLFW_KEY_KP_8);
    public static final KeyMapping Y_NEG = key("y_neg", GLFW.GLFW_KEY_KP_2);
    public static final KeyMapping Z_NEG = key("z_neg", GLFW.GLFW_KEY_KP_7);
    public static final KeyMapping Z_POS = key("z_pos", GLFW.GLFW_KEY_KP_9);
    public static final KeyMapping CYCLE_STEP = key("cycle_step", GLFW.GLFW_KEY_KP_0);
    public static final KeyMapping MODE = key("mode", GLFW.GLFW_KEY_KP_1);
    public static final KeyMapping EXPORT = key("export", GLFW.GLFW_KEY_KP_ENTER);
    public static final KeyMapping RESET = key("reset", GLFW.GLFW_KEY_KP_DECIMAL);

    private static KeyMapping key(String id, int glfwKey) {
        return new KeyMapping("key.wfcore.debug." + id, InputConstants.Type.KEYSYM, glfwKey, CATEGORY);
    }

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(X_NEG);
        event.register(X_POS);
        event.register(Y_POS);
        event.register(Y_NEG);
        event.register(Z_NEG);
        event.register(Z_POS);
        event.register(CYCLE_STEP);
        event.register(MODE);
        event.register(EXPORT);
        event.register(RESET);
    }
}
