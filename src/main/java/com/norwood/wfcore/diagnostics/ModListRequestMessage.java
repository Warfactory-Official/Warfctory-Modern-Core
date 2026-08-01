package com.norwood.wfcore.diagnostics;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server -> client: asks the client to hash its mods-folder jars and reply with a {@link ModReportMessage}.
 * The {@code nonce} ties a reply back to this request so the server ignores unsolicited or stale reports.
 */
public record ModListRequestMessage(long nonce) {

    public static void write(ModListRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
    }

    public static ModListRequestMessage read(FriendlyByteBuf buf) {
        return new ModListRequestMessage(buf.readLong());
    }

    public static void handle(ModListRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.ModAuditClient.onRequest(msg)));
        context.setPacketHandled(true);
    }
}
