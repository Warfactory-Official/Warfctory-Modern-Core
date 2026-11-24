package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.RegionThreads;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.neoforged.neoforge.common.world.chunk.ForcedChunkManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Function;

/** NeoForge {@code TicketController.forceChunk}: forced-chunk saved data + synchronous load + tickets, server thread only. */
@Mixin(ForcedChunkManager.class)
public abstract class ForcedChunkManagerMixin {

    private static final String FORCE = "forceChunk(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/resources/ResourceLocation;"
            + "Ljava/lang/Comparable;IIZZLnet/minecraft/server/level/TicketType;Ljava/util/function/Function;)Z";

    @Shadow
    @SuppressWarnings("rawtypes")
    static boolean forceChunk(ServerLevel level, ResourceLocation id, Comparable owner, int chunkX, int chunkZ,
                              boolean add, boolean ticking, TicketType type, Function ticketGetter) {
        throw new AssertionError();
    }

    /** Off-thread result is a guess ({@code add}): the real flag is known only when the op replays. */
    @Inject(method = FORCE, at = @At("HEAD"), cancellable = true)
    @SuppressWarnings("rawtypes")
    private static void wfcore$forceOnMain(ServerLevel level, ResourceLocation id, Comparable owner, int chunkX, int chunkZ,
                                           boolean add, boolean ticking, TicketType type, Function ticketGetter,
                                           CallbackInfoReturnable<Boolean> cir) {
        if (!level.getServer().isSameThread()) {
            RegionThreads.executeOnMain("TicketController.forceChunk",
                    () -> forceChunk(level, id, owner, chunkX, chunkZ, add, ticking, type, ticketGetter));
            cir.setReturnValue(add);
        }
    }
}
