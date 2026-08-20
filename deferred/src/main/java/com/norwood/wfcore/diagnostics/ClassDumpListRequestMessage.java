package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.ClassDumpService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public record ClassDumpListRequestMessage() {

    public static final ClassDumpListRequestMessage INSTANCE = new ClassDumpListRequestMessage();

    public static void write(ClassDumpListRequestMessage msg, FriendlyByteBuf buf) {}

    public static ClassDumpListRequestMessage read(FriendlyByteBuf buf) {
        return INSTANCE;
    }

    public static void handle(ClassDumpListRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> ClassDumpService.INSTANCE.onListRequest(sender));
        context.setPacketHandled(true);
    }
}
