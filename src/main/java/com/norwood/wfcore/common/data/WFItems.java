package com.norwood.wfcore.common.data;

import com.norwood.wfcore.common.item.BoltToolItem;
import com.norwood.wfcore.common.item.PackagedVehicleItem;
import com.tterrag.registrate.util.entry.ItemEntry;

import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

public class WFItems {

    public static ItemEntry<BoltToolItem> BOLT_TOOL;
    public static ItemEntry<PackagedVehicleItem> PACKAGED_VEHICLE;

    public static void init() {
        BOLT_TOOL = EXAMPLE_REGISTRATE.item("bolt_tool", BoltToolItem::new)
                .lang("Bolt Tool")
                .register();

        PACKAGED_VEHICLE = EXAMPLE_REGISTRATE.item("packaged_vehicle", PackagedVehicleItem::new)
                .lang("Packaged Vehicle")
                .register();
    }
}
