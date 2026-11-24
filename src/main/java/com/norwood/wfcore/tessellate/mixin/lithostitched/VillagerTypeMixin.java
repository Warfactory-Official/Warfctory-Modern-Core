package com.norwood.wfcore.tessellate.mixin.lithostitched;

import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shadows the field Lithostitched's own {@code VillagerTypeMixin} merges into {@code VillagerType}. Needs the later apply
 * order: config priority and this priority > Lithostitched's 1000. Field absent at apply => this mixin fails, cache stays HashMap.
 */
@Mixin(value = VillagerType.class, priority = 2000)
public abstract class VillagerTypeMixin {

    @Shadow(remap = false)
    @Mutable
    private static Map<VillagerType, TagKey<Biome>> TYPE_TO_TAG_CACHE;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void wfcore$concurrentTagCache(CallbackInfo ci) {
        TYPE_TO_TAG_CACHE = new ConcurrentHashMap<>();
    }
}
