package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.tessellate.Routing;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.commands.data.EntityDataAccessor;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** {@code /data merge|modify entity}: NBT write into a foreign-region entity => its owner; server thread => lease. */
@Mixin(EntityDataAccessor.class)
public abstract class EntityDataAccessorMixin {

    @Shadow
    @Final
    private Entity entity;

    @Shadow
    public abstract void setData(CompoundTag tag) throws CommandSyntaxException;

    @Inject(method = "setData", at = @At("HEAD"), cancellable = true)
    private void wfcore$toOwner(CompoundTag tag, CallbackInfo ci) {
        if (Routing.toOwner("data entity", entity, () -> {
            try {
                setData(tag);
            } catch (CommandSyntaxException e) {
                throw new IllegalStateException(e);
            }
        })) {
            ci.cancel();
        } else if (entity.level() instanceof ServerLevel level) {
            Routing.leaseOnMain(level, entity.blockPosition());
        }
    }
}
