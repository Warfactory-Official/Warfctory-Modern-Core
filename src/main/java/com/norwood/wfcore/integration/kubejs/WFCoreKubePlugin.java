package com.norwood.wfcore.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Registers WFCore's KubeJS bindings (discovered via {@code kubejs.plugins.txt}). */
public class WFCoreKubePlugin extends KubeJSPlugin {

    @Override
    public void registerBindings(BindingsEvent event) {
        if (event.getType() == ScriptType.STARTUP) {
            event.add("WFResearch", new WFResearchBindings());
            event.add("WFLoot", new WFLootBindings());
            event.add("WFEnchant", new WFEnchantBindings());
        }
    }
}
