package com.norwood.wfcore.common.data;


import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.integration.gt.GTIntegration;
import com.tterrag.registrate.util.entry.RegistryEntry;


public class WFCreativeTabs {

    public static RegistryEntry<CreativeModeTab, CreativeModeTab> WFCORE_TAB;

    public static void init() {
        if (GTIntegration.isLoaded()) {
            WFCreativeTabsGT.init();
            return;
        }
        // Without GregTech Registrate's own tab mechanism fills the tab (every item registered after it lands in it): one mechanism, so no duplicates.
        WFCORE_TAB = WFCore.REGISTRATE.defaultCreativeTab("wfcore",
                builder -> builder
                        .icon(() -> WFBlocks.ALUMINIUM_SHEET_CASING.asStack())
                        .title(WFCore.REGISTRATE.addLang("itemGroup", WFCore.id("wfcore"), "Warfactory Modern Core")))
                .register();
    }
}
