package com.norwood.wfcore.integration.kubejs;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.deposit.DepositNode;
import com.norwood.wfcore.common.deposit.DepositRegion;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.common.recipe.condition.DepositRecipeCondition;

/**
 * KubeJS binding exposed as {@code WFDeposits} in startup scripts. Adds drillable deposit types (the ore kinds
 * the drilling rig can mine) and creates the recipe condition that binds a drilling recipe to a deposit type.
 *
 * <pre>{@code
 * // startup_scripts: register a deposit type (bare ids default to the wfcore namespace)
 * WFDeposits.add('iron_deposit')
 *     .texture('kubejs:block/deposit/iron')   // ns:block/... ; a textures/<path>.png file
 *     .yield(2000, 6000)                       // per-block min/max yield (defaults from config)
 *     .weight(40)                              // worldgen weight
 *     .dimension('minecraft:overworld')        // dimensions it generates in
 *     .clusterSize(2, 4)                        // square cluster size, 2..6
 *     .register()
 *
 * // server_scripts: the base drilling recipe for that type (no inputs; condition picks it by deposit)
 * event.recipes.wfcore.drilling('wfcore:drill_iron')
 *     .outputItems('minecraft:raw_iron')
 *     .EUt(120).duration(200)
 *     .addCondition(WFDeposits.condition('iron_deposit'))
 *
 * // Drilling fluid: a recipe WITH a fluid input runs in place of the base whenever that fluid is fed in.
 * //  - faster / more output  = efficiency;  - extra outputItems / chancedOutput = extra materials;
 * //  - omit the base recipe entirely        = the deposit is mineable ONLY with the fluid (gating).
 * event.recipes.wfcore.drilling('wfcore:drill_iron_boosted')
 *     .inputFluids(Fluid.of('gtceu:drilling_fluid', 100))
 *     .outputItems('minecraft:raw_iron', 2)
 *     .chancedOutput('minecraft:raw_gold', 1000, 0)   // 10% gold byproduct
 *     .EUt(120).duration(100)
 *     .addCondition(WFDeposits.condition('iron_deposit'))
 *
 * // a hand-placed patch at exact coordinates (Y is ignored — deposits sit on bedrock)
 * WFDeposits.node('uranium_deposit', 500, 700).size(6).register()
 *
 * // a quota: ~20 iron patches spread across a region (deterministic, one per cell)
 * WFDeposits.region('iron_deposit').from(10, 30).to(10000, 30000).count(20).register()
 * }</pre>
 */
public class WFDepositBindings {

    /** Opens a builder for a new deposit type; call {@code .register()} to add it. */
    public DepositType.Builder add(String id) {
        return DepositType.builder(resolve(id));
    }

    /** Recipe condition binding a {@code wfcore:drilling} recipe to a deposit type. */
    public DepositRecipeCondition condition(String depositId) {
        return new DepositRecipeCondition(resolve(depositId).toString());
    }

    /** A forced deposit of {@code type} at exact world coordinates (placed on the bedrock floor). */
    public DepositNode.Builder node(String type, int x, int z) {
        return DepositNode.builder(resolve(type), x, z);
    }

    /** A quota of {@code count} deposits of {@code type} spread across a rectangular region. */
    public DepositRegion.Builder region(String type) {
        return DepositRegion.builder(resolve(type));
    }

    public void remove(String id) {
        WFDeposits.unregister(resolve(id));
    }

    private static ResourceLocation resolve(String id) {
        return id.indexOf(':') >= 0 ? new ResourceLocation(id) : WFCore.id(id);
    }
}
