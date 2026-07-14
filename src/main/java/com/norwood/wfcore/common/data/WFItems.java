package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.GTCEu;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.common.item.BoltToolItem;
import com.norwood.wfcore.common.item.DetonatorItem;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.norwood.wfcore.common.item.SiegeDefenseTesterItem;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;

import java.util.Locale;

import static com.norwood.wfcore.WFCore.WF_MACHINES;

public class WFItems {

    public static ItemEntry<BoltToolItem> BOLT_TOOL;
    public static ItemEntry<DetonatorItem> DETONATOR;
    public static ItemEntry<PackagedVehicleItem> PACKAGED_VEHICLE;
    public static ItemEntry<SiegeDefenseTesterItem> SIEGE_DEFENSE_TESTER;

    @SuppressWarnings("unchecked")
    public static final ItemEntry<ComponentItem>[] COOLING_FAN_COVERS = new ItemEntry[GTValues.EV + 1];

    public static void init() {
        // No bespoke art yet: reuse existing textures as placeholders so datagen produces a valid model
        // (a wrench for the casing bolt tool, a minecart for the packaged vehicle).
        BOLT_TOOL = WF_MACHINES.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .model((ctx, prov) -> prov.generated(ctx, GTCEu.id("item/tools/wrench")))
                .register();

        // Wireless detonator: sneak-link up to 5 mining charges, right-click to set them all off.
        // Placeholder flint-and-steel art (the trigger device, despite charges being inert to actual flint).
        DETONATOR = WF_MACHINES.item("detonator", DetonatorItem::new)
                .lang("Detonator")
                .properties(p -> p.stacksTo(1))
                .model((ctx, prov) -> prov.generated(ctx, new ResourceLocation("minecraft", "item/flint_and_steel")))
                .register();

        PACKAGED_VEHICLE = WF_MACHINES.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .model((ctx, prov) -> prov.generated(ctx, new ResourceLocation("minecraft", "item/minecart")))
                .register();

        // Testing tool: reports the WarForge siege difficulty of the current chunk. Uses the 1.12.2
        // eight_carrot art; static model is hand-authored so it renders without runData.
        SIEGE_DEFENSE_TESTER = WF_MACHINES.item("siege_defense_tester", SiegeDefenseTesterItem::new)
                .lang("Siege Difficulty Probe")
                .model(NonNullBiConsumer.noop())
                .register();

        // Cooling-fan cover placer items (one per tier). The cover definitions are registered earlier
        // via WfCoreAddon.registerCovers() (during gtceu's constructor, while the cover registry is
        // unfrozen), so WFCovers.COOLING_FANS is already populated here for CoverPlaceBehavior.
        for (int tier : WFCovers.FAN_TIERS) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            COOLING_FAN_COVERS[t] = WF_MACHINES
                    .item("cooling_fan_cover_" + vn, ComponentItem::create)
                    .lang(GTValues.VN[t] + " Cooling Fan Cover")
                    .model(NonNullBiConsumer.noop())
                    // Inline GTItems.attach(...) so we don't reference GTItems here: touching that class
                    // during construction triggers its <clinit>, which builds material-backed items before
                    // materials are registered (NPE). The lambda runs at the item RegisterEvent instead.
                    .onRegister(item -> item.attachComponents(new CoverPlaceBehavior(WFCovers.COOLING_FANS[t])))
                    .register();
        }
    }

}
