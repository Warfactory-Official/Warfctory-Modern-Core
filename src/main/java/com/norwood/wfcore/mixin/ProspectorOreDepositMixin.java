package com.norwood.wfcore.mixin;

import net.minecraft.world.level.chunk.LevelChunk;

import com.norwood.wfcore.common.deposit.WFDepositProspector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@SuppressWarnings("UnresolvedMixinReference")
@Mixin(targets = "com.gregtechceu.gtceu.api.gui.misc.ProspectorMode$1", remap = false)
public class ProspectorOreDepositMixin {

    @Inject(method = "scan", at = @At("TAIL"))
    private void wfcore$labelDeposits(String[][][] storage, LevelChunk chunk, CallbackInfo ci) {
        WFDepositProspector.labelDeposits(storage, chunk);
    }
}
