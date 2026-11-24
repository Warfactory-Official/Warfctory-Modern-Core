package com.norwood.wfcore.tessellate.mixin.vanilla;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;
import java.util.function.BiPredicate;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

/**
 * Village distance tracker (LeveledPriorityQueue) + section maps mutated from entity AI on region workers
 * ({@code MoveBackToVillageGoal} -> {@code sectionsToVillage} -> {@code runAllUpdates}) while the server thread ticks it.
 * All mutators and the tracker read under the instance monitor.
 */
@Mixin(PoiManager.class)
public abstract class PoiManagerMixin {

    @WrapMethod(method = "sectionsToVillage")
    private int wfcore$sectionsToVillage(SectionPos pos, Operation<Integer> original) {
        synchronized (this) {
            return original.call(pos);
        }
    }

    @WrapMethod(method = "tick")
    private void wfcore$tick(BooleanSupplier hasTime, Operation<Void> original) {
        synchronized (this) {
            original.call(hasTime);
        }
    }

    @WrapMethod(method = "setDirty")
    private void wfcore$setDirty(long section, Operation<Void> original) {
        synchronized (this) {
            original.call(section);
        }
    }

    @WrapMethod(method = "onSectionLoad")
    private void wfcore$onSectionLoad(long section, Operation<Void> original) {
        synchronized (this) {
            original.call(section);
        }
    }

    @WrapMethod(method = "add")
    private void wfcore$add(BlockPos pos, Holder<PoiType> type, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, type);
        }
    }

    @WrapMethod(method = "remove")
    private void wfcore$remove(BlockPos pos, Operation<Void> original) {
        synchronized (this) {
            original.call(pos);
        }
    }

    @WrapMethod(method = "release")
    private boolean wfcore$release(BlockPos pos, Operation<Boolean> original) {
        synchronized (this) {
            return original.call(pos);
        }
    }

    @WrapMethod(method = "take")
    private Optional<BlockPos> wfcore$take(Predicate<Holder<PoiType>> type, BiPredicate<Holder<PoiType>, BlockPos> pos,
                                           BlockPos origin, int radius, Operation<Optional<BlockPos>> original) {
        synchronized (this) {
            return original.call(type, pos, origin, radius);
        }
    }

    @WrapMethod(method = "checkConsistencyWithBlocks")
    private void wfcore$checkConsistency(SectionPos pos, LevelChunkSection section, Operation<Void> original) {
        synchronized (this) {
            original.call(pos, section);
        }
    }
}
