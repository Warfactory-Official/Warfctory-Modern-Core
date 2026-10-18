package com.norwood.wfcore.net;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.camera.ThirdPersonPolicyMessage;
import com.norwood.wfcore.common.darkness.DarknessEnforceMessage;
import com.norwood.wfcore.common.research.ResearchSyncMessage;
import com.norwood.wfcore.common.shader.ShaderEnforceMessage;

/**
 * WFCore's network layer.
 *
 * <p>This replaces the 1.20.1 {@code diagnostics.DiagNet} {@code SimpleChannel}. That class multiplexed
 * 19 message ids across two unrelated concerns: the diagnostics/anti-cheat suite (ids 0-4, 7-11, 13-18)
 * and three core gameplay messages (ids 5, 6, 12). The diagnostics half is deferred — see
 * {@code deferred/README.md} — so only the channel itself and those three messages come across, and the
 * name changes with them: "diag" stopped describing what this is.
 *
 * <p>NeoForge routes by payload type rather than by channel name + integer id, so the old id numbering is
 * gone. Payload types are declared on each message record; this class only registers them and offers the
 * send helpers.
 */
@EventBusSubscriber(modid = WFCore.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public final class WFNet {

    /** Bump when any payload's wire shape changes. */
    private static final String PROTOCOL = "2";

    private WFNet() {}

    @SubscribeEvent
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL);

        registrar.playToClient(DarknessEnforceMessage.TYPE, DarknessEnforceMessage.STREAM_CODEC,
                DarknessEnforceMessage::handle);
        registrar.playToClient(ShaderEnforceMessage.TYPE, ShaderEnforceMessage.STREAM_CODEC,
                ShaderEnforceMessage::handle);
        registrar.playToClient(ResearchSyncMessage.TYPE, ResearchSyncMessage.STREAM_CODEC,
                ResearchSyncMessage::handle);
        registrar.playToClient(ThirdPersonPolicyMessage.TYPE, ThirdPersonPolicyMessage.STREAM_CODEC,
                ThirdPersonPolicyMessage::handle);
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload message) {
        PacketDistributor.sendToPlayer(player, message);
    }

    public static void sendDarknessEnforce(ServerPlayer player, DarknessEnforceMessage message) {
        sendToPlayer(player, message);
    }

    public static void sendShaderEnforce(ServerPlayer player, ShaderEnforceMessage message) {
        sendToPlayer(player, message);
    }

    /** Sends the full research tree (categories + nodes) to a client — on login and after {@code /reload}. */
    public static void sendResearchRegistry(ServerPlayer player) {
        sendToPlayer(player, ResearchSyncMessage.snapshot());
    }
}
