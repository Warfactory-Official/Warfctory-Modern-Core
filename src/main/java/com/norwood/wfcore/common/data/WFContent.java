package com.norwood.wfcore.common.data;

import static com.norwood.wfcore.WFCore.EXAMPLE_REGISTRATE;

/**
 * Creates the registrate-backed content (creative tab, blocks, items) during mod construction, before
 * the registry events fire. Materials and machines are created from their dedicated GT events.
 */
public final class WFContent {

    private WFContent() {}

    public static void init() {
        WFCreativeTabs.init();
        EXAMPLE_REGISTRATE.creativeModeTab(() -> WFCreativeTabs.WFCORE_TAB);
        WFBlocks.init();
        WFItems.init();
    }
}
