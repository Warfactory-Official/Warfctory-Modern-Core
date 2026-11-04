package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.api.data.chemical.material.properties.PropertyKey;
import com.gregtechceu.gtceu.api.registry.registrate.entry.MaterialRegistryEntry;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import net.neoforged.neoforge.fluids.FluidStack;

import com.norwood.wfcore.WFCore;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_FRAME;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_PLATE;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_ROD;

/**
 * Custom materials for WFCore. Registered into the addon's own material registry during
 * {@code MaterialEvent}. Galvanized Steel provides the frame block used by the radar structure.
 */
public class WFMaterials {

    public static MaterialRegistryEntry GalvanizedSteel;

    public static MaterialRegistryEntry FireClay;

    public static MaterialRegistryEntry UnfiredFireClay;

    public static void init() {
        GalvanizedSteel = WFCore.WF_MACHINES.material("galvanized_steel", b -> b
                .ingot()
                .color(0xC0C8D0).secondaryColor(0x5A6470)
                .iconSet(MaterialIconSet.METALLIC)
                .flags(GENERATE_PLATE, GENERATE_ROD, GENERATE_FRAME)
                .components(GTMaterials.Iron, 1, GTMaterials.Zinc, 1));

        FireClay = WFCore.WF_MACHINES.material("fire_clay", b -> b
                // gasProof = false: gasses leak straight out (GTCEu's own pipe logic handles the leak).
                .fluidPipeProperties(5000, 20, false)
                .color(0xB0704A).secondaryColor(0x6E4A32)
                .iconSet(MaterialIconSet.DULL));


        UnfiredFireClay = WFCore.WF_MACHINES.material("unfired_fire_clay", b -> b
                .fluidPipeProperties(340, 3, false)
                .color(0xA79B86).secondaryColor(0x6E655A)
                .iconSet(MaterialIconSet.ROUGH));
    }

    /**
     * @return true if the fluid is a molten metal (its material forms ingots) — the only cargo a fire-clay
     *         pipe carries without failing.
     */
    public static boolean isMoltenMetal(FluidStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        Material material = ChemicalHelper.getMaterial(stack.getFluid());
        return material != null && material.hasProperty(PropertyKey.INGOT);
    }
}
