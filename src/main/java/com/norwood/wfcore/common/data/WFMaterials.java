package com.norwood.wfcore.common.data;

import com.gregtechceu.gtceu.api.data.chemical.material.Material;
import com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconSet;
import com.gregtechceu.gtceu.common.data.GTMaterials;

import com.norwood.wfcore.WFCore;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_FRAME;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_PLATE;
import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialFlags.GENERATE_ROD;

/**
 * Custom materials for WFCore. Registered into the addon's own material registry during
 * {@code MaterialEvent}. Galvanized Steel provides the frame block used by the radar structure.
 */
public class WFMaterials {

    public static Material GalvanizedSteel;

    public static void init() {
        GalvanizedSteel = new Material.Builder(WFCore.id("galvanized_steel"))
                .ingot()
                .color(0xC0C8D0).secondaryColor(0x5A6470)
                .iconSet(MaterialIconSet.METALLIC)
                .flags(GENERATE_PLATE, GENERATE_ROD, GENERATE_FRAME)
                .components(GTMaterials.Iron, 1, GTMaterials.Zinc, 1)
                .buildAndRegister();
    }
}
