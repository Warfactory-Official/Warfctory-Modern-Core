package com.norwood.wfcore.client.title;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.sounds.Music;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.client.event.SelectMusicEvent;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.sound.WFSounds;

@EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class TitleEvents {
    private static Music menuMusic;

    private TitleEvents() {
    }

    @SubscribeEvent
    static void onScreen(ScreenEvent.Opening event) {
        if (event.getNewScreen().getClass() == TitleScreen.class) {
            event.setNewScreen(new WFTitleScreen());
        }
    }

    /** Menu track on every screen while no level is loaded (world and server lists included). */
    @SubscribeEvent
    static void onMusic(SelectMusicEvent event) {
        if (Minecraft.getInstance().level == null) {
            if (menuMusic == null) {
                menuMusic = new Music(WFSounds.TITLE_MUSIC, 0, 0, false);
            }
            event.overrideMusic(menuMusic);
        }
    }

    @SubscribeEvent
    static void onJoin(ClientPlayerNetworkEvent.LoggingIn event) {
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }
}
