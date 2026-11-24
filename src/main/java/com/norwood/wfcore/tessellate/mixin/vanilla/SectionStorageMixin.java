package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.world.level.chunk.storage.SectionStorage;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

/** POI section map ({@code Long2ObjectMap}) + dirty set shared by region workers: lookups/creation under the instance monitor. */
@Mixin(SectionStorage.class)
public abstract class SectionStorageMixin<R> {

    @WrapMethod(method = "get")
    private Optional<R> wfcore$get(long key, Operation<Optional<R>> original) {
        synchronized (this) {
            return original.call(key);
        }
    }

    @WrapMethod(method = "getOrLoad")
    private Optional<R> wfcore$getOrLoad(long key, Operation<Optional<R>> original) {
        synchronized (this) {
            return original.call(key);
        }
    }

    @WrapMethod(method = "getOrCreate")
    private R wfcore$getOrCreate(long key, Operation<R> original) {
        synchronized (this) {
            return original.call(key);
        }
    }
}
