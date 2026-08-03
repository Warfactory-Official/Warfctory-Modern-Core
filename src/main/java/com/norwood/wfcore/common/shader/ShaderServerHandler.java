package com.norwood.wfcore.common.shader;

import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.diagnostics.DiagNet;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;


public final class ShaderServerHandler {

    public static final ShaderServerHandler INSTANCE = new ShaderServerHandler();

    private ShaderServerHandler() {}

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        MinecraftServer server = player.getServer();
        boolean block = WFCoreConfig.isBlockClientShaders()
                && WFCoreConfig.isTrueDarknessEnabled()
                && server != null
                && server.isDedicatedServer();
        DiagNet.sendShaderEnforce(player, new ShaderEnforceMessage(block));
    }
}
