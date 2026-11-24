package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.BlockDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code /data merge|modify block}: NBT write into a foreign-region block entity => its owner; server thread => lease. */
@Mixin(BlockDataAccessor.class)
public abstract class BlockDataAccessorMixin {

    @Shadow
    @Final
    private BlockEntity entity;

    @Shadow
    @Final
    private BlockPos pos;

    @Shadow
    public abstract void setData(CompoundTag tag);

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true)
    private void wfcore$toOwner(CompoundTag tag, CallbackInfo ci) {
        if (entity.getLevel() instanceof ServerLevel level && Routing.toOwner("data block", level, pos, () -> setData(tag))) {
            ci.cancel();
        } else if (entity.getLevel() instanceof ServerLevel level) {
            Routing.leaseOnMain(level, pos);
        }
    }
}
