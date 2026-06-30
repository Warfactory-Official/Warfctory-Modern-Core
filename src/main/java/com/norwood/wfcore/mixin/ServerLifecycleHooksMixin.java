package com.norwood.wfcore.mixin;

import net.minecraft.core.Holder;
import net.minecraftforge.common.world.BiomeModifier;
import net.minecraftforge.common.world.ForgeBiomeModifiers.AddFeaturesBiomeModifier;
import net.minecraftforge.server.ServerLifecycleHooks;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

// Strips Superb Warfare's ore world gen (galena/scheelite/silver + deepslate) before Forge applies biome
@Mixin(value = ServerLifecycleHooks.class, remap = false)
public class ServerLifecycleHooksMixin {

    @Unique
    private static final String WFCORE$SBW = "superbwarfare";

    @ModifyVariable(method = "runModifiers", at = @At("STORE"), ordinal = 0)
    private static List<BiomeModifier> wfcore$stripSuperbWarfareWorldgen(List<BiomeModifier> modifiers) {
        return modifiers.stream()
                .filter(modifier -> !wfcore$addsSuperbWarfareFeatures(modifier))
                .toList();
    }

    @Unique
    private static boolean wfcore$addsSuperbWarfareFeatures(BiomeModifier modifier) {
        if (!(modifier instanceof AddFeaturesBiomeModifier features)) {
            return false;
        }
        return features.features().stream()
                .map(Holder::unwrapKey)
                .anyMatch(key -> key.isPresent() && key.get().location().getNamespace().equals(WFCORE$SBW));
    }
}
