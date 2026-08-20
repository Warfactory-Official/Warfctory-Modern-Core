package com.norwood.wfcore.diagnostics;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public record ClassDumpRequestMessage(long nonce, int maxBytes, boolean includePlatform, boolean includeDefaultPackage) {

    public static void write(ClassDumpRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.nonce);
        buf.writeVarInt(msg.maxBytes);
        buf.writeBoolean(msg.includePlatform);
        buf.writeBoolean(msg.includeDefaultPackage);
    }

    public static ClassDumpRequestMessage read(FriendlyByteBuf buf) {
        return new ClassDumpRequestMessage(buf.readLong(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean());
    }

    public static void handle(ClassDumpRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.diagnostics.client.ClassDumpClient.onRequest(msg)));
        context.setPacketHandled(true);
    }
}
