package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.common.item.CoverPlaceBehavior;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
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
        BOLT_TOOL = WF_MACHINES.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/bolt_tool")))
                .register();

        DETONATOR = WF_MACHINES.item("detonator", DetonatorItem::new)
                .lang("Detonator")
                .properties(p -> p.stacksTo(1))
                .model((ctx, prov) -> prov.generated(ctx, WFCore.id("item/detonator")))
                .register();

        PACKAGED_VEHICLE = WF_MACHINES.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .model((ctx, prov) -> prov.generated(ctx, new ResourceLocation("minecraft", "item/minecart")))
                .register();

        SIEGE_DEFENSE_TESTER = WF_MACHINES.item("siege_defense_tester", SiegeDefenseTesterItem::new)
                .lang("Siege Difficulty Probe")
                .model(NonNullBiConsumer.noop())
                .register();

        for (int tier : WFCovers.FAN_TIERS) {
            final int t = tier;
            String vn = GTValues.VN[t].toLowerCase(Locale.ROOT);
            COOLING_FAN_COVERS[t] = WF_MACHINES
                    .item("cooling_fan_cover_" + vn, ComponentItem::create)
                    .lang(GTValues.VN[t] + " Cooling Fan Cover")
                    .model(NonNullBiConsumer.noop())
                    .onRegister(item -> item.attachComponents(new CoverPlaceBehavior(WFCovers.COOLING_FANS[t])))
                    .register();
        }
    }

}
