package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.SuperbOverrides;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Lets configured vehicles plough through and break cacti, wood logs and leaves as they drive, instead of
 * being blocked by them. The set of vehicle ids is the WFCore config {@code foliageBreakers}
 * ({@link SuperbOverrides#breaksFoliage}). Server-side only: at the end of each vehicle tick it scans the
 * vehicle's (slightly inflated) bounding box and destroys any matching block found inside it.
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleFoliageBreakerMixin {

    @Inject(method = "baseTick", at = @At("TAIL"))
    private void wfcore$breakFoliage(CallbackInfo ci) {
        VehicleEntity self = (VehicleEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide()) {
            return;
        }
        DefaultVehicleData data = self.computed();
        if (data == null || !SuperbOverrides.breaksFoliage(data.getId())) {
            return;
        }
        AABB box = self.getBoundingBox().inflate(0.1);
        for (BlockPos pos : BlockPos.betweenClosed(
                Mth.floor(box.minX), Mth.floor(box.minY), Mth.floor(box.minZ),
                Mth.floor(box.maxX), Mth.floor(box.maxY), Mth.floor(box.maxZ))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.CACTUS) || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
                level.destroyBlock(pos.immutable(), true, self);
            }
        }
    }
}
