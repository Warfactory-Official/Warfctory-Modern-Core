package com.norwood.wfcore.common.data;

import com.norwood.wfcore.integration.gt.GTIntegration;


/**
 * Creates the registrate-backed content (creative tab, blocks, items) during mod construction, before
 * the registry events fire. Materials and machines are created from their dedicated GT events.
 */
public final class WFContent {

    private WFContent() {}

    public static void init() {
        WFCreativeTabs.init();
        if (GTIntegration.isLoaded()) {
            com.norwood.wfcore.integration.gt.GTBootstrap.WF_MACHINES.creativeModeTab(() -> WFCreativeTabs.WFCORE_TAB);
        }
        WFBlocks.init();
        WFItems.init();
        WFMissiles.init();
        if (GTIntegration.isLoaded()) {
            WFMolds.init();
        }
    }
}
