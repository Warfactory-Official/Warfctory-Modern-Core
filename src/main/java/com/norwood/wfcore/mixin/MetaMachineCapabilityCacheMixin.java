package com.norwood.wfcore.mixin;

import java.util.HashMap;
import java.util.Map;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;


@Mixin(value = MetaMachineBlockEntity.class, remap = false)
public abstract class MetaMachineCapabilityCacheMixin {

    /** Slot 6 holds the {@code null} (sideless) query; 0-5 are {@link Direction#ordinal()}. */
    @Unique
    private static final int WFCORE$SIDE_SLOTS = 7;

    @Unique
    private Map<Capability<?>, LazyOptional<?>[]> wfcore$capabilityCache;

    @Unique
    private long wfcore$capabilityCacheTick = Long.MIN_VALUE;

    @Unique
    private static int wfcore$sideSlot(Direction side) {
        return side == null ? WFCORE$SIDE_SLOTS - 1 : side.ordinal();
    }

    @Unique
    private Map<Capability<?>, LazyOptional<?>[]> wfcore$cacheForThisTick() {
        if (!WFCoreConfig.isGtMachineCapabilityCacheEnabled()) {
            return null;
        }

        BlockEntity self = (BlockEntity) (Object) this;
        Level level = self.getLevel();
        if (level == null || level.isClientSide() || self.isRemoved()) {
            return null;
        }

        long now = level.getGameTime();
        if (wfcore$capabilityCache == null) {
            wfcore$capabilityCache = new HashMap<>(4);
        } else if (wfcore$capabilityCacheTick != now) {
            wfcore$capabilityCache.clear();
        }

        wfcore$capabilityCacheTick = now;
        return wfcore$capabilityCache;
    }

    @Inject(
        method = "getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)"
            + "Lnet/minecraftforge/common/util/LazyOptional;",
        at = @At("HEAD"),
        cancellable = true
    )
    private void wfcore$serveCachedCapability(
        Capability<?> capability,
        Direction side,
        CallbackInfoReturnable<LazyOptional<?>> cir
    ) {
        Map<Capability<?>, LazyOptional<?>[]> cache = wfcore$cacheForThisTick();
        if (cache == null) {
            return;
        }

        LazyOptional<?>[] bySide = cache.get(capability);
        if (bySide == null) {
            return;
        }

        LazyOptional<?> cached = bySide[wfcore$sideSlot(side)];
        if (cached == null) {
            return;
        }

        if (cached.isPresent() || cached == LazyOptional.empty()) {
            cir.setReturnValue(cached);
        }
    }

    @Inject(
        method = "getCapability(Lnet/minecraftforge/common/capabilities/Capability;Lnet/minecraft/core/Direction;)"
            + "Lnet/minecraftforge/common/util/LazyOptional;",
        at = @At("RETURN")
    )
    private void wfcore$storeCapability(
        Capability<?> capability,
        Direction side,
        CallbackInfoReturnable<LazyOptional<?>> cir
    ) {
        Map<Capability<?>, LazyOptional<?>[]> cache = wfcore$cacheForThisTick();
        if (cache == null) {
            return;
        }

        cache.computeIfAbsent(capability, ignored -> new LazyOptional<?>[WFCORE$SIDE_SLOTS])
            [wfcore$sideSlot(side)] = cir.getReturnValue();
    }
}
