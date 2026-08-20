package com.norwood.wfcore.common.shader;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.shader.ShaderClientApply;

public record ShaderEnforceMessage(boolean blockShaders) implements CustomPacketPayload {

    public static final Type<ShaderEnforceMessage> TYPE = new Type<>(WFCore.id("shader_enforce"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ShaderEnforceMessage> STREAM_CODEC = StreamCodec
            .composite(ByteBufCodecs.BOOL, ShaderEnforceMessage::blockShaders, ShaderEnforceMessage::new);

    @Override
    public Type<ShaderEnforceMessage> type() {
        return TYPE;
    }

    public static void handle(ShaderEnforceMessage msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ctx.enqueueWork(() -> applyClient(msg));
    }

    private static void applyClient(ShaderEnforceMessage msg) {
        ShaderClientApply.apply(msg.blockShaders());
    }
}
