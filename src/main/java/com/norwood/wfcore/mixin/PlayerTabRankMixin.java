package com.norwood.wfcore.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

import com.mojang.authlib.GameProfile;
import com.norwood.wfcore.common.rank.PlayerRankUtil;
import com.norwood.wfcore.config.WFCoreConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(PlayerTabOverlay.class)
public abstract class PlayerTabRankMixin {

    @Inject(method = "getNameForDisplay", at = @At("RETURN"), cancellable = true)
    private void wfcore$prependRankTag(PlayerInfo playerInfo, CallbackInfoReturnable<Component> cir) {
        if (!WFCoreConfig.isShowTabTags()) {
            return;
        }
        GameProfile profile = playerInfo.getProfile();
        Component tag = PlayerRankUtil.chatTag(profile.getId());
        if (tag == null) {
            return;
        }
        cir.setReturnValue(Component.empty().append(tag).append(Component.literal(" ")).append(cir.getReturnValue()));
    }
}
