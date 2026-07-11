package com.norwood.wfcore.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

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
 * ({@link SuperbOverrides#breaksFoliage}).
 * <p>
 * The foliage is cleared from the vehicle's swept path at the <em>head</em> of {@link VehicleEntity#move},
 * <em>before</em> {@code super.move} evaluates collision. This matters for more than convenience:
 * {@code VehicleEntity#move} deals {@code VEHICLE_STRIKE} crash damage the instant it registers a
 * horizontal/vertical collision, so if the foliage were still solid at that point the vehicle would take
 * damage from every cactus or log it touched (the old end-of-tick scan broke the block only after the hit).
 * Removing the foliage first means the vehicle never collides with it, so it takes no damage and drives
 * straight through — while real obstacles (stone, etc.) still block it and still deal crash damage.
 * Server-side only; sweeping the bounding box along the intended movement also covers fast vehicles that
 * would reach a block within a single tick.
 */
@Mixin(VehicleEntity.class)
public abstract class VehicleFoliageBreakerMixin {

    @Inject(method = "move(Lnet/minecraft/world/entity/MoverType;Lnet/minecraft/world/phys/Vec3;)V",
            at = @At("HEAD"))
    private void wfcore$breakFoliage(MoverType movementType, Vec3 movement, CallbackInfo ci) {
        VehicleEntity self = (VehicleEntity) (Object) this;
        Level level = self.level();
        if (level.isClientSide()) {
            return;
        }
        DefaultVehicleData data = self.computed();
        if (data == null || !SuperbOverrides.breaksFoliage(data.getId())) {
            return;
        }
        AABB box = self.getBoundingBox().expandTowards(movement).inflate(0.1);
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
