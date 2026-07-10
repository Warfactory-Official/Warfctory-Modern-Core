package com.norwood.wfcore.integration.kubejs;

import dev.latvian.mods.kubejs.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingsEvent;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Registers WFCore's KubeJS bindings (discovered via {@code kubejs.plugins.txt}). */
public class WFCoreKubePlugin extends KubeJSPlugin {

    @Override
    public void registerBindings(BindingsEvent event) {
        ScriptType type = event.getType();
        if (type == ScriptType.STARTUP || type == ScriptType.SERVER) {
            event.add("WFResearch", new WFResearchBindings());
            event.add("WFLoot", new WFLootBindings());
            event.add("WFEnchant", new WFEnchantBindings());
            event.add("WFDeposits", new WFDepositBindings());
            event.add("WFBlocks", new WFBlockBindings());
            event.add("WFVehicles", new WFVehicleBindings());
        }
    }
}
