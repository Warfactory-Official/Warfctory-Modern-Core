package com.norwood.wfcore.common.shader;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;


public record ShaderEnforceMessage(boolean blockShaders) {

    public static void write(ShaderEnforceMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.blockShaders);
    }

    public static ShaderEnforceMessage read(FriendlyByteBuf buf) {
        return new ShaderEnforceMessage(buf.readBoolean());
    }

    public static void handle(ShaderEnforceMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> com.norwood.wfcore.client.shader.ShaderClientApply.apply(msg.blockShaders())));
        context.setPacketHandled(true);
    }
}
