package com.norwood.wfcore.common.compute;

/**
 * Central holder for every tunable in the computation-mainframe simulation. All the "physics" constants that
 * used to be hardcoded in {@link com.norwood.wfcore.common.machine.MainframeMachine},
 * {@link com.norwood.wfcore.common.machine.compute.CoolingPartMachine} and {@link CPURegistry.CPUEntry} live
 * here so packs can retune them from KubeJS (via the {@code WFCompute.config()} binding).
 *
 * <p>
 * Fields are {@code volatile} and seeded with the historical defaults, so the getters are safe to read even
 * before any script or common-setup code runs. Setters clamp obviously-invalid values but otherwise trust the
 * caller — a pack author who wants a silly-hot mainframe is allowed to have one.
 */
public final class WFComputeConfig {

    private WFComputeConfig() {}

    // ---- thermal envelope -------------------------------------------------
    private static volatile double maxTemperature = 105.0;   // °C at which the mainframe explodes
    private static volatile double baseFrameMass = 500.0;    // thermal mass of the bare frame
    private static volatile double hatchThermalMass = 50.0;  // added thermal mass per physical hatch (cpu/ram/cooler)
    private static volatile double idleCooldownRate = 0.25;  // °C/t bled toward ambient while not providing
    private static volatile double explosionStrength = 10.0; // explosion power when maxTemperature is reached

    // ---- performance sag (throttling as it overheats) ---------------------
    private static volatile double sagStartTemp = 90.0;      // °C above which providable CWU starts dropping
    private static volatile double sagTempSpan = 10.0;       // °C over which the penalty ramps in
    private static volatile double sagPenaltyScale = 0.5;    // fraction of throughput lost at the top of the ramp
    private static volatile double forceActiveCoolTemp = 70.0; // °C above which liquid coolers run flat-out

    // ---- CPU physics ------------------------------------------------------
    private static volatile double heatRatio = 0.04;         // waste-EU -> heat conversion (EU_TO_HEAT_RATIO)
    private static volatile double efficiencyDropoff = 0.2;  // efficiency lost at full load (× load²)
    private static volatile double minEfficiency = 0.05;     // floor a CPU never drops below

    // ---- cooling ----------------------------------------------------------
    private static volatile double passiveCoolingBase = 0.05; // per-tier passive cooling coefficient
    private static volatile double activeCoolingScale = 0.1;  // scales raw coolant draw into a cooling rate
    private static volatile int liquidCoolantPerTick = 100;   // mB a single liquid cooler drains per tick

    // ---- ambient temperature ----------------------------------------------
    private static volatile double ambientDefault = 22.0;    // fallback ambient (no level/pos)
    private static volatile double ambientNether = 70.0;     // ambient in the Nether
    private static volatile double ambientEnd = 5.0;         // ambient in the End
    private static volatile double ambientBiomeScale = 30.0; // biome base-temperature -> °C multiplier
    private static volatile double ambientBiomeOffset = -5.0; // biome base-temperature -> °C offset

    public static double maxTemperature() {
        return maxTemperature;
    }

    public static double baseFrameMass() {
        return baseFrameMass;
    }

    public static double hatchThermalMass() {
        return hatchThermalMass;
    }

    public static double idleCooldownRate() {
        return idleCooldownRate;
    }

    public static double explosionStrength() {
        return explosionStrength;
    }

    public static double sagStartTemp() {
        return sagStartTemp;
    }

    public static double sagTempSpan() {
        return sagTempSpan;
    }

    public static double sagPenaltyScale() {
        return sagPenaltyScale;
    }

    public static double forceActiveCoolTemp() {
        return forceActiveCoolTemp;
    }

    public static double heatRatio() {
        return heatRatio;
    }

    public static double efficiencyDropoff() {
        return efficiencyDropoff;
    }

    public static double minEfficiency() {
        return minEfficiency;
    }

    public static double passiveCoolingBase() {
        return passiveCoolingBase;
    }

    public static double activeCoolingScale() {
        return activeCoolingScale;
    }

    public static int liquidCoolantPerTick() {
        return liquidCoolantPerTick;
    }

    public static double ambientDefault() {
        return ambientDefault;
    }

    public static double ambientNether() {
        return ambientNether;
    }

    public static double ambientEnd() {
        return ambientEnd;
    }

    public static double ambientBiomeScale() {
        return ambientBiomeScale;
    }

    public static double ambientBiomeOffset() {
        return ambientBiomeOffset;
    }


    public static void maxTemperature(double v) {
        maxTemperature = v;
    }

    public static void baseFrameMass(double v) {
        baseFrameMass = Math.max(1.0, v);
    }

    public static void hatchThermalMass(double v) {
        hatchThermalMass = Math.max(0.0, v);
    }

    public static void idleCooldownRate(double v) {
        idleCooldownRate = Math.max(0.0, v);
    }

    public static void explosionStrength(double v) {
        explosionStrength = Math.max(0.0, v);
    }

    public static void sagStartTemp(double v) {
        sagStartTemp = v;
    }

    public static void sagTempSpan(double v) {
        sagTempSpan = Math.max(0.001, v);
    }

    public static void sagPenaltyScale(double v) {
        sagPenaltyScale = Math.max(0.0, v);
    }

    public static void forceActiveCoolTemp(double v) {
        forceActiveCoolTemp = v;
    }

    public static void heatRatio(double v) {
        heatRatio = Math.max(0.0, v);
    }

    public static void efficiencyDropoff(double v) {
        efficiencyDropoff = Math.max(0.0, v);
    }

    public static void minEfficiency(double v) {
        minEfficiency = Math.min(1.0, Math.max(0.0, v));
    }

    public static void passiveCoolingBase(double v) {
        passiveCoolingBase = Math.max(0.0, v);
    }

    public static void activeCoolingScale(double v) {
        activeCoolingScale = Math.max(0.0, v);
    }

    public static void liquidCoolantPerTick(int v) {
        liquidCoolantPerTick = Math.max(0, v);
    }

    public static void ambientDefault(double v) {
        ambientDefault = v;
    }

    public static void ambientNether(double v) {
        ambientNether = v;
    }

    public static void ambientEnd(double v) {
        ambientEnd = v;
    }

    public static void ambientBiomeScale(double v) {
        ambientBiomeScale = v;
    }

    public static void ambientBiomeOffset(double v) {
        ambientBiomeOffset = v;
    }
}
