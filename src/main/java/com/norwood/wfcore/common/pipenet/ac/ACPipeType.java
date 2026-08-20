package com.norwood.wfcore.common.pipenet.ac;

import com.gregtechceu.gtceu.api.pipenet.IPipeType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.StringRepresentable;

import com.norwood.wfcore.WFCore;

/**
 * AC cable thickness tiers. Throughput scales with the number of strands but with diminishing returns
 * (sqrt of strand count), so a single steel wire carries the base 512 EU/t and thicker cables add less per
 * strand. All thicknesses share one pipe-net type.
 */
public enum ACPipeType implements IPipeType<ACPipeProperties>, StringRepresentable {

    SINGLE("single", 0.25f, 1),
    DOUBLE("double", 0.375f, 2),
    QUADRUPLE("quadruple", 0.5f, 4),
    OCTAL("octal", 0.75f, 8),
    HEX("hex", 1.0f, 16);

    public static final ACPipeType[] VALUES = values();
    public static final ResourceLocation TYPE_ID = WFCore.id("ac");

    public final String typeName;
    public final float thickness;
    public final int strands;

    ACPipeType(String typeName, float thickness, int strands) {
        this.typeName = typeName;
        this.thickness = thickness;
        this.strands = strands;
    }

    @Override
    public float getThickness() {
        return thickness;
    }

    @Override
    public ACPipeProperties modifyProperties(ACPipeProperties baseProperties) {
        return new ACPipeProperties((long) (baseProperties.throughput * Math.sqrt(strands)));
    }

    @Override
    public boolean isPaintable() {
        return true;
    }

    @Override
    public ResourceLocation type() {
        return TYPE_ID;
    }

    @Override
    public String getSerializedName() {
        return typeName;
    }
}
