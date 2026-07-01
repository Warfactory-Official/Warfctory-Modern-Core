package com.norwood.wfcore.mixin;

import net.minecraft.network.FriendlyByteBuf;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.network.message.receive.VehiclesDataMessage;
import com.atsuishio.superbwarfare.tools.BufferSerializer;
import com.norwood.wfcore.network.PayloadCompression;
import io.netty.buffer.Unpooled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Superb Warfare syncs the whole vehicle-data registry to each player on login in one packet, which
 * overflows the vanilla 1 MiB custom-payload cap once enough addon vehicles are loaded. Deflate the
 * payload on encode and inflate it on decode.
 */
@Mixin(value = VehiclesDataMessage.class, remap = false)
public class VehiclesDataMessageCompressionMixin {

    @Inject(method = "encode", at = @At("HEAD"), cancellable = true)
    private static void wfcore$encodeCompressed(VehiclesDataMessage message, FriendlyByteBuf buf, CallbackInfo ci) {
        FriendlyByteBuf raw = new FriendlyByteBuf(Unpooled.buffer());
        raw.writeVarInt(message.data().size());
        for (DefaultVehicleData data : message.data()) {
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
