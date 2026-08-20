package com.norwood.wfcore.common.darkness;


public final class DarknessEnforcement {

    private static volatile boolean active = true;
    private static volatile boolean blockLightOnly = false;
    private static volatile boolean ignoreMoonPhase = true;
    private static volatile boolean darkOverworld = true;
    private static volatile boolean darkNether = true;
    private static volatile boolean darkEnd = true;
    private static volatile boolean darkDefault = true;
    private static volatile boolean darkSkyless = false;
    private static volatile double darkNetherFog = 0.5;
    private static volatile double darkEndFog = 0.0;

    private DarknessEnforcement() {}


    public static void set(boolean active, boolean blockLightOnly, boolean ignoreMoonPhase,
            boolean darkOverworld, boolean darkNether, boolean darkEnd, boolean darkDefault,
            boolean darkSkyless, double darkNetherFog, double darkEndFog) {
        DarknessEnforcement.active = active;
        DarknessEnforcement.blockLightOnly = blockLightOnly;
        DarknessEnforcement.ignoreMoonPhase = ignoreMoonPhase;
        DarknessEnforcement.darkOverworld = darkOverworld;
        DarknessEnforcement.darkNether = darkNether;
        DarknessEnforcement.darkEnd = darkEnd;
        DarknessEnforcement.darkDefault = darkDefault;
        DarknessEnforcement.darkSkyless = darkSkyless;
        DarknessEnforcement.darkNetherFog = darkNetherFog;
        DarknessEnforcement.darkEndFog = darkEndFog;
    }

    /** When false the mixin leaves True Darkness alone (enforcement disabled by config). */
    public static boolean active() {
        return active;
    }

    public static boolean blockLightOnly() {
        return blockLightOnly;
    }

    public static boolean ignoreMoonPhase() {
        return ignoreMoonPhase;
    }

    public static boolean darkOverworld() {
        return darkOverworld;
    }

    public static boolean darkNether() {
        return darkNether;
    }

    public static boolean darkEnd() {
        return darkEnd;
    }

    public static boolean darkDefault() {
        return darkDefault;
    }

    public static boolean darkSkyless() {
        return darkSkyless;
    }

    public static double darkNetherFog() {
        return darkNetherFog;
    }

    public static double darkEndFog() {
        return darkEndFog;
    }
}
