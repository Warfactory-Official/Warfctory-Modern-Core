package com.norwood.wfcore.tessellate;

import com.norwood.wfcore.tessellate.ae2things.Ae2ThingsTransformer;
import com.norwood.wfcore.tessellate.ir.IrTransformer;
import com.norwood.wfcore.tessellate.kubejs.KubeJsTransformer;
import com.norwood.wfcore.tessellate.ldlib2.Ldlib2Transformer;
import com.norwood.wfcore.tessellate.umc.UmcTransformer;
import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.LoadingModList;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Applies a mixin only when Tessellate and the mod named by the mixin's sub-package are both present. */
public final class TessellateMixinPlugin implements IMixinConfigPlugin {

    private static final Logger LOGGER = LogUtils.getLogger();

    static final String TESSELLATE = "tessellate";
    static final String MIXIN_ROOT = "com.norwood.wfcore.tessellate.mixin.";

    /** Sub-package => mod id. Unknown sub-package => refuse. */
    static final Map<String, String> TARGET_MODS = Map.ofEntries(
            Map.entry("hungerstrike", "hungerstrike"),
            Map.entry("jade", "jade"),
            Map.entry("voicechat", "voicechat"),
            Map.entry("ldlib2", "ldlib2"),
            Map.entry("lithostitched", "lithostitched"),
            Map.entry("journeymap", "journeymap"),
            Map.entry("vanilla", TESSELLATE),
            Map.entry("tessellate", TESSELLATE),
            Map.entry("rhino", "rhino"),
            Map.entry("ae2things", "ae2things"),
            Map.entry("lithium", "lithium"),
            Map.entry("umc", "universalmodcore"),
            Map.entry("ir", "immersiverailroading"),
            Map.entry("kubejs", "kubejs"));

    /** Mixin class prefix (below {@link #MIXIN_ROOT}) => exact mod version; edits are shape-dependent. */
    static final Map<String, String> VERSION_GATES = Map.of(
            "ldlib2.transform.", "2.2.42",
            "tessellate.", "1.2.13",
            "rhino.", "2101.2.8-build.91",
            "ae2things.", "1.4.2-beta",
            "lithium.", "0.15.4+mc1.21.1",
            "umc.", "1.3.1",
            "ir.", "0.0NONE",
            "kubejs.", "2101.7.2-build.381");

    static final String LDLIB2_TRANSFORM = MIXIN_ROOT + "ldlib2.transform.";

    /** Anchor-mixin sub-package => class edit run from {@link #postApply}. */
    static final Map<String, BiConsumer<String, ClassNode>> TRANSFORMS = Map.of(
            "ldlib2", Ldlib2Transformer::apply,
            "ae2things", Ae2ThingsTransformer::apply,
            "umc", UmcTransformer::apply,
            "ir", IrTransformer::apply,
            "kubejs", KubeJsTransformer::apply);

    static boolean applies(String mixinClassName, Predicate<String> loaded) {
        if (!mixinClassName.startsWith(MIXIN_ROOT)) {
            return false;
        }
        String rest = mixinClassName.substring(MIXIN_ROOT.length());
        String target = TARGET_MODS.get(rest.substring(0, Math.max(rest.indexOf('.'), 0)));
        return target != null && loaded.test(TESSELLATE) && loaded.test(target);
    }

    static boolean applies(String mixinClassName, Predicate<String> loaded, Function<String, String> version) {
        if (!applies(mixinClassName, loaded)) {
            return false;
        }
        String rest = mixinClassName.substring(MIXIN_ROOT.length());
        String target = TARGET_MODS.get(rest.substring(0, rest.indexOf('.')));
        for (Map.Entry<String, String> gate : VERSION_GATES.entrySet()) {
            if (rest.startsWith(gate.getKey())) {
                return gate.getValue().equals(version.apply(target));
            }
        }
        return true;
    }

    private static String modVersion(String id) {
        return LoadingModList.get().getModFileById(id).getMods().get(0).getVersion().toString();
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        Predicate<String> loaded = id -> LoadingModList.get().getModFileById(id) != null;
        boolean applies = applies(mixinClassName, loaded, TessellateMixinPlugin::modVersion);
        if (!applies && applies(mixinClassName, loaded)) {
            LOGGER.warn("Tessellate compat {} not applied: target mod version differs from the pinned one", mixinClassName);
        }
        return applies;
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
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
        if (mixinClassName.startsWith(MIXIN_ROOT)) {
            String rest = mixinClassName.substring(MIXIN_ROOT.length());
            int dot = rest.indexOf('.');
            if (rest.startsWith("transform.", dot + 1)) {
                BiConsumer<String, ClassNode> edit = TRANSFORMS.get(rest.substring(0, dot));
                if (edit == null) {
                    throw new IllegalStateException("no transformer for " + mixinClassName);
                }
                edit.accept(targetClassName, targetClass);
            }
        }
    }
}
