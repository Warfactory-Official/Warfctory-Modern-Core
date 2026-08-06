package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.darkness.DarknessEnforceMessage;
import com.norwood.wfcore.common.research.ResearchSyncMessage;
import com.norwood.wfcore.common.shader.ShaderEnforceMessage;

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

        CHANNEL.registerMessage(3, ModListRequestMessage.class,
                ModListRequestMessage::write, ModListRequestMessage::read, ModListRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(4, ModReportMessage.class,
                ModReportMessage::write, ModReportMessage::read, ModReportMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(5, DarknessEnforceMessage.class,
                DarknessEnforceMessage::write, DarknessEnforceMessage::read, DarknessEnforceMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(6, ShaderEnforceMessage.class,
                ShaderEnforceMessage::write, ShaderEnforceMessage::read, ShaderEnforceMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(7, DiagCatalogMessage.class,
                DiagCatalogMessage::write, DiagCatalogMessage::read, DiagCatalogMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(8, DiagListRequestMessage.class,
                DiagListRequestMessage::write, DiagListRequestMessage::read, DiagListRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(9, DiagImageRequestMessage.class,
                DiagImageRequestMessage::write, DiagImageRequestMessage::read, DiagImageRequestMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_SERVER));

        CHANNEL.registerMessage(10, DiagImageHeaderMessage.class,
                DiagImageHeaderMessage::write, DiagImageHeaderMessage::read, DiagImageHeaderMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(11, DiagImageChunkMessage.class,
                DiagImageChunkMessage::write, DiagImageChunkMessage::read, DiagImageChunkMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));

        CHANNEL.registerMessage(12, ResearchSyncMessage.class,
                ResearchSyncMessage::write, ResearchSyncMessage::read, ResearchSyncMessage::handle,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void sendToClient(ServerPlayer player, DiagRequestMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendModListRequest(ServerPlayer player, ModListRequestMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendDarknessEnforce(ServerPlayer player, DarknessEnforceMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendShaderEnforce(ServerPlayer player, ShaderEnforceMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendCatalog(ServerPlayer player, DiagCatalogMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendImageHeader(ServerPlayer player, DiagImageHeaderMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    public static void sendImageChunk(ServerPlayer player, DiagImageChunkMessage message) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), message);
    }

    /** Sends the full research tree (categories + nodes) to a client — on login and after {@code /reload}. */
    public static void sendResearchRegistry(ServerPlayer player) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), ResearchSyncMessage.snapshot());
    }
}
