package com.norwood.wfcore.common.darkness;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.Supplier;

/**
 * Server -&gt; client: the pack/server's authoritative True Darkness settings, pushed on join so a client cannot
 * loosen the darkness by editing its own {@code darkness.properties} or the config screen. The client stores them
 * in {@link DarknessEnforcement}; {@code DarknessConfigLockMixin} then pins them into True Darkness every frame.
 *
 * <p>{@code enabled} maps onto {@link DarknessEnforcement#active()}: when the server has enforcement off, the
 * client is told so and True Darkness reverts to behaving per its own config.
 */
public record DarknessEnforceMessage(boolean enabled, boolean blockLightOnly, boolean ignoreMoonPhase,
        boolean darkOverworld, boolean darkNether, boolean darkEnd, boolean darkDefault, boolean darkSkyless,
        double darkNetherFog, double darkEndFog) {

    public static void write(DarknessEnforceMessage msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.enabled);
        buf.writeBoolean(msg.blockLightOnly);
        buf.writeBoolean(msg.ignoreMoonPhase);
        buf.writeBoolean(msg.darkOverworld);
        buf.writeBoolean(msg.darkNether);
        buf.writeBoolean(msg.darkEnd);
        buf.writeBoolean(msg.darkDefault);
        buf.writeBoolean(msg.darkSkyless);
        buf.writeDouble(msg.darkNetherFog);
        buf.writeDouble(msg.darkEndFog);
    }

    public static DarknessEnforceMessage read(FriendlyByteBuf buf) {
        return new DarknessEnforceMessage(
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble());
    }

    public static void handle(DarknessEnforceMessage msg, Supplier<NetworkEvent.Context> ctx) {
        NetworkEvent.Context context = ctx.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> DarknessEnforcement.set(msg.enabled, msg.blockLightOnly, msg.ignoreMoonPhase,
                        msg.darkOverworld, msg.darkNether, msg.darkEnd, msg.darkDefault, msg.darkSkyless,
                        msg.darkNetherFog, msg.darkEndFog)));
        context.setPacketHandled(true);
    }
}
