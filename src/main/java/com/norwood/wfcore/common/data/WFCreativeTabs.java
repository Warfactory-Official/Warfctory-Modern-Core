package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.common.data.GTCreativeModeTabs.RegistrateDisplayItemsGenerator;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;

import com.norwood.wfcore.WFCore;
import com.tterrag.registrate.util.entry.RegistryEntry;

import static com.norwood.wfcore.WFCore.WF_MACHINES;

public class WFCreativeTabs {

    public static RegistryEntry<CreativeModeTab, CreativeModeTab> WFCORE_TAB;

    public static void init() {
        WFCORE_TAB = WF_MACHINES.defaultCreativeTab("wfcore",
                builder -> builder
                        .displayItems(new RegistrateDisplayItemsGenerator("wfcore", WF_MACHINES))
                        .icon(() -> WFBlocks.ALUMINIUM_SHEET_CASING.asStack())
                        .title(WF_MACHINES.addLang("itemGroup", WFCore.id("wfcore"), "Warfactory Modern Core"))
                        .build())
                .register();

        // Switch Registrate's automatic tab assignment OFF so exactly ONE mechanism fills this tab.
        //
        // defaultCreativeTab(...) does two things: it registers the tab, and it points Registrate's
        // `defaultCreativeModeTab` field at it. AbstractRegistrate#item then transforms every subsequent
        // item builder with `.tab(defaultCreativeModeTab)`, and Registrate's own
        // onBuildCreativeModeTabContents listener accepts each of those into the tab. That is a second,
        // independent path on top of the RegistrateDisplayItemsGenerator above, which walks
        // registrate.getAll(Registries.ITEM) against GTRegistrate's TAB_LOOKUP. With both live, every
        // wfcore item was offered twice and CreativeModeTab.Output throws on the first one it sees:
        // "Itemstack 1 wfcore:aluminium_sheet_casing already exists in the tab's list" — a hard crash on
        // opening a world, not a cosmetic duplicate.
        //
        // GT does not hit this because its tabs are created after its content registers; wfcore builds the
        // tab first (WFContent#init) because GT's TAB_LOOKUP needs the entry to exist.
        //
        // Null is the documented off switch: AbstractRegistrate#item does
        // `defaultCreativeModeTab == null ? builder : builder.tab(defaultCreativeModeTab)`. Handing back
        // Registrate's initial value (CreativeModeTabs.SEARCH) does NOT work and was tried first — the
        // vanilla search tab already aggregates every other tab's contents, so that merely relocated the
        // duplicate from the wfcore tab into SEARCH and it crashed exactly the same way.
        WF_MACHINES.defaultCreativeTab((ResourceKey<CreativeModeTab>) null);
    }
}
