package com.norwood.wfcore.common.worldgen.road;

/**
 * Numeric half of a {@link RoadType}: all that geometry, profile and rasteriser read. No block providers, so it
 * loads without a bootstrapped registry. {@code markingWidth == 0} => no markings; {@code lampHeight == 0} => no
 * lamps; {@code lampSpacing == 0} => lamps at junctions only; {@code guardRailFill == 0} => no guard rails;
 * {@code archRise == 0} => flat soffit; {@code trussLength == 0} => no truss.
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
        double cutBatter,
        RoadType.Grade grade,
        RoadType.Water water,
        boolean railing,
        int railingHeight,
        int pierSpacing,
        int pierRadius,
        int bridgeMinHeight,
        int archRise,
        int trussLength,
        boolean tunnelLight,
        int lightSpacing,
        int tunnelHeight,
        int minCover,
        int tunnelMinLength,
        boolean gradeSeparated,
        int cornerRadius,
        int lampSpacing,
        int lampHeight,
        int guardRailFill,
        boolean stopSign) {

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
