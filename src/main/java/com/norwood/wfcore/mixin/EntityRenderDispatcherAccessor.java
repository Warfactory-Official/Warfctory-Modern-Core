package com.norwood.wfcore.mixin;

import java.util.Map;

import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.EntityType;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes the vanilla {@code EntityRenderDispatcher.renderers} map so the Kmodo Flywheel registrar can discover,
 * generically, every entity type whose renderer is a Superb Warfare {@code VehicleRenderer} (SBW + addons) and
 * register a Flywheel visualizer for it — without hard-coding a vehicle list.
 */
@Mixin(EntityRenderDispatcher.class)
public interface EntityRenderDispatcherAccessor {

    @Accessor("renderers")
    Map<EntityType<?>, EntityRenderer<?>> wfcore$getRenderers();
}
