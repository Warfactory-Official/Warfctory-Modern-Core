package com.norwood.wfcore.integration.kubejs;

import com.norwood.wfcore.common.compute.WFComputeConfig;
import com.norwood.wfcore.common.compute.WFComputeScripts;

/**
 * Fluent, chainable view over {@link WFComputeConfig} for KubeJS, reached via {@code WFCompute.config()}.
 *
 * <p>
 * Every setter records the change and applies it after WFCore's defaults (so scripts always win), then returns
 * {@code this} so calls can be chained:
 *
 * <pre>{@code
 * WFCompute.config()
 *     .maxTemperature(120)
 *     .sagStartTemp(95)
 *     .passiveCoolingBase(0.06)
 * }</pre>
 *
 * <p>
 * Getters read the current live value (i.e. after this binding's queued changes have been applied).
 */
public final class WFComputeConfigBinding {

    WFComputeConfigBinding() {}

    // ---- thermal envelope -------------------------------------------------

    /** °C at which the mainframe explodes (default 105). */
    public WFComputeConfigBinding maxTemperature(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.maxTemperature(v));
        return this;
    }

    /** Thermal mass of the bare frame — higher = slower to heat and cool (default 500). */
    public WFComputeConfigBinding baseFrameMass(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.baseFrameMass(v));
        return this;
    }

    /** Added thermal mass per physical hatch (cpu/ram/cooler) (default 50). */
    public WFComputeConfigBinding hatchThermalMass(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.hatchThermalMass(v));
        return this;
    }

    /** °C/t bled toward ambient while the mainframe is idle / disabled (default 0.25). */
    public WFComputeConfigBinding idleCooldownRate(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.idleCooldownRate(v));
        return this;
    }

    /** Explosion power when the max temperature is reached (default 10). */
    public WFComputeConfigBinding explosionStrength(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.explosionStrength(v));
        return this;
    }

    // ---- performance sag --------------------------------------------------

    /** °C above which providable computation starts throttling (default 90). */
    public WFComputeConfigBinding sagStartTemp(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.sagStartTemp(v));
        return this;
    }

    /** °C span over which the throttle ramps to full (default 10). */
    public WFComputeConfigBinding sagTempSpan(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.sagTempSpan(v));
        return this;
    }

    /** Fraction of throughput lost at the top of the throttle ramp (default 0.5). */
    public WFComputeConfigBinding sagPenaltyScale(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.sagPenaltyScale(v));
        return this;
    }

    /** °C above which liquid coolers are forced to run flat-out (default 70). */
    public WFComputeConfigBinding forceActiveCoolTemp(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.forceActiveCoolTemp(v));
        return this;
    }

    // ---- CPU physics ------------------------------------------------------

    /** Waste-EU -> heat conversion factor (default 0.04). */
    public WFComputeConfigBinding heatRatio(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.heatRatio(v));
        return this;
    }

    /** Efficiency lost at full load, multiplied by load² (default 0.2). */
    public WFComputeConfigBinding efficiencyDropoff(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.efficiencyDropoff(v));
        return this;
    }

    /** Floor a CPU's efficiency never drops below (default 0.05). */
    public WFComputeConfigBinding minEfficiency(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.minEfficiency(v));
        return this;
    }

    // ---- cooling ----------------------------------------------------------

    /** Per-tier passive-cooling coefficient; effective rate is base·(fanTier+1) (default 0.05). */
    public WFComputeConfigBinding passiveCoolingBase(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.passiveCoolingBase(v));
        return this;
    }

    /** Scales a liquid cooler's raw coolant draw into a cooling rate (default 0.1). */
    public WFComputeConfigBinding activeCoolingScale(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.activeCoolingScale(v));
        return this;
    }

    /** mB a single liquid cooler drains per tick (default 100). */
    public WFComputeConfigBinding liquidCoolantPerTick(int v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.liquidCoolantPerTick(v));
        return this;
    }

    // ---- ambient temperature ----------------------------------------------

    /** Fallback ambient °C when no level/position is available (default 22). */
    public WFComputeConfigBinding ambientDefault(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.ambientDefault(v));
        return this;
    }

    /** Ambient °C in the Nether (default 70). */
    public WFComputeConfigBinding ambientNether(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.ambientNether(v));
        return this;
    }

    /** Ambient °C in the End (default 5). */
    public WFComputeConfigBinding ambientEnd(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.ambientEnd(v));
        return this;
    }

    /** Multiplier from a biome's base temperature to ambient °C (default 30). */
    public WFComputeConfigBinding ambientBiomeScale(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.ambientBiomeScale(v));
        return this;
    }

    /** Offset added after {@link #ambientBiomeScale(double)} when deriving ambient °C (default -5). */
    public WFComputeConfigBinding ambientBiomeOffset(double v) {
        WFComputeScripts.enqueue(() -> WFComputeConfig.ambientBiomeOffset(v));
        return this;
    }


    public double getMaxTemperature() {
        return WFComputeConfig.maxTemperature();
    }

    public double getBaseFrameMass() {
        return WFComputeConfig.baseFrameMass();
    }

    public double getHatchThermalMass() {
        return WFComputeConfig.hatchThermalMass();
    }

    public double getIdleCooldownRate() {
        return WFComputeConfig.idleCooldownRate();
    }

    public double getExplosionStrength() {
        return WFComputeConfig.explosionStrength();
    }

    public double getSagStartTemp() {
        return WFComputeConfig.sagStartTemp();
    }

    public double getSagTempSpan() {
        return WFComputeConfig.sagTempSpan();
    }

    public double getSagPenaltyScale() {
        return WFComputeConfig.sagPenaltyScale();
    }

    public double getForceActiveCoolTemp() {
        return WFComputeConfig.forceActiveCoolTemp();
    }

    public double getHeatRatio() {
        return WFComputeConfig.heatRatio();
    }

    public double getEfficiencyDropoff() {
        return WFComputeConfig.efficiencyDropoff();
    }

    public double getMinEfficiency() {
        return WFComputeConfig.minEfficiency();
    }

    public double getPassiveCoolingBase() {
        return WFComputeConfig.passiveCoolingBase();
    }

    public double getActiveCoolingScale() {
        return WFComputeConfig.activeCoolingScale();
    }

    public int getLiquidCoolantPerTick() {
        return WFComputeConfig.liquidCoolantPerTick();
    }

    public double getAmbientDefault() {
        return WFComputeConfig.ambientDefault();
    }

    public double getAmbientNether() {
        return WFComputeConfig.ambientNether();
    }

    public double getAmbientEnd() {
        return WFComputeConfig.ambientEnd();
    }

    public double getAmbientBiomeScale() {
        return WFComputeConfig.ambientBiomeScale();
    }

    public double getAmbientBiomeOffset() {
        return WFComputeConfig.ambientBiomeOffset();
    }
}
