package com.norwood.wfcore.diagnostics;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;


public record ModListRequestMessage(long nonce, List<String> signatures) {

    private static final int MAX_SIGNATURES = 512;
    private static final int MAX_SIGNATURE_LEN = 256;

    public static void write(ModListRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        List<String> sigs = msg.signatures != null ? msg.signatures : List.of();
        int n = Math.min(sigs.size(), MAX_SIGNATURES);
        buf.writeVarInt(n);
        for (int i = 0; i < n; i++) {
            buf.writeUtf(sigs.get(i), MAX_SIGNATURE_LEN);
        }
    }

    public static ModListRequestMessage read(FriendlyByteBuf buf) {
        long nonce = buf.readLong();
        int n = buf.readVarInt();
        if (n < 0 || n > MAX_SIGNATURES) {
            return new ModListRequestMessage(nonce, List.of());
        }
        List<String> sigs = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            sigs.add(buf.readUtf(MAX_SIGNATURE_LEN));
        }
        return new ModListRequestMessage(nonce, sigs);
    }

    public static void handle(ModListRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.ModAuditClient.onRequest(msg)));
        context.setPacketHandled(true);
    }
}
