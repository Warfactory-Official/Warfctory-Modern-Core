package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.WFCore;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

@SuppressWarnings("removal")
public final class DiagNet {

    private static final String PROTOCOL = "1";

    public static final ResourceLocation CHANNEL_ID = new ResourceLocation(WFCore.MOD_ID, "diag");

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_ID,
            () -> PROTOCOL,
            PROTOCOL::equals,
            PROTOCOL::equals);

    private static boolean registered;

    private DiagNet() {}

    public static synchronized void register() {
        if (registered) {
            return;
        }
        registered = true;

        CHANNEL.registerMessage(0, DiagRequestMessage.class,
                DiagRequestMessage::write, DiagRequestMessage::read, DiagRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(1, DiagHeaderMessage.class,
                DiagHeaderMessage::write, DiagHeaderMessage::read, DiagHeaderMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(2, DiagChunkMessage.class,
                DiagChunkMessage::write, DiagChunkMessage::read, DiagChunkMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    public static void sendToClient(ServerPlayer player, DiagRequestMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }
}
