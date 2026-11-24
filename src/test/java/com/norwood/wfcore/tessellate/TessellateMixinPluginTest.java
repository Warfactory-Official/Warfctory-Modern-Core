package com.norwood.wfcore.tessellate;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TessellateMixinPluginTest {

    private static final String JADE = "com.norwood.wfcore.tessellate.mixin.jade.BlockAccessorImplMixin";

    private static Predicate<String> mods(String... ids) {
        return Set.of(ids)::contains;
    }

    @Test
    void appliesOnlyWithTessellateAndTarget() {
        assertTrue(TessellateMixinPlugin.applies(JADE, mods("tessellate", "jade")));
        assertFalse(TessellateMixinPlugin.applies(JADE, mods("jade")));
        assertFalse(TessellateMixinPlugin.applies(JADE, mods("tessellate")));
        assertFalse(TessellateMixinPlugin.applies(JADE, mods("tessellate", "voicechat")));
    }

    @Test
    void refusesOutsideRootAndUnknownSubpackage() {
        Predicate<String> all = id -> true;
        assertFalse(TessellateMixinPlugin.applies("com.norwood.wfcore.mixin.JadeReplayHideMixin", all));
        assertFalse(TessellateMixinPlugin.applies(TessellateMixinPlugin.MIXIN_ROOT + "unknown.XMixin", all));
        assertFalse(TessellateMixinPlugin.applies(TessellateMixinPlugin.MIXIN_ROOT + "XMixin", all));
    }

    @Test
    void everyConfiguredMixinMapsToAKnownTarget() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/wfcore-tessellate.mixins.json")) {
            var mixins = JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().getAsJsonArray("mixins");
            Set<String> seen = new HashSet<>();
            mixins.forEach(node -> {
                String name = TessellateMixinPlugin.MIXIN_ROOT + node.getAsString();
                assertTrue(TessellateMixinPlugin.applies(name, id -> true), name);
                seen.add(node.getAsString().substring(0, node.getAsString().indexOf('.')));
            });
            assertEquals(TessellateMixinPlugin.TARGET_MODS.keySet(), seen);
        }
    }

    @Test
    void vanillaMixinsNeedOnlyTessellate() {
        String name = TessellateMixinPlugin.MIXIN_ROOT + "vanilla.ScoreboardMixin";
        assertTrue(TessellateMixinPlugin.applies(name, mods("tessellate")));
        assertFalse(TessellateMixinPlugin.applies(name, mods()));
    }

    @Test
    void everyVanillaMixinSourceIsConfigured() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/wfcore-tessellate.mixins.json");
             var sources = java.nio.file.Files.list(java.nio.file.Path.of("src/main/java/com/norwood/wfcore/tessellate/mixin/vanilla"))) {
            Set<String> configured = new HashSet<>();
            JsonParser.parseReader(new InputStreamReader(in)).getAsJsonObject().getAsJsonArray("mixins")
                    .forEach(node -> configured.add(node.getAsString()));
            sources.map(p -> p.getFileName().toString()).filter(f -> f.endsWith(".java"))
                    .forEach(f -> assertTrue(configured.contains("vanilla." + f.substring(0, f.length() - 5)), f));
        }
    }

    @Test
    void ldlib2TransformGatedOnVersion() {
        String transform = TessellateMixinPlugin.LDLIB2_TRANSFORM + "AsyncThreadDataMixin";
        Predicate<String> all = id -> true;
        assertTrue(TessellateMixinPlugin.applies(transform, all, id -> "2.2.42"));
        assertFalse(TessellateMixinPlugin.applies(transform, all, id -> "2.2.43"));
        assertFalse(TessellateMixinPlugin.applies(transform, mods("ldlib2"), id -> "2.2.42"));
        assertFalse(TessellateMixinPlugin.applies(transform, mods("tessellate"), id -> "2.2.42"));
        assertTrue(TessellateMixinPlugin.applies(JADE, all, id -> "0"));
    }

    @Test
    void exactVersionGatesPinShapeDependentEdits() {
        Predicate<String> all = id -> true;
        for (var gate : TessellateMixinPlugin.VERSION_GATES.entrySet()) {
            String rest = gate.getKey();
            String mixin = TessellateMixinPlugin.MIXIN_ROOT + rest + "XMixin";
            assertTrue(TessellateMixinPlugin.applies(mixin, all, id -> gate.getValue()), mixin);
            assertFalse(TessellateMixinPlugin.applies(mixin, all, id -> gate.getValue() + "1"), mixin);
        }
    }

    @Test
    void everyTransformSubpackageHasATransformer() {
        for (String mod : TessellateMixinPlugin.TRANSFORMS.keySet()) {
            assertTrue(TessellateMixinPlugin.TARGET_MODS.containsKey(mod), mod);
        }
    }
}
