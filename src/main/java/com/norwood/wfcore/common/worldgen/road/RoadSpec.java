package com.norwood.wfcore.common.worldgen.road;

/**
 * Numeric half of a {@link RoadType}: all that geometry, profile and rasteriser read. No block providers, so it
 * loads without a bootstrapped registry. {@code markingWidth == 0} => no markings.
 */
public record RoadSpec(
        int priority,
        int width,
        int shoulder,
        int clearance,
        int vegetationMargin,
        int markingWidth,
        int markingDash,
        int markingGap,
        int baseDepth,
        int margin,
        double batter,
        RoadType.Grade grade,
        RoadType.Water water,
        boolean railing,
        int railingHeight,
        int pierSpacing,
        int pierRadius,
        int bridgeMinHeight,
        boolean tunnelLight,
        int lightSpacing,
        int tunnelHeight,
        int minCover) {

    public double pavedHalfWidth() {
        return width / 2.0;
    }

    public double outerHalfWidth() {
        return width / 2.0 + shoulder;
    }

    public double gradedHalfWidth() {
        return outerHalfWidth() + margin;
    }

    public double clearedHalfWidth() {
        return outerHalfWidth() + Math.max(margin, vegetationMargin);
    }

    public boolean paintedAt(int station) {
        int period = markingDash + markingGap;
        return markingWidth > 0 && (markingGap == 0 || Math.floorMod(station, period) < markingDash);
    }
}
