package com.norwood.wfcore.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.rank.PlayerRankUtil;
import com.replaymod.replay.ReplayHandler;
import com.replaymod.replay.ReplayModReplay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;

@Mixin(value = ReplayModReplay.class, remap = false)
public abstract class ReplayPlaybackGateMixin {

    @Inject(
            method = "startReplay(Lcom/replaymod/replaystudio/replay/ReplayFile;ZZ)Lcom/replaymod/replay/ReplayHandler;",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void wfcore$gateReplayPlayback(CallbackInfoReturnable<ReplayHandler> cir) {
        if (!PlayerRankUtil.isReplayGateActive()) {
            WFCore.LOGGER.info("wfcore: replay gate inactive (feature/replayRequiresRank off); allowing playback.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        UUID self = mc.getUser().getProfileId();
        boolean allowed = PlayerRankUtil.canReplay(self);
        WFCore.LOGGER.info("wfcore: replay gate — local user {} ({}) → {}",
                mc.getUser().getName(), self, allowed ? "ALLOWED" : "BLOCKED");
        if (allowed) {
            return;
        }
        SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.PERIODIC_NOTIFICATION,
                Component.literal("Replay locked").withStyle(ChatFormatting.RED),
                Component.literal("Replay playback requires the Press rank."));
        cir.setReturnValue(null);
    }
}
