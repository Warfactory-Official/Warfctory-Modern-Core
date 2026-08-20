package com.norwood.wfcore.common.data;

import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.flight.FlightContext;
import com.wf.wfballistics.flight.FlightStage;
import com.wf.wfballistics.flight.FlightStageRegistry;
import net.minecraft.world.phys.Vec3;


/**
 * Angled pure-pursuit terminal dive at a <em>commanded</em> descent speed, so a missile keeps its steep attack
 * angle while coming down at a controlled, non-accelerating rate instead of plunging like WF-B's
 * {@code VerticalDiveStage} (~18 b/t), which overshoots the target.
 *
 * <p>Two registered variants share the algorithm and differ only in commanded speed:
 * <ul>
 *   <li>{@link #INSTANCE} ({@link #ID}) — the readable {@value #DEFAULT_APPROACH}-b/t dive for the general suite.</li>
 *   <li>{@link #ICBM} ({@link #ICBM_ID}) — a fast {@value #ICBM_APPROACH}-b/t descent for the ICBM class: quick
 *       enough that a lower-tier interceptor can't run it down (it's reduced to unreliable crossing shots), yet
 *       still converges because it's the same pure-pursuit-onto-the-dive-line logic, not a blind plunge.</li>
 * </ul>
 * The commanded speed is a floor of {@code max(cruiseSpeed, approachSpeed)}, so a fast missile never dives
 * <em>slower</em> than it cruises.
 */
public final class ControlledDiveStage implements FlightStage {

    /** Stage ids. WF-B namespaces its own ({@code wfballistics:...}); the {@code wfcore_} prefix keeps these ours. */
    public static final String ID = "wfcore_controlled_dive";
    public static final String ICBM_ID = "wfcore_icbm_dive";
    public static final String MEDIUM_ID = "wfcore_medium_dive";
    public static final String WEAK_ID = "wfcore_weak_dive";

    private static final double DEFAULT_APPROACH = 5.0;
    private static final double ICBM_APPROACH = 12.0;
    private static final double MEDIUM_APPROACH = 10.0;
    private static final double WEAK_APPROACH = 8.0;

    public static final ControlledDiveStage INSTANCE = new ControlledDiveStage(ID, DEFAULT_APPROACH);
    public static final ControlledDiveStage ICBM = new ControlledDiveStage(ICBM_ID, ICBM_APPROACH);
    public static final ControlledDiveStage MEDIUM = new ControlledDiveStage(MEDIUM_ID, MEDIUM_APPROACH);
    public static final ControlledDiveStage WEAK = new ControlledDiveStage(WEAK_ID, WEAK_APPROACH);

    // Carrot distance ahead along the approach line for the angled pure-pursuit run (matches AttackStage).
    private static final double LOOKAHEAD = 12.0;
    // 3-D distance to the aim point at which the run commits to flying straight ahead.
    private static final double COMMIT_RADIUS = 2.0;

    private final String id;
    private final double approachSpeed;

    private ControlledDiveStage(String id, double approachSpeed) {
        this.id = id;
        this.approachSpeed = approachSpeed;
    }

    /** Register both dive variants for the ATTACK phase. Call once at mod construction, before any preset uses them. */
    public static void register() {
        FlightStageRegistry.register(MissileEntity.Phase.ATTACK, INSTANCE);
        FlightStageRegistry.register(MissileEntity.Phase.ATTACK, ICBM);
        FlightStageRegistry.register(MissileEntity.Phase.ATTACK, MEDIUM);
        FlightStageRegistry.register(MissileEntity.Phase.ATTACK, WEAK);
    }

    @Override
    public Vec3 guide(MissileEntity missile, FlightContext ctx) {
        if (missile.isDiveCommitted()) {
            return holdHeading(missile, ctx);
        }
        if (reachedAimDescending(missile, ctx)) {
            missile.setDiveCommitted(true);
            return holdHeading(missile, ctx);
        }
        return guideAngled(missile, ctx, missile.resolveDiveAngle(ctx));
    }

    /** @return true once the missile is within {@link #COMMIT_RADIUS} of its aim point on a descending pass. */
    private static boolean reachedAimDescending(MissileEntity missile, FlightContext ctx) {
        if (missile.getDeltaMovement().y > 0.0) {
            return false; // only commit while descending, so "straight ahead" carries it into the ground
        }
        double dy = ctx.position().y - ctx.target().y;
        double d2 = ctx.horizontalDist() * ctx.horizontalDist() + dy * dy;
        return d2 <= COMMIT_RADIUS * COMMIT_RADIUS;
    }

    /** Fly straight on the current heading at the dive speed; fall back to a fresh dive if stationary. */
    private Vec3 holdHeading(MissileEntity missile, FlightContext ctx) {
        Vec3 v = missile.getDeltaMovement();
        double len = v.length();
        double speed = diveSpeed(missile);
        return len > 1.0E-4 ? v.scale(speed / len)
                : guideAngled(missile, ctx, missile.resolveDiveAngle(ctx));
    }

    /** Pure-pursuit onto the line through the target at {@code angleDeg} below horizontal, at the dive speed. */
    private Vec3 guideAngled(MissileEntity missile, FlightContext ctx, double angleDeg) {
        double theta = Math.toRadians(angleDeg);
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        Vec3 target = ctx.target();
        Vec3 dir = new Vec3(ctx.nx() * cos, -sin, ctx.nz() * cos);
        Vec3 pos = ctx.position();
        double along = pos.subtract(target).dot(dir);
        double carrotParam = Math.min(0.0, along + LOOKAHEAD);
        Vec3 carrot = target.add(dir.scale(carrotParam));
        Vec3 toCarrot = carrot.subtract(pos);
        double len = toCarrot.length();
        double speed = diveSpeed(missile);
        return len < 1.0E-4 ? dir.scale(speed) : toCarrot.scale(speed / len);
    }

    /** Never dive slower than the missile already cruises, but otherwise hold this variant's commanded speed. */
    private double diveSpeed(MissileEntity missile) {
        return Math.max(missile.getCruiseSpeed(), this.approachSpeed);
    }

    @Override
    public String id() {
        return this.id;
    }
}
