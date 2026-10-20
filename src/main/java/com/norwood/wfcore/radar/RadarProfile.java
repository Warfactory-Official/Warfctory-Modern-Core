package com.norwood.wfcore.radar;

import com.gregtechceu.gtceu.api.GTValues;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tunables of one radar flavour, baked from {@code wfcore-radar.toml} by {@link RadarConfig#bake()}; KubeJS ops
 * ({@link #script}) re-applied after every bake.
 */
public final class RadarProfile {

    public static final RadarProfile RADAR = new RadarProfile("radar", true, 200, 10, 2000, 5000, 128, 3,
            GTValues.HV, 128, 15000, 0, 0, 1.0, 1.0);
    public static final RadarProfile SEISMIC = new RadarProfile("seismic_radar", false, 48, 3, 200, 500, 16, 2,
            GTValues.MV, 16, 6000, 1024, 32, 4.0, 0.0);

    /** Machine id; lang keys {@code wfcore.machine.<name>.*}. */
    public final String name;
    /** Radar dish gates: Y >= 100 + sky access. */
    public final boolean needsSky;
    final Defaults defaults;

    volatile int eps;
    volatile int minPts;
    volatile int calibMinDistance;
    volatile int calibMaxDistance;
    volatile int calibTolerance;
    volatile int calibBaseCount;
    volatile int minTier;
    volatile int baseCWUt;
    volatile int scanTicks;
    volatile int range;
    volatile int depthThreshold;
    volatile double deepMultiplier;
    volatile double shallowMultiplier;

    private final List<Consumer<RadarProfile>> scriptOps = new ArrayList<>();
    private boolean baked;

    record Defaults(int eps, int minPts, int calibMinDistance, int calibMaxDistance, int calibTolerance,
                    int calibBaseCount, int minTier, int baseCWUt, int scanTicks, int range, int depthThreshold,
                    double deepMultiplier, double shallowMultiplier) {}

    private RadarProfile(String name, boolean needsSky, int eps, int minPts, int calibMinDistance,
                         int calibMaxDistance, int calibTolerance, int calibBaseCount, int minTier, int baseCWUt,
                         int scanTicks, int range, int depthThreshold, double deepMultiplier,
                         double shallowMultiplier) {
        this.name = name;
        this.needsSky = needsSky;
        this.defaults = new Defaults(eps, minPts, calibMinDistance, calibMaxDistance, calibTolerance,
                calibBaseCount, minTier, baseCWUt, scanTicks, range, depthThreshold, deepMultiplier,
                shallowMultiplier);
        apply(defaults);
    }

    synchronized void apply(Defaults d) {
        eps = d.eps();
        minPts = d.minPts();
        calibMinDistance = d.calibMinDistance();
        calibMaxDistance = Math.max(d.calibMaxDistance(), d.calibMinDistance());
        calibTolerance = d.calibTolerance();
        calibBaseCount = d.calibBaseCount();
        minTier = d.minTier();
        baseCWUt = d.baseCWUt();
        scanTicks = d.scanTicks();
        range = d.range();
        depthThreshold = d.depthThreshold();
        deepMultiplier = d.deepMultiplier();
        shallowMultiplier = d.shallowMultiplier();
        for (Consumer<RadarProfile> op : scriptOps) {
            op.accept(this);
        }
        baked = true;
    }

    /** KubeJS override; survives config reloads. */
    public synchronized void script(Consumer<RadarProfile> op) {
        scriptOps.add(op);
        if (baked) {
            op.accept(this);
        }
    }

    public void setEps(int v) { eps = v; }
    public void setMinPts(int v) { minPts = v; }
    public void setCalibrator(int min, int max, int tolerance, int baseCount) {
        calibMinDistance = min;
        calibMaxDistance = Math.max(max, min);
        calibTolerance = tolerance;
        calibBaseCount = baseCount;
    }
    public void setScan(int minTier, int baseCWUt, int scanTicks) {
        this.minTier = minTier;
        this.baseCWUt = baseCWUt;
        this.scanTicks = scanTicks;
    }
    public void setRange(int v) { range = v; }
    public void setDepth(int threshold, double deep, double shallow) {
        depthThreshold = threshold;
        deepMultiplier = deep;
        shallowMultiplier = shallow;
    }

    public int eps() { return eps; }
    public int minPts() { return minPts; }
    public int calibMinDistance() { return calibMinDistance; }
    public int calibMaxDistance() { return calibMaxDistance; }
    public int calibTolerance() { return calibTolerance; }
    public int calibBaseCount() { return calibBaseCount; }
    public int minTier() { return minTier; }
    /** CWU/t at {@code minTier} and {@code minTier + 1}; x4 per tier above. */
    public int baseCWUt() { return baseCWUt; }
    /** Scan ticks at {@code minTier}; x0.8 per tier above. */
    public int scanTicks() { return scanTicks; }
    /** Horizontal radius around the machine; 0 = whole level. */
    public int range() { return range; }
    /** Blocks below {@code level.getSeaLevel()} at which a target counts as deep. */
    public int depthThreshold() { return depthThreshold; }
    public double deepMultiplier() { return deepMultiplier; }
    /** 0 => shallow targets (players included) dropped. */
    public double shallowMultiplier() { return shallowMultiplier; }

    /** Richness weight of a target at {@code y}; 0 => not detected. */
    public double weight(int y, int seaLevel) {
        return y < seaLevel - depthThreshold ? deepMultiplier : shallowMultiplier;
    }
}
