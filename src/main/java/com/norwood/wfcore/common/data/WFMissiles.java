package com.norwood.wfcore.common.data;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.MissileModels;
import com.wf.wfballistics.item.MissileItem;
import com.wf.wfballistics.item.MissilePreset;
import com.wf.wfballistics.warhead.WarheadRegistry;

import java.util.function.UnaryOperator;

import static com.norwood.wfcore.WFCore.WF_MACHINES;

/**
 * WFCore's own missile registry. Each entry is a carryable {@link MissileItem} — the exact same item class
 * WF-Ballistics registers — wrapping a {@link MissilePreset} built here with WF-B's public builder.
 *
 * <p><b>Why register them on our own Registrate instead of WF-B's {@code MissilePresetRegistry}:</b> WF-B
 * snapshots its preset registry into items in {@code ModItems}' static initializer, which runs during WF-B's
 * own mod construction. WFCore hard-depends on WF-B ({@code ordering = "AFTER"}), so by the time any WFCore
 * code runs, that snapshot has already been taken — anything registered into {@code MissilePresetRegistry}
 * from here would never become an item. Owning the items ourselves avoids the whole load-order trap, and
 * they behave identically to {@code wfballistics:missile_*} everywhere it matters:
 * <ul>
 * <li>the Missile Factory / Launch Silo / Interceptor Battery all key off {@code instanceof MissileItem}
 * and {@code preset()}, not the namespace;</li>
 * <li>the client BEWLR ({@code MissileItemRenderer}) draws the airframe from the preset's model id, so the
 * item renders as the real 3D missile (the item model just needs to parent WF-B's {@code builtin/entity}
 * base — see {@code assets/wfcore/models/item/missile_*.json});</li>
 * <li>KubeJS missile-factory recipes can output {@code wfcore:missile_<name>} directly (see
 * {@code run/kubejs/server_scripts/wfcore_missile_factory.js}).</li>
 * </ul>
 *
 * <p><b>Airframes + warheads</b> are referenced by id from WF-B's registries. Built-in airframe ids include
 * {@code v2, strong, huge, atlas, micro, taint, shahed, thermo, neon, stealth, abm, ...} ({@link MissileModels});
 * built-in warhead ids include {@code standard, fragmentation, mininuke, interceptor, emp, gas, fire,
 * recursive_frag, fire_cluster, inert} ({@link WarheadRegistry}). Reuse one of those, or register a custom
 * warhead with {@code WarheadRegistry.register(id, detonation)} before the preset that references it is built
 * (custom airframe models require a WF-B-side asset, so those are best added in WF-B itself). An unknown
 * airframe/warhead id silently falls back to the default, so keep the ids in sync with WF-B.
 *
 * <p>The entries below are ready-to-use scaffolding — rename, retune, or delete them and add your own.
 */
public class WFMissiles {

    public static ItemEntry<MissileItem> HEAVY_CRUISE;
    public static ItemEntry<MissileItem> BUNKER_BUSTER;
    public static ItemEntry<MissileItem> HEAVY_INTERCEPTOR;

    private WFMissiles() {}

    public static void init() {
        // A slow, heavy terrain-following cruise missile with a standard warhead.
        HEAVY_CRUISE = missile("heavy_cruise", "Heavy Cruise Missile",
                MissileModels.rl("strong"), WarheadRegistry.rl("standard"),
                b -> b.terrainFollow(24.0).cruiseSpeed(0.9).health(50.0f)
                        .fuel(MissileEntity.FuelType.LIQUID, 2000));

        // High-altitude, near-vertical top-attack with a big fragmentation payload.
        BUNKER_BUSTER = missile("bunker_buster", "Bunker Buster Missile",
                MissileModels.rl("huge"), WarheadRegistry.rl("fragmentation"),
                b -> b.highAltitude(240.0).cruiseSpeed(1.2).health(70.0f)
                        .attackAngle(85.0).fragmentCount(48).explosionOffset(4.0f)
                        .fuel(MissileEntity.FuelType.SOLID, 2400));

        // A point-defense round for the Interceptor Battery. interceptor(...) flags preset().isInterceptor(),
        // which is what routes it to the battery (and out of the Launch Silo's launchable filter).
        HEAVY_INTERCEPTOR = missile("interceptor_heavy", "Heavy Interceptor Missile",
                MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"),
                b -> b.highAltitude(220.0).cruiseSpeed(8.0).turnRate(0.4).health(25.0f)
                        .interceptor(0.9f).accel(0.9, 0.9)
                        .fuel(MissileEntity.FuelType.SOLID, 800));
    }

    /**
     * Registers one {@code wfcore:missile_<name>} item wrapping a preset built from {@code cfg}. The preset is
     * built inside the item factory (invoked during the item {@code RegisterEvent}, long after WF-B has
     * bootstrapped its model/warhead registries, so the id lookups resolve). Stacks to 16, matching WF-B.
     *
     * <p>{@code model(noop)} because the item model is hand-authored to parent WF-B's {@code missile_render_base}
     * (a {@code builtin/entity} model) so the BEWLR takes over rendering — a generated flat-sprite model would
     * suppress it.
     */
    private static ItemEntry<MissileItem> missile(String name, String lang, ResourceLocation airframe,
                                                  ResourceLocation warhead,
                                                  UnaryOperator<MissilePreset.Builder> cfg) {
        return WF_MACHINES
                .item("missile_" + name, props -> new MissileItem(
                        cfg.apply(MissilePreset.builder(WFCore.id("missile_" + name), airframe, warhead)).build(),
                        props))
                .lang(lang)
                .properties(p -> p.stacksTo(16))
                .model(NonNullBiConsumer.noop())
                .register();
    }
}
