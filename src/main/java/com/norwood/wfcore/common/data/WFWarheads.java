package com.norwood.wfcore.common.data;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.sound.WFDelayedSounds;
import com.norwood.wfcore.common.sound.WFSounds;
import com.norwood.wfcore.common.warhead.BlockAllocatorEMPRay;
import com.norwood.wfcore.common.warhead.BlockProcessorPulverize;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.MissileModels;
import com.wf.wfballistics.ModEntities;
import com.wf.wfballistics.aef.ExplosionAEF;
import com.wf.wfballistics.aef.nuke.MiniNuke;
import com.wf.wfballistics.aef.nuke.MiniNuke.MukeParams;
import com.wf.wfballistics.aef.standard.*;
import com.wf.wfballistics.client.fx.ClientSoundScheduler;
import com.wf.wfballistics.entity.BombletEntity;
import com.wf.wfballistics.entity.mist.GasCloud;
import com.wf.wfballistics.flight.FlightStageRegistry;
import com.wf.wfballistics.fluid.WFFluids;
import com.wf.wfballistics.fx.EMPCreator;
import com.wf.wfballistics.fx.ExplosionCreator;
import com.wf.wfballistics.fx.ExplosionSmallCreator;
import com.wf.wfballistics.swarm.SwarmManager;
import com.wf.wfballistics.util.FragmentationUtil;
import com.wf.wfballistics.warhead.BombletWarhead;
import com.wf.wfballistics.warhead.WarheadRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import static com.gregtechceu.gtceu.api.data.chemical.material.info.MaterialIconType.seed;

/**
 * WFCore's custom missile warheads, registered into WF-Ballistics' {@link WarheadRegistry} so the
 * {@link WFMissiles} presets can reference them by id. Each is a self-contained detonation assembled from WF-B's
 * public Advanced Explosion Framework strategies ({@link ExplosionAEF}) — or, for the cluster/skyfall/interceptor
 * warheads, by spawning child {@link MissileEntity}s directly — so the ids resolve at detonation time.
 *
 * <p>These cover the pack's missile <b>families</b> (all HV-and-up, since the Missile Factory unlocks at HV):
 * <ul>
 * <li><b>Demolition</b> — {@link #HE}, {@link #LONG_RANGE}, {@link #THERMOBARIC} (+ the built-in {@code mininuke}):
 *     big destructive ray-march craters, slow and interceptable. A plain sphere can't drain through tungsten
 *     plating (res 130) no matter how large, so demolition/penetrator rounds never defeat top-tier armour.</li>
 * <li><b>Bunker buster</b> — {@link #BUNKER_MK1}/{@link #BUNKER_MK2}: a narrow shaped-charge jet that
 *     <em>breaks</em> what it can and <em>cracks</em> the rest to gravel ({@link BlockProcessorPulverize}) so a
 *     follow-up round finishes hardened defences.</li>
 * <li><b>Frag / cluster</b> — {@link #GAS_SWISS} (scattered short gas pools) and {@link #SKYFALL} (bursts high,
 *     rains entity-seeking submunitions); plus the built-in {@code fragmentation}/{@code fire_cluster}. Anti-entity,
 *     minimal terrain damage.</li>
 * <li><b>EMP</b> — {@link #EMP_SMALL}/{@link #EMP_LARGE} (area disable), {@link #EMP_CLUSTER} (bomblet EMP over a
 *     wide area), {@link #EMP_RAY} (pinpoint lance, no terrain damage).</li>
 * <li><b>Interceptor</b> — {@link #INTERCEPT_CLUSTER}: on engaging, airbursts into a handful of small interceptors
 *     to soak a barrage.</li>
 * <li><b>Drones</b> (a separate, deliberately cheap class) keep {@link #DRONE}/{@link #DRONE_GAS}.</li>
 * </ul>
 *
 * <p><b>Ordering:</b> {@link #register()} must run before any preset that references these ids is built. It's
 * called at the top of {@link WFMissiles#init()} (mod construction); the presets build lazily in the item
 * factory, long after. An unregistered id silently falls back to WF-B's {@code standard} warhead.
 */
public final class WFWarheads {

    public static final ResourceLocation HE = WFCore.id("he");
    public static final ResourceLocation LONG_RANGE = WFCore.id("long_range");
    public static final ResourceLocation SHAPED = WFCore.id("shaped");
    public static final ResourceLocation DRONE = WFCore.id("drone");
    public static final ResourceLocation DRONE_GAS = WFCore.id("drone_gas");

    // --- Families -----------------------------------------------------------------------------------------
    public static final ResourceLocation THERMOBARIC = WFCore.id("thermobaric");
    public static final ResourceLocation MININUKE = WFCore.id("mininuke");
    public static final ResourceLocation ICBM = WFCore.id("icbm");
    public static final ResourceLocation ICBM_HEAVY = WFCore.id("icbm_heavy");
    public static final ResourceLocation BUNKER_MK1 = WFCore.id("bunker_mk1");
    public static final ResourceLocation BUNKER_MK2 = WFCore.id("bunker_mk2");
    public static final ResourceLocation TUNNELLER = WFCore.id("tunneller");
    public static final ResourceLocation GAS_SWISS = WFCore.id("gas_swiss");
    public static final ResourceLocation FIRE_CLUSTER = WFCore.id("fire_cluster");
    public static final ResourceLocation FRAG_STORM = WFCore.id("frag_storm");
    public static final ResourceLocation FRAG_STORM_CHILD = WFCore.id("frag_storm_child");
    public static final ResourceLocation FRAG_LOW = WFCore.id("frag_low");
    public static final ResourceLocation EMP_SMALL = WFCore.id("emp_small");
    public static final ResourceLocation EMP_LARGE = WFCore.id("emp_large");
    public static final ResourceLocation EMP_BOMBLET = WFCore.id("emp_bomblet");
    public static final ResourceLocation EMP_CLUSTER = WFCore.id("emp_cluster");
    public static final ResourceLocation EMP_RAY = WFCore.id("emp_ray");
    public static final ResourceLocation SKYFALL = WFCore.id("skyfall");
    public static final ResourceLocation INTERCEPT_CLUSTER = WFCore.id("intercept_cluster");

    // Block-destruction blast sizes (vanilla-style ray-march power; larger = wider crater + harder blocks).
    private static final float HE_SIZE = 20.0f;
    private static final float LONG_RANGE_SIZE = 11.0f;
    private static final float DRONE_SIZE = 4.0f;
    private static final float THERMOBARIC_SIZE = 32.0f;
    // Multiplies only the entity blast radius/damage, leaving block destruction at the base size.
    private static final float DRONE_ENTITY_RANGE_MOD = 4.0f;
    private static final float THERMOBARIC_ENTITY_RANGE_MOD = 3.0f;

    // Skyfall submunitions.
    private static final int SKYFALL_SUBMUNITIONS = 9;
    private static final double SKYFALL_SCAN_RANGE = 100.0;
    // Limited lifespan: each seeker targets a SNAPSHOT of its entity's position, so a flying/moving target can be
    // missed. Rather than loiter over empty air, a short tank runs dry in ~6 s — the seeker then falls and pops on
    // the ground below instead of orbiting forever.
    private static final int SKYFALL_SUBMUNITION_FUEL = 120;

    // Fragmentation storm cascade: 9 child missiles, each throwing 4 low-yield bomblets, scattered wide.
    private static final int FRAG_STORM_CHILDREN = 8;
    private static final int FRAG_STORM_CHILD_FRAGS =8;
    private static final double FRAG_STORM_SPREAD = 40.0;   // radius the child missiles scatter over
    private static final float FRAG_LOW_SIZE = 3.0f;        // one bomblet's blast: clears wood/leaves, weak vs cover

    // One low-yield fragmentation pop: a tiny drop-free blast that breaks wood/leaves and chips entities, but a
    // size-3 ray-march barely scratches stone/concrete — the "weak explosive" the storm saturates an area with.
    private static final WarheadRegistry.Detonation FRAG_LOW_DET = (source, pos) -> {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        ExplosionAEF pop = new ExplosionAEF(level, pos.x, pos.y, pos.z, FRAG_LOW_SIZE);
        pop.setBlockAllocator(new BlockAllocatorStandard(12));
        pop.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        pop.setEntityProcessor(new EntityProcessorCross());
        pop.setPlayerProcessor(new PlayerProcessorStandard());
        pop.explode();
        ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 4, 1, 1);
    };

    // Configurable EMP effect: exactly what a strike does to each machine it hits. Mix and match the four levers
    // (charge-lock seconds / drain stored energy / pause "mallet" the machine / break its maintenance) per warhead.
    private record EmpEffect(int chargeLockSeconds, boolean drainEnergy, boolean pauseWork, boolean breakMaintenance) {}

    // Full-spectrum disable for the area/cluster EMPs: drain energy, pause work, break maintenance, 10s charge lock.
    private static final EmpEffect EMP_FULL = new EmpEffect(90, true, true, true);
    // The lance ONLY pauses ("mallets") the machine — no energy drain, no maintenance break, no charge lock.
    private static final EmpEffect EMP_STANDARD = new EmpEffect(30, true, false, false);
    private static final EmpEffect EMP_LONG = new EmpEffect(60, true, false, true);

    // A small EMP burst carried by each emp-cluster bomblet (registered so it survives bomblet save/load).
    private static final WarheadRegistry.Detonation EMP_BOMBLET_DET = (source, pos) -> empBlast(source.level(), pos, 10, EMP_STANDARD);

    // EMP lance ray length (blocks) — shared by the block-entity allocator and the visible beam.
    private static final double EMP_RAY_LENGTH = 10.0;

    private WFWarheads() {}

    public static void register() {
        // --- Demolition -----------------------------------------------------------------------------------
        // Both cruise-missile warheads are a plain destructive blast, differing only in size (HE vs. lighter).
        WarheadRegistry.register(HE, (source, pos) ->
                blast(source.level(), pos, HE_SIZE, 32, new EntityProcessorCross()),
                WarheadRegistry.STANDARD_INTERCEPT);

        WarheadRegistry.register(LONG_RANGE, (source, pos) ->
                blast(source.level(), pos, LONG_RANGE_SIZE, 32, new EntityProcessorCross()),
                WarheadRegistry.STANDARD_INTERCEPT);

        // Thermobaric: a moderate crater but a very wide, hard-hitting entity blast (fuel-air overpressure).
        WarheadRegistry.register(THERMOBARIC, (source, pos) ->
                blast(source.level(), pos, THERMOBARIC_SIZE, 24,
                        new EntityProcessorCross().withRangeMod(THERMOBARIC_ENTITY_RANGE_MOD)),
                WarheadRegistry.STANDARD_INTERCEPT);

        // Mini-nuke (custom): WF-B's built-in mininuke is a surface-cratering sphere with poor penetration
        // against hardened armour. Keep its (good) area effect — crater, fire, ~55-block kill radius, mushroom —
        // but fire a heavy shaped jet down the impact heading FIRST so it drives deep through fortification into
        // the structure, defeating bunkers instead of just scorching their roof.
        WarheadRegistry.register(MININUKE, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            ExplosionAEF jet = new ExplosionAEF(level, pos.x, pos.y, pos.z, 60);
            jet.setBlockAllocator(new BlockAllocatorShapedCharge(source.angle(), 18.0f, 20.0f));
            jet.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
            jet.setPlayerProcessor(new PlayerProcessorStandard());
            jet.explode();
            var muke = new MukeParams();
            muke.blastRadius = 80;
            muke.killRadius = 100;
            muke.fire = false;
            muke.resolution = 128;
            muke.miniNuke = true;
            MiniNuke.detonate(level,
                    pos,
                    muke);
        });

        // ICBM warheads: a compact, forceful top-attack punch — a narrow charge (low blast radius) with a lethal
        // spherical entity kill at the impact point. Its jet power is deliberately LOW (4/6), well under even the
        // entry bunker buster (MK1 = 8), so it does NOT dig deep or defeat hardened armour — that's the bunker
        // family's job. The ICBM is a precise concentrated surface strike, not a penetrator.
        WarheadRegistry.register(ICBM, (source, pos) ->
                icbmStrike(source.level(), pos, source.angle(), 8.0f, 4.0f),
                WarheadRegistry.STANDARD_INTERCEPT);
        WarheadRegistry.register(ICBM_HEAVY, (source, pos) ->
                icbmStrike(source.level(), pos, source.angle(), 10.0f, 6.0f),
                WarheadRegistry.STANDARD_INTERCEPT);

        // --- Drones (cheap, separate class) ---------------------------------------------------------------
        WarheadRegistry.register(DRONE, (source, pos) ->
                blast(source.level(), pos, DRONE_SIZE, 16,
                        new EntityProcessorCross().withRangeMod(DRONE_ENTITY_RANGE_MOD)),
                WarheadRegistry.STANDARD_INTERCEPT);

        WarheadRegistry.register(DRONE_GAS, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            RandomSource rng = level.random;
            for (int i = 0; i < 4; i++) {
                GasCloud.spawn(level, WFFluids.MUSTARD_GAS.get(),
                        pos.add(rng.nextInt(15) - 7, 0, rng.nextInt(15) - 7), 6, 8192, 260);
            }
            ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 10, 2, 2);
        });

        // --- Bunker buster --------------------------------------------------------------------------------
        // Narrow shaped jet down the impact heading. Mk1 breaks concrete-class blocks and cracks everything
        // tougher (steel/tungsten) to gravel; Mk2 hits harder + drives its crack threshold up so it breaks the
        // steel and only cracks tungsten. Neither finishes tungsten alone — that's the "needs follow-up" role.
        WarheadRegistry.register(BUNKER_MK1, (source, pos) ->
                bunker(source.level(), pos, source.angle(), 10.0f, 8.0f, 18.0f, 30.0f),
                WarheadRegistry.STANDARD_INTERCEPT);
        WarheadRegistry.register(BUNKER_MK2, (source, pos) ->
                bunker(source.level(), pos, source.angle(), 12.0f, 14.0f, 16.0f, 100.0f),
                WarheadRegistry.STANDARD_INTERCEPT);

        // Tunneller (final-tier bunker buster): instead of a surface shaped-charge, it BORES a shaft up to
        // TUNNEL_MAX_DEPTH blocks down the impact heading — stopped early when it meets heavy shielding
        // (tungsten-class) — then detonates a secondary blast INSIDE. That secondary only breaks blocks about as
        // well as a first-tier HE round, so it clears and kills the interior without widening the breach: get
        // in, then boom (and follow up to actually level the structure).
        WarheadRegistry.register(TUNNELLER, WFWarheads::tunneller, WarheadRegistry.STANDARD_INTERCEPT);

        // The legacy "shaped" warhead (destroys + scatters gravel debris) — kept for compatibility.
        WarheadRegistry.register(SHAPED, (source, pos) -> {
            ExplosionAEF blast = new ExplosionAEF(source.level(), pos.x, pos.y, pos.z, 10);
            blast.setBlockAllocator(new BlockAllocatorShapedCharge(source.angle(), 15, 10));
            blast.setBlockProcessor(new BlockProcessorStandard().withBlockEffect(new BlockMutatorDebris(Blocks.GRAVEL)));
            blast.setEntityProcessor(new EntityProcessorCone(source.angle(), 20));
            blast.setPlayerProcessor(new PlayerProcessorStandard());
            blast.explode();
            ExplosionCreator.composeEffectLarge(source.level(), pos.x, pos.y, pos.z);
        }, WarheadRegistry.STANDARD_INTERCEPT);

        // --- Frag / cluster -------------------------------------------------------------------------------
        // Gas "swiss cheese": many small, short-lived mustard pools scattered wide, denying an area in patches
        // rather than one solid cloud. Minimal terrain damage.
        WarheadRegistry.register(GAS_SWISS, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            RandomSource rng = level.random;
            for (int i = 0; i < 16; i++) {
                // Tighter spread (±8, was ±14) so the pools overlap into a denser patch instead of sprawling.
                Vec3 c = pos.add(rng.nextInt(17) - 8, 0, rng.nextInt(17) - 8);
                GasCloud.spawn(level, WFFluids.MUSTARD_GAS.get(), c, 3, 2048, 820);
            }
            ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 12, 2, 2);
        });

        // Skyfall: bursts high (via a large explosionOffset on the preset) and rains a spread of small
        // submunitions, each seeking a distinct entity within SKYFALL_SCAN_RANGE (falling back to scattered
        // ground points when there aren't enough targets).
        WarheadRegistry.register(SKYFALL, WFWarheads::skyfall);

        // Fire cluster (tighter than WF-B's 60-degree fire_cluster): a narrow 35-degree cone of incendiary
        // bomblets, so the fire lands in a concentrated patch instead of a wide sprawl.
        WarheadRegistry.register(FIRE_CLUSTER, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            FragmentationUtil.cone(level, pos, new Vec3(0.0, -1.0, 0.0), Math.toRadians(35.0),
                    Math.max(6, source.getFragmentCount()), 1.0, 0.2,
                    BombletWarhead.FIRE_ID, BombletWarhead.FIRE, BombletEntity.DEFAULT_FUSE, null);
            ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 8, 2, 2);
        });

        // Fragmentation storm: the parent bursts high into FRAG_STORM_CHILDREN small frag missiles scattered
        // over a wide area; each of those, on airburst, throws FRAG_STORM_CHILD_FRAGS low-yield bomblets. Net:
        // dense saturation of a large footprint with weak explosions — great at clearing wood/leaves and
        // chipping infantry, but they barely scratch real cover.
        WarheadRegistry.register(FRAG_LOW, FRAG_LOW_DET);
        WarheadRegistry.register(FRAG_STORM_CHILD, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            FragmentationUtil.burst(level, pos, FRAG_STORM_CHILD_FRAGS, 0.6, 0.2, FRAG_LOW, FRAG_LOW_DET, BombletEntity.DEFAULT_FUSE, null);
            ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 5, 1, 1);
        });
        WarheadRegistry.register(FRAG_STORM, WFWarheads::fragStorm);

        // --- EMP ------------------------------------------------------------------------------------------
        WarheadRegistry.register(EMP_SMALL, (source, pos) -> empBlast(source.level(), pos, 16, EMP_STANDARD),
                WarheadRegistry.STANDARD_INTERCEPT);
        WarheadRegistry.register(EMP_LARGE, (source, pos) -> empBlast(source.level(), pos, 40, EMP_LONG),
                WarheadRegistry.STANDARD_INTERCEPT);

        // The small EMP each cluster bomblet carries (referenced by the cone below, registered for save/load).
        WarheadRegistry.register(EMP_BOMBLET, EMP_BOMBLET_DET);

        // Non-stealth EMP cluster: a downward cone of bomblets that each pop a small EMP, blanketing a wide area.
        WarheadRegistry.register(EMP_CLUSTER, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            FragmentationUtil.cone(level, pos, new Vec3(0.0, -1.0, 0.0), Math.toRadians(60.0),
                    Math.max(6, source.getFragmentCount()), 1.4, 0.4,
                    EMP_BOMBLET, EMP_BOMBLET_DET, BombletEntity.DEFAULT_FUSE, null);
            EMPCreator.composeStun(level, pos.x, pos.y, pos.z);
        });

        // Pinpoint EMP lance: a narrow 2x2 ray down the impact heading disables machines it threads through — no
        // terrain damage, no area effect. The penetrator-EMP. A bright laser beam marks the strike path so it's
        // easy to see exactly what it hit.
        WarheadRegistry.register(EMP_RAY, (source, pos) -> {
            Level level = source.level();
            if (level.isClientSide) {
                return;
            }
            Vec3 axis = source.angle();
            ExplosionAEF emp = new ExplosionAEF(level, pos.x, pos.y, pos.z, 20);
            emp.setBlockAllocator(new BlockAllocatorEMPRay(axis, EMP_RAY_LENGTH));
            emp.setBlockProcessor(empProcessor(EMP_FULL)); // lance only pauses/mallets, nothing else
            emp.bypassClaims(true);
            emp.explode();
            EMPCreator.compose(level, pos.x, pos.y, pos.z, 4);
            empRayBeam(level, pos, axis, EMP_RAY_LENGTH);
            playEMP(pos,level);
        }, WarheadRegistry.STANDARD_INTERCEPT);

        // --- Interceptor ----------------------------------------------------------------------------------
        // Cluster interceptor: when the parent engages (its intercept effect fires), it airbursts into a handful
        // of small, fast child interceptors that each home on the nearest remaining missile — one shot to blunt
        // a barrage. Children carry the plain "interceptor" warhead so they don't re-cluster.
        WarheadRegistry.register(INTERCEPT_CLUSTER, (source, pos) -> {}, WFWarheads::interceptCluster);
    }

    // ------------------------------------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------------------------------------

   private static void playEMP(Vec3 pos, Level level){
       var rand = level.random;
        for (ServerPlayer player : ((ServerLevel)level).players()) {
            double dist = Math.sqrt(player.distanceToSqr(pos));
            if (dist > 256f) {
                continue;
            }
            WFDelayedSounds.schedule((ServerLevel) level, (int)(dist/ClientSoundScheduler.SPEED_OF_SOUND), player, pos.x, pos.y, pos.z,
                    WFSounds.MISSILE_EMP.get(), SoundSource.BLOCKS, 1.0f, rand.nextFloat()+0.4f, 0L);
        }
   }

    /**
     * Runs one AEF blast: ray-march terrain destruction at {@code size} (no item drops), entity damage via the
     * supplied processor, then the large explosion SFX. Server-side only.
     */
    private static void blast(Level level, Vec3 pos, float size, int allocatorResolution,
                              EntityProcessorCross entityProcessor) {
        if (level.isClientSide) {
            return;
        }
        ExplosionAEF blast = new ExplosionAEF(level, pos.x, pos.y, pos.z, size);
        blast.setBlockAllocator(new BlockAllocatorStandard(allocatorResolution));
        blast.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        blast.setEntityProcessor(entityProcessor);
        blast.setPlayerProcessor(new PlayerProcessorStandard());
        blast.explode();
        ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
    }

    /** ICBM strike: a narrow charge (low blast radius) with a shallow jet + a lethal spherical entity kill. */
    private static void icbmStrike(Level level, Vec3 pos, Vec3 axis, float size, float jetPower) {
        if (level.isClientSide) {
            return;
        }
        ExplosionAEF blast = new ExplosionAEF(level, pos.x, pos.y, pos.z, size);
        blast.setBlockAllocator(new BlockAllocatorShapedCharge(axis, 18.0f, jetPower));
        blast.setBlockProcessor(new BlockProcessorStandard().setNoDrop());
        blast.setEntityProcessor(new EntityProcessorCross());
        blast.setPlayerProcessor(new PlayerProcessorStandard());
        blast.explode();
        ExplosionCreator.composeEffectStandard(level, pos.x, pos.y, pos.z);
    }

    /**
     * A shaped-charge bunker-buster blast: a jet down {@code axis} that breaks soft blocks and cracks anything at
     * or above {@code crackThreshold} to gravel (see {@link BlockProcessorPulverize}). Server-side only.
     */
    private static void bunker(Level level, Vec3 pos, Vec3 axis, float size, float jetPower, float halfAngleDeg,
                               float crackThreshold) {
        if (level.isClientSide) {
            return;
        }
        ExplosionAEF blast = new ExplosionAEF(level, pos.x, pos.y, pos.z, size);
        blast.setBlockAllocator(new BlockAllocatorShapedCharge(axis, halfAngleDeg, jetPower));
        blast.setBlockProcessor(new BlockProcessorPulverize(crackThreshold));
        blast.setEntityProcessor(new EntityProcessorCone(axis, halfAngleDeg));
        blast.setPlayerProcessor(new PlayerProcessorStandard());
        blast.explode();
        ExplosionCreator.composeEffectLarge(level, pos.x, pos.y, pos.z);
    }

    // Tunneller bore tunables.
    private static final int TUNNEL_MAX_DEPTH = 15;          // max blocks the shaft reaches through soft ground
    private static final double TUNNEL_DIG_POWER = 300.0;    // budget drained by each gate block's resistance
    private static final double TUNNEL_RADIUS = 1.3;         // shaft half-width (a ~3-block-wide bore)
    private static final float TUNNEL_WALL_CAP = 120.0f;     // resistance the bore can't defeat (tungsten-class stops it)
    private static final float TUNNELLER_SECONDARY_SIZE = 18.0f; // interior blast — ~first-tier HE block-breaking

    /**
     * Bore a shaft down {@code source.angle()} up to {@link #TUNNEL_MAX_DEPTH} blocks, stopping early when the
     * dig budget runs out or it meets a tungsten-class block, then detonate a modest secondary blast at the end.
     */
    private static void tunneller(com.wf.wfballistics.warhead.WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        Vec3 axis = source.angle();
        double power = TUNNEL_DIG_POWER;
        Vec3 end = pos;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int depth = 1; depth <= TUNNEL_MAX_DEPTH; depth++) {
            Vec3 center = pos.add(axis.scale(depth));
            cursor.set(Mth.floor(center.x), Mth.floor(center.y), Mth.floor(center.z));
            BlockState gate = level.getBlockState(cursor);
            if (!gate.isAir()) {
                float res = gate.getBlock().getExplosionResistance();
                if (res >= TUNNEL_WALL_CAP || power < res) {
                    break; // heavy shielding (or budget spent): stop here, leaving the shield intact
                }
                power -= res + 1.0;
            }
            breakShaft(level, center);
            end = center;
        }
        ExplosionSmallCreator.composeEffect(level, pos.x, pos.y, pos.z, 8, 2, 2);
        // Secondary blast inside — weak at block-breaking (~first-tier HE), but kills/clears the interior.
        blast(level, end, TUNNELLER_SECONDARY_SIZE, 24, new EntityProcessorCross());
    }

    /** Clears a small sphere ({@link #TUNNEL_RADIUS}) of borable blocks — the shaft wall; tungsten-class survives. */
    private static void breakShaft(Level level, Vec3 center) {
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        int r = (int) Math.ceil(TUNNEL_RADIUS);
        int cx = Mth.floor(center.x), cy = Mth.floor(center.y), cz = Mth.floor(center.z);
        double r2 = TUNNEL_RADIUS * TUNNEL_RADIUS;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dy * dy + dz * dz > r2) {
                        continue;
                    }
                    c.set(cx + dx, cy + dy, cz + dz);
                    BlockState s = level.getBlockState(c);
                    if (!s.isAir() && s.getBlock().getExplosionResistance() < TUNNEL_WALL_CAP) {
                        level.destroyBlock(c, false);
                    }
                }
            }
        }
    }

    /**
     * A bright, laser-shaped beam along the EMP lance's ray so the strike path is unmistakable. Runs from a couple
     * blocks above the impact (the incoming lance) down through the 2x2 penetration column: an {@code END_ROD}
     * core for a solid glowing line, flanked by {@code ELECTRIC_SPARK} at the 2x2 corners to read at the effect's
     * width and sell the EMP.
     */
    private static void empRayBeam(Level level, Vec3 pos, Vec3 axis, double length) {
        if (!(level instanceof ServerLevel sl)) {
            return;
        }
        Vec3 up = Math.abs(axis.y) < 0.999 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = up.cross(axis).normalize();
        Vec3 v = axis.cross(u);
        double[] cross = {-0.5, 0.5};
        for (double t = -2.0; t <= length; t += 0.4) {
            Vec3 c = pos.add(axis.scale(t));
            sl.sendParticles(ParticleTypes.END_ROD, c.x, c.y, c.z, 1, 0.0, 0.0, 0.0, 0.0);
            for (double ou : cross) {
                for (double ov : cross) {
                    Vec3 p = c.add(u.scale(ou)).add(v.scale(ov));
                    sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
                }
            }
        }
    }

    /** Area EMP of the given block radius, applying {@code eff}, reusing WF-B's block-entity allocator. */
    private static void empBlast(Level level, Vec3 pos, int radius, EmpEffect eff) {
        if (level.isClientSide) {
            return;
        }
        ExplosionAEF emp = new ExplosionAEF(level, pos.x, pos.y, pos.z, radius);
        emp.setBlockAllocator(new BlockAllocatorBlockEntities(radius));
        emp.setBlockProcessor(empProcessor(eff));
        emp.bypassClaims(true);
        emp.explode();
        EMPCreator.compose(level, pos.x, pos.y, pos.z, radius);
        playEMP(pos,level);
    }

    /** Builds WF-B's EMP block processor from a wfcore {@link EmpEffect} profile. */
    private static BlockProcessorEMP empProcessor(EmpEffect eff) {
        return new BlockProcessorEMP(eff.chargeLockSeconds(), eff.drainEnergy(), eff.pauseWork(), eff.breakMaintenance());
    }

    /** Skyfall: spawn a spread of entity-seeking submunitions around the burst point. */
    private static void skyfall(com.wf.wfballistics.warhead.WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        RandomSource rng = server.random;
        AABB scan = new AABB(pos.x - SKYFALL_SCAN_RANGE, pos.y - SKYFALL_SCAN_RANGE, pos.z - SKYFALL_SCAN_RANGE,
                pos.x + SKYFALL_SCAN_RANGE, pos.y + SKYFALL_SCAN_RANGE, pos.z + SKYFALL_SCAN_RANGE);
        List<LivingEntity> targets = server.getEntitiesOfClass(LivingEntity.class, scan,
                e -> e.isAlive() && !e.isSpectator());
        targets.sort(Comparator.comparingDouble(e -> e.distanceToSqr(pos)));

        long swarm = SwarmManager.newId(server);
        UUID control = source instanceof MissileEntity m ? m.getControlId() : null;

        for (int i = 0; i < SKYFALL_SUBMUNITIONS; i++) {
            LivingEntity seek = i < targets.size() ? targets.get(i) : null;
            Vec3 target;
            if (seek != null) {
                target = seek.position();
            } else {
                double ang = rng.nextDouble() * Math.PI * 2.0;
                double r = 30.0 * rng.nextDouble();
                double tx = pos.x + Math.cos(ang) * r;
                double tz = pos.z + Math.sin(ang) * r;
                int gy = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tx), Mth.floor(tz));
                target = new Vec3(tx, gy, tz);
            }
            MissileEntity.Builder b = MissileEntity.builder(ModEntities.STEALTH_MISSILE.get(), server)
                    .model(MissileModels.rl("cluster_part"))
                    .target(target)
                    .detonation(WarheadRegistry.rl("bomblet"))
                    .swarmId(swarm)
                    .startInAttack()
                    .cruiseSpeed(3.0)
                    .terrainFollow(6.0)
                    .health(8.0f)
                    .fuel(MissileEntity.FuelType.SOLID, SKYFALL_SUBMUNITION_FUEL);
            // Live-track the assigned entity: MissileEntity re-aims a non-interceptor at its designated target's
            // CURRENT position each tick, so a moving/flying target is followed, not just snapshotted. If it dies
            // or escapes, the target stops updating and the short fuel tank times the seeker out (falls + pops).
            if (seek != null) {
                b.designatedTarget(seek.getUUID());
            }
            if (control != null) {
                b.controlId(control);
            }
            MissileEntity child = b.build();
            child.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
            Vec3 dir = target.subtract(pos);
            if (dir.lengthSqr() > 1.0e-6) {
                child.setDeltaMovement(dir.normalize().scale(2.0));
            }
            server.addFreshEntity(child);
        }
        ExplosionCreator.composeEffectSmall(server, pos.x, pos.y, pos.z);
    }

    /** Fragmentation storm: scatter FRAG_STORM_CHILDREN small frag missiles over a wide area around the burst. */
    private static void fragStorm(com.wf.wfballistics.warhead.WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        RandomSource rng = server.random;
        long swarm = SwarmManager.newId(server);
        UUID control = source instanceof MissileEntity m ? m.getControlId() : null;
        double base = rng.nextDouble() * Math.PI * 2.0;
        double stepA = Math.PI * 2.0 / FRAG_STORM_CHILDREN;
        for (int i = 0; i < FRAG_STORM_CHILDREN; i++) {
            double ang = base + i * stepA + (rng.nextDouble() - 0.5) * stepA * 0.5;
            double r = FRAG_STORM_SPREAD * (0.35 + 0.65 * rng.nextDouble());
            double tx = pos.x + Math.cos(ang) * r;
            double tz = pos.z + Math.sin(ang) * r;
            int gy = server.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(tx), Mth.floor(tz));
            Vec3 target = new Vec3(tx, gy, tz);
            MissileEntity.Builder b = MissileEntity.builder(ModEntities.STEALTH_MISSILE.get(), server)
                    .model(MissileModels.rl("cluster_part"))
                    .target(target)
                    .detonation(FRAG_STORM_CHILD)
                    .swarmId(swarm)
                    .startInAttack()
                    .attackStage(FlightStageRegistry.rl(ControlledDiveStage.ID))
                    .explosionOffset(10.0f)     // airburst so the 4 bomblets spread before hitting the ground
                    .cruiseSpeed(2.0)
                    .terrainFollow(6.0)
                    .health(6.0f)
                    .fuel(MissileEntity.FuelType.SOLID, 800);
            if (control != null) {
                b.controlId(control);
            }
            MissileEntity child = b.build();
            child.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
            Vec3 dir = target.subtract(pos);
            if (dir.lengthSqr() > 1.0e-6) {
                child.setDeltaMovement(dir.normalize().scale(1.2));
            }
            server.addFreshEntity(child);
        }
        ExplosionCreator.composeEffectSmall(server, pos.x, pos.y, pos.z);
    }

    /** Cluster interceptor: airburst into several small homing child interceptors. */
    private static void interceptCluster(com.wf.wfballistics.warhead.WarheadCarrier source, Vec3 pos) {
        Level level = source.level();
        if (level.isClientSide) {
            return;
        }
        long swarm = SwarmManager.newId(level);
        UUID control = source instanceof MissileEntity m ? m.getControlId() : null;
        for (int i = 0; i < 9; i++) {
            MissileEntity.Builder b = MissileEntity.builder(ModEntities.STEALTH_MISSILE.get(), level)
                    .model(MissileModels.rl("abm"))
                    .target(pos)
                    .interceptor(true)
                    .interceptMode(MissileEntity.InterceptMode.NEAREST)
                    .interceptChance(0.75f)
                    .swarmId(swarm)
                    .startInCruise()
                    .cruiseSpeed(9.0)
                    .turnRate(0.7)
                    .health(8.0f)
                    .fuel(MissileEntity.FuelType.SOLID, 400);
            if (control != null) {
                b.controlId(control);
            }
            MissileEntity child = b.build();
            child.moveTo(pos.x, pos.y, pos.z, 0.0f, 0.0f);
            level.addFreshEntity(child);
        }
        ExplosionCreator.composeEffectSmall(level, pos.x, pos.y, pos.z);
    }
}
