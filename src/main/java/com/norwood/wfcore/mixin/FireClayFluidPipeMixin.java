package com.norwood.wfcore.mixin;

import com.gregtechceu.gtceu.api.block.MaterialPipeBlock;
import com.gregtechceu.gtceu.api.pipenet.IPipeNode;
import com.gregtechceu.gtceu.common.blockentity.FluidPipeBlockEntity;

import com.norwood.wfcore.common.data.WFMaterials;

import net.minecraftforge.fluids.FluidStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Layers the fire-clay pipe's containment rule onto GTCEu's stock fluid pipe
 * YOU WOULD HOPE IT WAS A PREDICATE
 */
@Mixin(value = FluidPipeBlockEntity.class, remap = false)
public abstract class FireClayFluidPipeMixin {

    @Inject(method = "checkAndDestroy", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$fireClayContainment(FluidStack stack, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) return;
        if (!(((IPipeNode<?, ?>) (Object) this).getPipeBlock() instanceof MaterialPipeBlock<?, ?, ?> pipeBlock)
                || pipeBlock.material != WFMaterials.FireClay) {
            return;
        }
        if (WFMaterials.isMoltenMetal(stack)) {
            ci.cancel();
            return;
        }
        boolean gas = stack.getFluid().getFluidType().isLighterThanAir()
                || stack.getFluid().getFluidType().getDensity(stack) < 0;
        ((FluidPipeBlockEntity) (Object) this).destroyPipe(stack, false, gas, !gas, false, false);
        ci.cancel();
    }
}
