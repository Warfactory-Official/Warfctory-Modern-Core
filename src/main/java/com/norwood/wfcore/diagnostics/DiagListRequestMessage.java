package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.DiagnosticsService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** The viewer's Refresh button: re-scan the screenshot directories and send a fresh {@link DiagCatalogMessage}. */
public record DiagListRequestMessage() {

    public static final DiagListRequestMessage INSTANCE = new DiagListRequestMessage();

    public static void write(DiagListRequestMessage msg, FriendlyByteBuf buf) {}

    public static DiagListRequestMessage read(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(DiagListRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> DiagnosticsService.INSTANCE.onListRequest(sender));
        context.setPacketHandled(true);
    }
}
