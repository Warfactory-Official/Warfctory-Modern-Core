package com.norwood.wfcore.client.debug;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.mojang.blaze3d.platform.InputConstants;
import com.norwood.wfcore.WFCore;

/**
 * Key mappings for the {@link KmodoDebugHandler} Kmodo Accelerator debug overlay. The toggle key is
 * left UNBOUND by default so it never interferes with normal play — bind it in Controls if you need
 * it. The dump key fires when the toggle is already on; pressing the same mapping twice in quick
 * succession toggles the overlay and immediately dumps.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KmodoDebugKeyMappings {

    private KmodoDebugKeyMappings() {}

    private static final String CATEGORY = "key.categories.wfcore.debug";

    /**
     * Toggle the Kmodo debug overlay on/off. When toggled ON, also dumps the current stats to chat
     * and log immediately. Default: UNBOUND.
     */
    public static final KeyMapping TOGGLE = new KeyMapping(
            "key.wfcore.kmodo_debug.toggle",
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    /**
     * Dump the current Kmodo stats to chat and log while the overlay is already enabled. Useful if
     * you want a fresh snapshot without toggling off. Default: UNBOUND.
     */
    public static final KeyMapping DUMP = new KeyMapping(
            "key.wfcore.kmodo_debug.dump",
            InputConstants.UNKNOWN.getValue(),
            CATEGORY);

    @SubscribeEvent
    public static void onRegister(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(DUMP);
    }
}
