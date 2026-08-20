package com.norwood.wfcore.client.rank;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.rank.PlayerRankUtil;
import com.norwood.wfcore.config.WFCoreConfig;

import java.util.UUID;


@EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class RankChatDecorator {

    private RankChatDecorator() {}

    @SubscribeEvent
    public static void onPlayerChat(ClientChatReceivedEvent.Player event) {
        UUID sender = event.getSender();
        if (PlayerRankUtil.getRanks(sender).isEmpty()) {
            return;
        }

        Component prefix = WFCoreConfig.isShowChatTags() ? PlayerRankUtil.chatTag(sender) : null;
        boolean rewrite = PlayerRankUtil.hasChatContentTransform(sender);
        if (prefix == null && !rewrite) {
            return;
        }

        Component body;
        if (rewrite) {
            PlayerChatMessage message = event.getPlayerChatMessage();
            String content = message.signedContent();
            String transformed = PlayerRankUtil.transformChatContent(sender, content);
            body = event.getBoundChatType().decorate(Component.literal(transformed));
        } else {
            body = event.getMessage();
        }

        if (prefix != null) {
            body = Component.empty().append(prefix).append(Component.literal(" ")).append(body);
        }
        event.setMessage(body);
    }
}
