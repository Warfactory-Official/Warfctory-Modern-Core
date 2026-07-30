package com.norwood.wfcore.common.data;

import net.minecraft.resources.ResourceLocation;

import com.norwood.wfcore.WFCore;
import com.tterrag.registrate.util.entry.ItemEntry;
import com.tterrag.registrate.util.nullness.NonNullBiConsumer;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.MissileModels;
import com.wf.wfballistics.flight.FlightStageRegistry;
import com.wf.wfballistics.item.MissileItem;
import com.wf.wfballistics.item.MissilePreset;
import com.wf.wfballistics.warhead.WarheadRegistry;
import com.norwood.wfcore.common.sound.WFSounds;

import java.util.Map;
import java.util.function.UnaryOperator;

import static com.norwood.wfcore.WFCore.WF_MACHINES;

/**
 * WFCore's missile suite. Each entry is a carryable {@link MissileItem} wrapping a {@link MissilePreset} built
 * with WF-B's public builder (see {@link #missile}); the warheads come from {@link WFWarheads}.
 *
 * <h2>Families</h2>
 * The Missile Factory / Launch Silo / Interceptor Battery all unlock at <b>HV</b>, so the whole suite is gated
 * HV-and-up; tiers below are relative <b>within</b> that (HV entry → EV → IV apex). Balance is tuned against the
 * interceptor engine, whose kill math is (verified in {@code MissileEntity.tryIntercept}/{@code evadeBoost}):
 * <pre>
 *   P(survive one engagement) = (1 - interceptChance)
 *                             + interceptChance · evasion · (1.5 in the terminal dive)
 *                                              · min(1, missileSpeed / interceptorSpeed)
 * </pre>
 * — so evasion only pays off when the round can roughly out-run the interceptor, and each dodge burns 150 fuel.
 *
 * <ul>
 * <li><b>Demolition</b> ({@link #HE}, {@link #THERMOBARIC}, {@link #MININUKE}) — slow, easily intercepted, big
 *     craters. A ray-march sphere can't drain through tungsten plating, so these never crack top armour.</li>
 * <li><b>Penetrator</b> ({@link #PENETRATOR}, {@link #PENETRATOR_SUPERSONIC}, {@link #PENETRATOR_HYPERSONIC}) —
 *     an HE payload that trades crater for speed + evasion, scaling to a near-sure hit at hypersonic. Still just
 *     a sphere, so still can't defeat tungsten.</li>
 * <li><b>Bunker buster</b> ({@link #BUNKER_BUSTER}, {@link #BUNKER_BUSTER_HEAVY}) — a narrow shaped jet that
 *     cracks all defences to gravel but can't finish tungsten alone (needs a follow-up round). Short range, poor
 *     accel, ~10-block CEP, but evasive and nimble.</li>
 * <li><b>Frag / cluster</b> ({@link #CLUSTER}, {@link #CLUSTER_FIRE}, {@link #CLUSTER_GAS}, {@link #SKYFALL}) —
 *     anti-entity area coverage, minimal terrain damage; the apex Skyfall bursts high and rains 9 seekers.</li>
 * <li><b>EMP</b> ({@link #EMP}, {@link #EMP_HEAVY}, {@link #EMP_CLUSTER}, {@link #EMP_LANCE}) — stealth
 *     terrain-huggers that disable machinery; a wide bomblet variant and a fast pinpoint-ray penetrator.</li>
 * <li><b>Interceptor</b> ({@link #INTERCEPTOR}, {@link #INTERCEPTOR_MK2}, {@link #INTERCEPTOR_ACE},
 *     {@link #INTERCEPTOR_CLUSTER}) — scale to match; the Ace is competitive with all but the top evasive round,
 *     the Cluster airbursts into small interceptors to blunt a barrage.</li>
 * <li><b>Drones</b> ({@link #STRIKE_DRONE}, {@link #GAS_DRONE}, {@link #LOITER_DRONE}) — a separate,
 *     deliberately cheap class (constant-note moped loop, wide anti-personnel blast, tiny structural footprint).
 *     The Loiter Drone is the odd one out: inert, long-legged, and it just orbits its objective until it runs
 *     dry — a recon/decoy bird rather than a weapon.</li>
 * </ul>
 *
 * <p>Airframes/warheads are referenced by id; an unknown id silently falls back to a default, so keep them in
 * sync with WF-B ({@link MissileModels}) and {@link WFWarheads}.
 */
public class WFMissiles {

    // Default shot-down behaviour for every HARDENED wfcore missile (applied in the missile() helper, so a
    // preset can still override it): 70% a full-warhead DETONATE, the rest split between an engines-out drop and
    // a tumbling spin-out. An interceptor kill ignores this and forces a guaranteed 100% DETONATE.
    private static final MissilePreset.DownedActionPicker DEFAULT_DOWNED = MissilePreset.DownedActionPicker.weighted(
            Map.of(MissileEntity.DownedAction.DETONATE, 70,
                    MissileEntity.DownedAction.POWER_LOSS, 15,
                    MissileEntity.DownedAction.SPIN_OUT, 15));

    // Default engine "rev": rocket missiles pitch up with their own flight speed. Drones opt out (steady moped).
    private static final double DEFAULT_MISSILE_REV = 0.05;

    // Every top-attack round uses ControlledDiveStage. It dives at max(cruiseSpeed, 5) with angled pure-pursuit
    // and a 2-block commit radius, so a fast missile still comes down fast (a hypersonic dives at ~7 b/t) but
    // actually converges on the target. WF-B's plain "dive"/VerticalDiveStage (~18 b/t) plunges past and misses.
    private static ResourceLocation controlledDive() {
        return FlightStageRegistry.rl(ControlledDiveStage.ID);
    }

    // Fast controlled descent (~12 b/t) for the ICBM class: quick enough that a lower-tier interceptor can't run
    // it down (crossing shots only), yet still converges (same pure-pursuit logic, not a blind plunge).
    private static ResourceLocation icbmDive() {
        return FlightStageRegistry.rl(ControlledDiveStage.ICBM_ID);
    }

    // --- Demolition ---
    public static ItemEntry<MissileItem> HE;
    public static ItemEntry<MissileItem> DUMMY;
    public static ItemEntry<MissileItem> LONG_RANGE;
    public static ItemEntry<MissileItem> THERMOBARIC;
    public static ItemEntry<MissileItem> MININUKE;
    // --- Penetrator ---
    public static ItemEntry<MissileItem> PENETRATOR;
    public static ItemEntry<MissileItem> PENETRATOR_SUPERSONIC;
    public static ItemEntry<MissileItem> PENETRATOR_HYPERSONIC;
    // --- ICBM ---
    public static ItemEntry<MissileItem> ICBM;
    public static ItemEntry<MissileItem> ICBM_HEAVY;
    // --- Bunker buster ---
    public static ItemEntry<MissileItem> BUNKER_BUSTER;
    public static ItemEntry<MissileItem> BUNKER_BUSTER_HEAVY;
    public static ItemEntry<MissileItem> BUNKER_TUNNELLER;
    // --- Frag / cluster ---
    public static ItemEntry<MissileItem> CLUSTER;
    public static ItemEntry<MissileItem> CLUSTER_FIRE;
    public static ItemEntry<MissileItem> CLUSTER_GAS;
    public static ItemEntry<MissileItem> FRAG_STORM;
    public static ItemEntry<MissileItem> SKYFALL;
    // --- EMP ---
    public static ItemEntry<MissileItem> EMP;
    public static ItemEntry<MissileItem> EMP_HEAVY;
    public static ItemEntry<MissileItem> EMP_CLUSTER;
    public static ItemEntry<MissileItem> EMP_LANCE;
    // --- Interceptor ---
    public static ItemEntry<MissileItem> INTERCEPTOR;
    public static ItemEntry<MissileItem> INTERCEPTOR_MK2;
    public static ItemEntry<MissileItem> INTERCEPTOR_ACE;
    public static ItemEntry<MissileItem> INTERCEPTOR_CLUSTER;
    // --- Drones (cheap, separate class) ---
    public static ItemEntry<MissileItem> STRIKE_DRONE;
    public static ItemEntry<MissileItem> GAS_DRONE;
    public static ItemEntry<MissileItem> LOITER_DRONE;

    private WFMissiles() {}

    public static void init() {
        // Custom warheads, the controlled-dive attack stage, and the default damage response first: their ids
        // must be registered before any preset below references them (presets build lazily in the item factory).
        WFWarheads.register();
        ControlledDiveStage.register();
        LoiterUntilDryStage.register();
        WFDamageResponses.register();

        // =========================== DEMOLITION ===========================================================
        // Slow, high, near-vertical top-attack; big destructive craters; easy interceptor bait (no evasion).

        HE = missile("he", "High-Explosive Missile",
                MissileModels.rl("v2"), WFWarheads.HE,
                b -> b.highAltitude(300.0).cruiseSpeed(1.3).health(55.0f)
                        .diveAngleRange(68.0, 90.0).attackStage(controlledDive())
                        .accel(0.2, 0.3).fuel(MissileEntity.FuelType.LIQUID, 8000));

        DUMMY = missile("dummy", "Dummy Missile",
                MissileModels.rl("v2"), WarheadRegistry.rl("inert"),
                b -> b.highAltitude(300.0).cruiseSpeed(1.1).health(10.0f)
                        .diveAngleRange(68.0, 90.0).attackStage(controlledDive())
                        .accel(0.1, 0.4).fuel(MissileEntity.FuelType.LIQUID, 8000));

        LONG_RANGE = missile("long_range", "Long-Range Missile",
                MissileModels.rl("booster"), WFWarheads.LONG_RANGE,
                b -> b.highAltitude(300.0).cruiseSpeed(2.0).health(45.0f)
                        .diveAngleRange(68.0, 90.0).attackStage(controlledDive())
                        .accel(0.3, 0.4).fuel(MissileEntity.FuelType.LIQUID, 15000));

        // Thermobaric: modest crater, enormous anti-everything overpressure. Slow and fragile.
        THERMOBARIC = missile("thermobaric", "Thermobaric Missile",
                MissileModels.rl("strong"), WFWarheads.THERMOBARIC,
                b -> b.highAltitude(300.0).cruiseSpeed(1.2).health(50.0f)
                        .diveAngleRange(68.0, 90.0).attackStage(controlledDive())
                        .exhaustColor(0xFF6A2D)
                        .accel(0.2, 0.3).fuel(MissileEntity.FuelType.LIQUID, 9000));

        // Mininuke: the demolition apex — a ~50-block-wide crater PLUS a deep penetrating jet (WFWarheads
        // custom) so it drives through fortification instead of surface-scorching. Very slow, trivially intercepted.
        MININUKE = missile("mininuke", "Tactical Mininuke Missile",
                MissileModels.rl("atlas_doomsday"), WFWarheads.MININUKE,
                b -> b.highAltitude(320.0).cruiseSpeed(1.0).health(60.0f)
                        .diveAngleRange(80.0, 90.0).attackStage(controlledDive())
                        .accel(0.15, 0.25).fuel(MissileEntity.FuelType.LIQUID, 6000));

        // =========================== PENETRATOR ===========================================================
        // HE payload, but fast + evasive with a quick vertical dive. Scales to a near-sure hit at hypersonic.
        // Never defeats tungsten (plain sphere). evasion tuned so it beats a tier-1 interceptor (~spd 8) often.

        PENETRATOR = missile("penetrator", "Penetrator Missile",
                MissileModels.rl("neon"), WFWarheads.HE,
                b -> b.highAltitude(300.0).cruiseSpeed(3.0).health(45.0f)
                        .diveAngleRange(80.0, 90.0).attackStage(controlledDive())
                        .evasion(0.55f)
                        .accel(1.0, 1.0).fuel(MissileEntity.FuelType.LIQUID, 12000));

        PENETRATOR_SUPERSONIC = missile("penetrator_supersonic", "Supersonic Penetrator Missile",
                MissileModels.rl("atlas"), WFWarheads.HE,
                b -> b.highAltitude(300.0).cruiseSpeed(4.5).health(45.0f)
                        .diveAngleRange(80.0, 90.0).attackStage(controlledDive())
                        .evasion(0.7f).evasiveManeuver()
                        .accel(1.2, 1.2).fuel(MissileEntity.FuelType.LIQUID, 15000));

        PENETRATOR_HYPERSONIC = missile("penetrator_hypersonic", "Hypersonic Penetrator Missile",
                MissileModels.rl("atlas"), WFWarheads.HE,
                b -> b.highAltitude(300.0).cruiseSpeed(7.0).turnRate(0.5).health(40.0f)
                        .diveAngleRange(80.0, 90.0).attackStage(controlledDive())
                        .evasion(0.9f).evasiveManeuver().exhaustColor(0x66E0FF)
                        .accel(1.5, 1.2).fuel(MissileEntity.FuelType.LIQUID, 18000));

        // =========================== ICBM ==================================================================
        // The long-range line fleshed out: extreme range, high-altitude fast cruise, a pure 90-degree top-attack,
        // and a FAST controlled descent (~12 b/t) that lower-tier interceptors can't run down — they're reduced to
        // unreliable crossing shots — so it beats them on pure speed + timing, NOT evasion (it has none). Low blast
        // radius but a strong concentrated punch; snappy accel/decel so it reaches cruise + descent speed quickly.

        // Tanky + resistant: a big shoot-down pool (hard to chip down / CIWS through) plus the HARDENED_ICBM
        // response (immune to gunfire/fire, and only 25% of everything else), so it shrugs off attacks a normal
        // missile wouldn't. A dedicated interceptor still forces a clean kill — health/resistance don't stop that.
        ICBM = missile("icbm", "ICBM",
                MissileModels.rl("atlas"), WFWarheads.ICBM,
                b -> b.highAltitude(340.0).cruiseSpeed(5.0).turnRate(0.6).health(120.0f)
                        .damageResponse(WFDamageResponses.HARDENED_ICBM_ID)
                        .attackAngle(90.0).attackStage(icbmDive())
                        .accel(1.5, 1.5).fuel(MissileEntity.FuelType.SOLID, 40000));

        ICBM_HEAVY = missile("icbm_heavy", "Heavy ICBM",
                MissileModels.rl("atlas"), WFWarheads.ICBM_HEAVY,
                b -> b.highAltitude(360.0).cruiseSpeed(6.0).turnRate(0.6).health(160.0f)
                        .damageResponse(WFDamageResponses.HARDENED_ICBM_ID)
                        .attackAngle(90.0).attackStage(icbmDive()).exhaustColor(0xF0F0FF)
                        .accel(1.8, 1.8).fuel(MissileEntity.FuelType.SOLID, 60000));

        // =========================== BUNKER BUSTER =========================================================
        // Narrow shaped jet along the impact heading: cracks everything to gravel, breaks what it can. Short
        // range (small fuel), sluggish accel, evasive and turns well. Needs an HE follow-up. The family's
        // unreliable-accuracy trait belongs to the ENTRY tier — the Mk1's crude guidance gives a ~10-block CEP —
        // and higher tiers buy it back with tighter guidance (Mk2 ~2.5), so accuracy IMPROVES up the ladder.

        BUNKER_BUSTER = missile("bunker_buster", "Bunker Buster Missile",
                MissileModels.rl("v2_bunker"), WFWarheads.BUNKER_MK1,
                b -> b.highAltitude(280.0).cruiseSpeed(4.0).health(70.0f)
                        .diveAngleRange(82.0, 90.0).attackStage(controlledDive())
                        .evasion(0.5f).turnRate(0.55).accuracy(10.0)
                        .accel(0.15, 0.1).fuel(MissileEntity.FuelType.LIQUID, 3000));

        BUNKER_BUSTER_HEAVY = missile("bunker_buster_heavy", "Heavy Bunker Buster Missile",
                MissileModels.rl("carrier"), WFWarheads.BUNKER_MK2,
                b -> b.highAltitude(280.0).cruiseSpeed(4.5).health(90.0f)
                        .diveAngleRange(82.0, 90.0).attackStage(controlledDive())
                        .evasion(0.6f).turnRate(0.6).accuracy(2.5)
                        .accel(0.18, 0.12).fuel(MissileEntity.FuelType.LIQUID, 3500));

        // Tunneller (final tier): near-vertical so the shaft drives straight down; bores in up to 15 blocks and
        // detonates inside. Family apex → most accurate (~1-block CEP), toughest, but still short-ranged/sluggish.
        BUNKER_TUNNELLER = missile("bunker_tunneller", "Tunneller Bunker Buster Missile",
                MissileModels.rl("huge_bunker"), WFWarheads.TUNNELLER,
                b -> b.highAltitude(280.0).cruiseSpeed(4.5).health(110.0f)
                        .diveAngleRange(86.0, 90.0).attackStage(controlledDive())
                        .evasion(0.6f).turnRate(0.6).accuracy(1.0)
                        .accel(0.18, 0.12).fuel(MissileEntity.FuelType.LIQUID, 4000));

        // =========================== FRAG / CLUSTER ========================================================
        // Anti-entity area coverage, little-to-no ground damage. The airburst offset lets bomblets rain.

        CLUSTER = missile("cluster", "Cluster Munition Missile",
                MissileModels.rl("v2_cluster"), WarheadRegistry.rl("fragmentation"),
                b -> b.highAltitude(280.0).cruiseSpeed(2.0).health(30.0f).fragmentCount(32)
                        .explosionOffset(24.0f)
                        .diveAngleRange(55.0, 75.0).attackStage(controlledDive())
                        .accel(0.3, 0.3).fuel(MissileEntity.FuelType.LIQUID, 12000));

        CLUSTER_FIRE = missile("cluster_fire", "Incendiary Cluster Missile",
                MissileModels.rl("v2_incendiary"), WFWarheads.FIRE_CLUSTER,
                b -> b.highAltitude(280.0).cruiseSpeed(2.0).health(30.0f).fragmentCount(24)
                        .explosionOffset(24.0f).exhaustColor(0xFF7A1A)
                        .diveAngleRange(55.0, 75.0).attackStage(controlledDive())
                        .accel(0.3, 0.3).fuel(MissileEntity.FuelType.LIQUID, 12000));

        // Gas cluster: scattered small, short-lived mustard pools
        CLUSTER_GAS = missile("cluster_gas", "Chemical Cluster Missile",
                MissileModels.rl("taint"), WFWarheads.GAS_SWISS,
                b -> b.highAltitude(260.0).cruiseSpeed(1.8).health(30.0f)
                        .diveAngleRange(70.0, 88.0).attackStage(controlledDive())
                        .exhaustColor(0xB6C43A)
                        .accel(0.25, 0.3).fuel(MissileEntity.FuelType.LIQUID, 12000));

        // Fragmentation storm: bursts high into 9 small frag missiles spread over a wide area; each throws 4
        // low-yield bomblets.
        FRAG_STORM = missile("frag_storm", "Fragmentation Storm Missile",
                MissileModels.rl("cluster"), WFWarheads.FRAG_STORM,
                b -> b.highAltitude(300.0).cruiseSpeed(2.5).health(35.0f)
                        .explosionOffset(50.0f).exhaustColor(0xE0C060)
                        .diveAngleRange(70.0, 90.0).attackStage(controlledDive())
                        .accel(0.35, 0.35).fuel(MissileEntity.FuelType.LIQUID, 14000));

        // Skyfall (apex): bursts high (large airburst offset = fires almost as it enters the dive) and rains 9
        // submunitions, each seeking a distinct entity within ~100 blocks of the burst.
        SKYFALL = missile("skyfall", "Skyfall Cluster Missile",
                MissileModels.rl("huge_cluster"), WFWarheads.SKYFALL,
                b -> b.highAltitude(320.0).cruiseSpeed(3.0).health(40.0f)
                        .explosionOffset(60.0f).exhaustColor(0xFFC24D)
                        .diveAngleRange(70.0, 90.0).attackStage(controlledDive())
                        .accel(0.4, 0.4).fuel(MissileEntity.FuelType.LIQUID, 16000));

        // =========================== EMP ===================================================================
        // Stealth (invisible to auto-acquisition) terrain-huggers that disable machinery; radius scales by tier.

        EMP = missile("emp", "EMP Missile",
                MissileModels.rl("stealth"), WFWarheads.EMP_SMALL,
                b -> b.terrainFollow(8.0).cruiseSpeed(1.5).health(25.0f).stealth()
                        .exhaustColor(0x3AA0FF)
                        .diveAngleRange(30.0, 55.0).attackStage(controlledDive())
                        .accel(0.2, 0.3).fuel(MissileEntity.FuelType.LIQUID, 14000));

        EMP_HEAVY = missile("emp_heavy", "Heavy EMP Missile",
                MissileModels.rl("stealth"), WFWarheads.EMP_LARGE,
                b -> b.terrainFollow(8.0).cruiseSpeed(1.5).health(30.0f).stealth()
                        .exhaustColor(0x3AA0FF)
                        .diveAngleRange(30.0, 55.0).attackStage(controlledDive())
                        .accel(0.2, 0.3).fuel(MissileEntity.FuelType.LIQUID, 14000));

        // Non-stealth EMP cluster: high airburst rains EMP bomblets over a wide area.
        EMP_CLUSTER = missile("emp_cluster", "EMP Cluster Missile",
                MissileModels.rl("micro_bhole"), WFWarheads.EMP_CLUSTER,
                b -> b.highAltitude(240.0).cruiseSpeed(2.0).health(30.0f).fragmentCount(16)
                        .explosionOffset(30.0f).exhaustColor(0x66C2FF)
                        .diveAngleRange(55.0, 75.0).attackStage(controlledDive())
                        .accel(0.3, 0.3).fuel(MissileEntity.FuelType.LIQUID, 13000));

        // EMP lance (penetrator): no terrain damage, a pinpoint 10-block ray down the dive that fries a target's
        // machinery. Fast and evasive rather than stealthy.
        EMP_LANCE = missile("emp_lance", "EMP Lance Missile",
                MissileModels.rl("micro_emp"), WFWarheads.EMP_RAY,
                b -> b.highAltitude(300.0).cruiseSpeed(4.0).health(35.0f)
                        .evasion(0.6f).evasiveManeuver().exhaustColor(0x99DBFF)
                        .diveAngleRange(84.0, 90.0).attackStage(controlledDive())
                        .accel(0.5, 0.5).fuel(MissileEntity.FuelType.LIQUID, 14000));

        // =========================== INTERCEPTORS ==========================================================
        // .interceptor(chance) flags preset().isInterceptor() (routes to the Interceptor Battery) and sets the
        // base kill roll. Speed matters twice: to run a target down (else the crossing-shot penalty), and to
        // deny its evasion (min(1, missileSpeed/interceptorSpeed) in evadeBoost).

        INTERCEPTOR = missile("interceptor", "Interceptor Missile",
                MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"),
                b -> b.highAltitude(300.0).cruiseSpeed(8.0).turnRate(0.6).health(25.0f)
                        .interceptor(0.85f).accel(1.2, 1.2)
                        .fuel(MissileEntity.FuelType.SOLID, 1000));

        INTERCEPTOR_MK2 = missile("interceptor_mk2", "Interceptor Missile Mk.II",
                MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"),
                b -> b.highAltitude(300.0).cruiseSpeed(11.0).turnRate(0.65).health(25.0f)
                        .interceptor(0.90f).accel(1.4, 1.4)
                        .fuel(MissileEntity.FuelType.SOLID, 1200));

        // Ace: fast enough to run down and out-speed nearly everything (denying evasion); competitive with all
        // but the top evasive (hypersonic) round, which can match its speed factor.
        INTERCEPTOR_ACE = missile("interceptor_ace", "Ace Interceptor Missile",
                MissileModels.rl("abm"), WarheadRegistry.rl("interceptor"),
                b -> b.highAltitude(300.0).cruiseSpeed(14.0).turnRate(0.8).health(30.0f)
                        .interceptor(0.90f).exhaustColor(0xFFE24D).accel(1.8, 1.8)
                        .fuel(MissileEntity.FuelType.SOLID, 1400));

        // Cluster interceptor: a mediocre parent that, on engaging, airbursts into 4 small homing interceptors
        // (see WFWarheads.INTERCEPT_CLUSTER) — one shot to blunt a barrage.
        INTERCEPTOR_CLUSTER = missile("interceptor_cluster", "Cluster Interceptor Missile",
                MissileModels.rl("abm"), WFWarheads.INTERCEPT_CLUSTER,
                b -> b.highAltitude(300.0).cruiseSpeed(9.0).turnRate(0.6).health(25.0f)
                        .interceptor(0.6f).accel(1.3, 1.3)
                        .fuel(MissileEntity.FuelType.SOLID, 900));

        // =========================== DRONES (cheap, separate class) ========================================
        // Wide anti-personnel blast, tiny structural damage.

        STRIKE_DRONE = missile("strike_drone", "Strike Drone",
                MissileModels.rl("shahed"), WFWarheads.DRONE,
                b -> b.highAltitude(280.0).cruiseSpeed(1.6).health(20.0f)
                        .diveAngleRange(35.0, 55.0).attackStage(controlledDive())
                        .flightSound(WFSounds.MISSILE_DRONE.getId())
                        .flightSoundRange(600.0)
                        .accel(0.15, 0.2).fuel(MissileEntity.FuelType.LIQUID, 12500));

        GAS_DRONE = missile("gas_drone", "Gas Drone",
                MissileModels.rl("shahedjarty"), WFWarheads.DRONE_GAS,
                b -> b.highAltitude(280.0).cruiseSpeed(1.5).health(20.0f)
                        .diveAngleRange(35.0, 55.0).attackStage(controlledDive())
                        .explosionOffset(1.0f).flightSound(WFSounds.MISSILE_JARTY.getId())
                        .exhaustColor(0x487800)
                        .flightSoundSpeedPitch(0.0).flightSoundRange(400.0)
                        .accel(0.15, 0.2).fuel(MissileEntity.FuelType.LIQUID, 12000));

        LOITER_DRONE = missile("loiter_drone", "Loitering Drone",
                MissileModels.rl("shahedjarty"), WarheadRegistry.rl("inert"),
                b -> b.highAltitude(200.0).cruiseSpeed(1.5).health(20.0f)
                        .cruiseStage(FlightStageRegistry.rl(LoiterUntilDryStage.ID))
                        .exhaustColor(0x487800)
                        .flightSound(WFSounds.MISSILE_JARTY.getId())
                        .flightSoundSpeedPitch(0.0).flightSoundRange(1000.0)
                        .accel(0.15, 0.2).fuel(MissileEntity.FuelType.LIQUID, 48000));
    }

    /**
     * Registers one {@code wfcore:missile_<name>} item wrapping a preset built from {@code cfg}. The preset is
     * built inside the item factory (during the item {@code RegisterEvent}, after WF-B has bootstrapped its
     * model/warhead registries, so id lookups resolve). Stacks to 16, matching WF-B.
     *
     * <p>{@code model(noop)} because the item model is hand-authored to parent WF-B's {@code missile_render_base}
     * (a {@code builtin/entity} model) so the BEWLR takes over rendering.
     *
     * <p>Every wfcore missile defaults to the {@link WFDamageResponses#HARDENED} damage response and the shared
     * {@link #DEFAULT_DOWNED} shot-down roll, both applied before {@code cfg} so a preset can override them.
     */
    private static ItemEntry<MissileItem> missile(String name, String lang, ResourceLocation airframe,
                                                  ResourceLocation warhead,
                                                  UnaryOperator<MissilePreset.Builder> cfg) {
        return WF_MACHINES
                .item("missile_" + name, props -> new MissileItem(
                        cfg.apply(MissilePreset.builder(WFCore.id("missile_" + name), airframe, warhead)
                                .damageResponse(WFDamageResponses.HARDENED_ID)
                                .flightSoundSpeedPitch(DEFAULT_MISSILE_REV) // rocket engine revs with speed; drones override to 0
                                .downedAction(DEFAULT_DOWNED)).build(),
                        props))
                .lang(lang)
                .properties(p -> p.stacksTo(16))
                .model(NonNullBiConsumer.noop())
                .register();
    }
}
