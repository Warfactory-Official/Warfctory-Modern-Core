package com.norwood.wfcore.integration.kubejs;

import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.ScriptType;

/** Registers WFCore's KubeJS bindings (discovered via {@code kubejs.plugins.txt}). */
public class WFCoreKubePlugin implements KubeJSPlugin {

    @Override
    public void registerBindings(BindingRegistry bindings) {
        ScriptType type = bindings.type();
        if (type == ScriptType.STARTUP || type == ScriptType.SERVER) {
            bindings.add("WFLoot", new WFLootBindings());
            bindings.add("WFEnchant", new WFEnchantBindings());
            bindings.add("WFBlocks", new WFBlockBindings());
            bindings.add("WFBoltGun", new WFBoltGunBindings());
            if (com.norwood.wfcore.integration.gt.GTIntegration.isLoaded()) {
                // These read GregTech data (research, deposits, compute parts, radar machines, vehicle factory); without GregTech the bindings do not exist.
                bindings.add("WFResearch", new WFResearchBindings());
                bindings.add("WFDeposits", new WFDepositBindings());
                bindings.add("WFCompute", new WFComputeBindings());
                bindings.add("WFRadar", new WFRadarBindings());
                bindings.add("WFVehicles", new WFVehicleBindings());
            }
        }
    }
}
