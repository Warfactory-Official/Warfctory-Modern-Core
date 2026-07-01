package com.norwood.wfcore.mixin;

import net.minecraft.network.FriendlyByteBuf;

import com.atsuishio.superbwarfare.data.gun.DefaultGunData;
import com.atsuishio.superbwarfare.network.message.receive.GunsDataMessage;
import com.atsuishio.superbwarfare.tools.BufferSerializer;
import com.norwood.wfcore.network.PayloadCompression;
import io.netty.buffer.Unpooled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = GunsDataMessage.class, remap = false)
public class GunsDataMessageCompressionMixin {

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private static void wfcore$encodeCompressed(GunsDataMessage message, FriendlyByteBuf buf, CallbackInfo ci) {
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        raw.writeVarInt(message.data.size());
        for (DefaultGunData data : message.data) {
            raw.writeBytes(BufferSerializer.serialize(data).copy());
        }
        byte[] rawBytes = new byte[raw.readableBytes()];
        raw.readBytes(rawBytes);
        raw.release();
        buf.writeByteArray(PayloadCompression.deflate(rawBytes));
        ci.cancel();
    }

    @ModifyVariable(method = "decode", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static FriendlyByteBuf wfcore$decodeDecompressed(FriendlyByteBuf buf) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(PayloadCompression.inflate(buf.readByteArray())));
    }
}
