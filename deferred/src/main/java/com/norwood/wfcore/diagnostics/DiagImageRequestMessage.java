package com.norwood.wfcore.diagnostics;

import com.norwood.wfcore.diagnostics.server.DiagnosticsService;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * "Send me this capture." {@code fileName} is a bare filename taken from a {@link DiagCatalogMessage} entry —
 * the server never trusts it, {@code ScreenshotCatalog.read} re-validates the character set and asserts the
 * resolved path's parent is the expected screenshot directory before opening anything.
 */
public record DiagImageRequestMessage(long requestId, boolean flagged, String fileName) {

    public static void write(DiagImageRequestMessage msg, FriendlyByteBuf buf) {
        buf.writeLong(msg.requestId);
        buf.writeBoolean(msg.flagged);
        buf.writeUtf(msg.fileName, 256);
    }

    public static DiagImageRequestMessage read(FriendlyByteBuf buf) {
        return new DiagImageRequestMessage(buf.readLong(), buf.readBoolean(), buf.readUtf(256));
    }

    public static void handle(DiagImageRequestMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> DiagnosticsService.INSTANCE.onImageRequest(sender, msg));
        context.setPacketHandled(true);
    }
}
