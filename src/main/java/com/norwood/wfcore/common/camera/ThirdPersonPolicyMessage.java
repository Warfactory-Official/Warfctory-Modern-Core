package com.norwood.wfcore.common.camera;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import com.norwood.wfcore.WFCore;

/**
 * Server -&gt; client: whether this world allows third person. Sent on join and whenever an op changes it. The client
 * only stores it in {@link ThirdPersonPolicy}; {@code ForceFirstPersonCameraMixin} reads it.
 */
public record ThirdPersonPolicyMessage(boolean allowed) implements CustomPacketPayload {

    public static final Type<ThirdPersonPolicyMessage> TYPE = new Type<>(
            // MOD_ID is an inlined constant; WFCore.id() would initialise the whole mod class.
            ResourceLocation.fromNamespaceAndPath(WFCore.MOD_ID, "third_person_policy"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ThirdPersonPolicyMessage> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, ThirdPersonPolicyMessage::allowed, ThirdPersonPolicyMessage::new);

    @Override
    public Type<ThirdPersonPolicyMessage> type() {
        return TYPE;
    }

    public static void handle(ThirdPersonPolicyMessage msg, IPayloadContext ctx) {
        ctx.enqueueWork(() -> ThirdPersonPolicy.set(msg.allowed));
    }
}
