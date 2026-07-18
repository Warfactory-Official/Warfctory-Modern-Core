package com.norwood.wfcore.client.debug;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.kmodo.KmodoDebug;
import com.norwood.wfcore.client.render.kmodo.KmodoDebug.ModelStats;

@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class KmodoDebugHandler {

    private KmodoDebugHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }

        KmodoDebug.beginFrame();

        if (KmodoDebugKeyMappings.TOGGLE.consumeClick()) {
            boolean nowOn = KmodoDebug.toggle();
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                if (nowOn) {
                    mc.player.displayClientMessage(
                            Component.literal("[WFCore] ")
                                    .withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal("Kmodo debug ON — dumping stats...")
                                            .withStyle(ChatFormatting.GREEN)),
                            false);

                    dumpToChat(mc);
                } else {
                    mc.player.displayClientMessage(
                            Component.literal("[WFCore] ")
                                    .withStyle(ChatFormatting.AQUA)
                                    .append(Component.literal("Kmodo debug OFF")
                                            .withStyle(ChatFormatting.GRAY)),
                            false);
                }
            } else if (nowOn) {

                KmodoDebug.dump();
            }
        }

        if (KmodoDebug.enabled() && KmodoDebugKeyMappings.DUMP.consumeClick()) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                dumpToChat(mc);
            } else {
                KmodoDebug.dump();
            }
        }
    }

    private static void dumpToChat(Minecraft mc) {
        String full = KmodoDebug.dump();
        for (String line : full.split("\n")) {
            if (line.isBlank()) continue;
            ChatFormatting color;
            if (line.startsWith("===")) {
                color = ChatFormatting.AQUA;
            } else if (line.contains("FLYWHEEL")) {
                color = ChatFormatting.GREEN;
            } else if (line.contains("RETAINED")) {
                color = ChatFormatting.YELLOW;
            } else if (line.contains("VANILLA")) {
                color = ChatFormatting.GRAY;
            } else {
                color = ChatFormatting.WHITE;
            }
            mc.player.displayClientMessage(
                    Component.literal(line).withStyle(color), false);
        }
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (!KmodoDebug.enabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        GuiGraphics gg = event.getGuiGraphics();
        int x = 6;
        int y = 6;
        final int lineH = 10;

        gg.drawString(mc.font,
                "§b[KmodoDebug]§r (TOGGLE=off / DUMP=refresh)", x, y, 0xFFFFFF);
        y += lineH;

        if (KmodoDebug.allStats().isEmpty()) {
            gg.drawString(mc.font,
                    "§7  (no models tracked — drive a vehicle)", x, y, 0xAAAAAA);
            return;
        }

        for (ModelStats s : KmodoDebug.allStats()) {
            KmodoDebug.Mode mode = s.lastMode;
            int modeColor;
            String modeTag;
            if (mode == KmodoDebug.Mode.FLYWHEEL) {
                modeColor = 0x55FF55;
                modeTag = "FLY";
            } else if (mode == KmodoDebug.Mode.RETAINED) {
                modeColor = 0xFFFF55;
                modeTag = "RET";
            } else if (mode == KmodoDebug.Mode.VANILLA) {
                modeColor = 0xAAAAAA;
                modeTag = "VAN";
            } else {
                modeColor = 0x888888;
                modeTag = "???";
            }

            String label = s.res.getPath();
            int slash = label.lastIndexOf('/');
            if (slash >= 0) label = label.substring(slash + 1);
            if (label.endsWith(".geo.json")) label = label.substring(0, label.length() - 9);

            String detail;
            if (mode == KmodoDebug.Mode.FLYWHEEL) {
                detail = String.format(" b=%dv d=%d(%dv) %dkB live=%d drm=%d act=%d",
                        s.flywheelBodyVertices, s.flywheelDynamicBoneCount, s.flywheelDynamicVertices,
                        s.flywheelGpuBytes / 1024,
                        s.flywheelLiveInstances.get(),
                        s.dormantThisFrame.get(), s.activeThisFrame.get());
            } else if (mode == KmodoDebug.Mode.RETAINED) {
                detail = String.format(" vbos=%d verts=%d frm=%d",
                        s.retainedVboCount, s.retainedTotalVertices,
                        s.retainedFrameVehicles.get());
            } else {
                detail = "";
            }

            gg.drawString(mc.font,
                    "[" + modeTag + "] " + label + detail,
                    x, y, modeColor);
            y += lineH;
        }
    }
}
