package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.ClassDumpService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;


public record ClassDumpViewRequestMessage(long requestId, String fileName) {

    public static void write(ClassDumpViewRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.requestId);
        buf.writeUtf(msg.fileName, 256);
    }

    public static ClassDumpViewRequestMessage read(FriendlyByteBuf buf) {
        return new ClassDumpViewRequestMessage(buf.readLong(), buf.readUtf(256));
    }

    public static void handle(ClassDumpViewRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> ClassDumpService.INSTANCE.onViewRequest(sender, msg));
        context.setPacketHandled(true);
    }
}
