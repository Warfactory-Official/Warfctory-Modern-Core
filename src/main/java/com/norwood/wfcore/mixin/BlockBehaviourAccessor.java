package com.norwood.wfcore.mixin;

import net.minecraft.world.level.block.state.BlockBehaviour;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes a setter for the normally-final {@code explosionResistance} field so it can be changed after construction.
 */
@Mixin(BlockBehaviour.class)
public interface BlockBehaviourAccessor {

    @Mutable
    @Accessor("explosionResistance")
    void wfcore$setExplosionResistance(float resistance);
}
