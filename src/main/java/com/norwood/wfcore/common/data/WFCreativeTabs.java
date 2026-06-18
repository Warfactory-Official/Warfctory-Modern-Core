package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs.RegistrateDisplayItemsGenerator;

import net.minecraft.world.item.CreativeModeTab;

import com.norwood.wfcore.WFCore;
import com.tterrag.registrate.util.entry.RegistryEntry;

import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

public class WFCreativeTabs {

    public static RegistryEntry<CreativeModeTab> WFCORE_TAB;

    public static void init() {
        WFCORE_TAB = EXAMPLE_REGISTRATE.defaultCreativeTab("wfcore",
                builder -> builder
                        .displayItems(new RegistrateDisplayItemsGenerator("wfcore", EXAMPLE_REGISTRATE))
                        .icon(() -> WFBlocks.ALUMINIUM_SHEET_CASING.asStack())
                        .title(EXAMPLE_REGISTRATE.addLang("itemGroup", WFCore.id("wfcore"), "Warfactory Modern Core"))
                        .build())
                .register();
    }
}
