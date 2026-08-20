package com.norwood.wfcore.common.darkness;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.norwood.wfcore.WFCore;

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
        double darkNetherFog, double darkEndFog) implements CustomPacketPayload {

    public static final Type<DarknessEnforceMessage> TYPE = new Type<>(WFCore.id("darkness_enforce"));

    // 10 fields — past StreamCodec.composite's 6-arg ceiling, so this is hand-rolled.
    public static final StreamCodec<RegistryFriendlyByteBuf, DarknessEnforceMessage> STREAM_CODEC = StreamCodec.of(
            (buf, msg) -> {
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
            },
            buf -> new DarknessEnforceMessage(
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readBoolean(),
                    buf.readBoolean(), buf.readBoolean(), buf.readBoolean(), buf.readDouble(), buf.readDouble()));

    @Override
    public Type<DarknessEnforceMessage> type() {
        return TYPE;
    }

    /**
     * DistExecutor is gone in NeoForge; the dist check is an {@link FMLEnvironment#dist} guard, and the
     * client-only {@link DarknessEnforcement} touch stays inside a separate method so a dedicated server
     * never classloads it.
     */
    public static void handle(DarknessEnforceMessage msg, IPayloadContext ctx) {
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        ctx.enqueueWork(() -> applyClient(msg));
    }

    private static void applyClient(DarknessEnforceMessage msg) {
        DarknessEnforcement.set(msg.enabled, msg.blockLightOnly, msg.ignoreMoonPhase, msg.darkOverworld,
                msg.darkNether, msg.darkEnd, msg.darkDefault, msg.darkSkyless, msg.darkNetherFog, msg.darkEndFog);
    }
}
