package com.norwood.wfcore.mixin;

import net.neoforged.fml.loading.FMLLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Leaves out the mixins that need GregTech when GregTech is not installed. Some target GregTech's own classes (harmless unapplied), and three reach into
 * vanilla's worldgen to call GregTech-bound code (road sweep, feature setback, resource allocation), which would fail on the first chunk generated.
 * Must not touch any GregTech class.
 */
public final class WFMixinPlugin implements IMixinConfigPlugin {
    private static final Set<String> NEED_GREGTECH = Set.of(
            "ChunkStatusTasksRoadSweepMixin", "ConfiguredFeatureRoadSetbackMixin", "PlacedFeatureAllocationMixin",
            "DataAccessHatchResearchShareMixin", "EnderItemLinkCoverWeightMixin", "FireClayFluidPipeMixin",
            "LongDistanceItemPipeWeightMixin", "MetaMachineCapabilityCacheMixin", "MultiblockInfoSlotBoundsMixin");

    private boolean gregtech;

    @Override
    public void onLoad(String mixinPackage) {
        gregtech = FMLLoader.getLoadingModList().getModFileById("gtceu") != null;
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (gregtech) {
            return true;
        }
        String simple = mixinClassName.substring(mixinClassName.lastIndexOf('.') + 1);
        return !(simple.startsWith("GT") || NEED_GREGTECH.contains(simple));
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
