package com.norwood.wfcore.common.data;

import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.flight.FlightContext;
import com.wf.wfballistics.flight.FlightStage;
import com.wf.wfballistics.flight.FlightStageRegistry;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Endless-loiter cruise — a copy of WF-B's {@code LoiterStage} that orbits the target point at cruise altitude
 * but <em>never</em> hands off to the terminal dive. Where the stock loiter stage circles for a fixed
 * {@code LOITER_TICKS} and then attacks, this one just keeps orbiting until the tank runs dry, at which point
 * {@link MissileEntity} takes over with a ballistic fall (harmless for an inert drone).
 *
 * <p>Registered for the {@link MissileEntity.Phase#CRUISE} phase as {@value #ID}; drop it into a preset's
 * cruise slot ({@code .cruiseStage(...)}) to make a pure loitering recon/decoy drone.
 */
public final class LoiterUntilDryStage implements FlightStage {

    /** Stage id. The {@code wfcore_} prefix keeps it ours (WF-B namespaces its own as {@code wfballistics:...}). */
    public static final String ID = "wfcore_loiter_until_dry";

    public static final LoiterUntilDryStage INSTANCE = new LoiterUntilDryStage();

    private static final double ORBIT_RADIUS = 24.0;
    private static final double ALTITUDE_GAIN = 0.1;

    private LoiterUntilDryStage() {}

    public static void register() {
        FlightStageRegistry.register(MissileEntity.Phase.CRUISE, INSTANCE);
    }

    @Override
    public Vec3 guide(MissileEntity missile, FlightContext ctx) {
        double maxSpeed = missile.getCruiseSpeed();
        double vy = Mth.clamp((ctx.safeAltitude() - missile.getY()) * ALTITUDE_GAIN, -maxSpeed, maxSpeed);

        double dist = ctx.horizontalDist();
        double vx;
        double vz;
        if (dist > ORBIT_RADIUS + 4.0) {
            vx = ctx.nx() * maxSpeed;
            vz = ctx.nz() * maxSpeed;
        } else {
            double tx = -ctx.nz();
            double tz = ctx.nx();
            double radialErr = Mth.clamp((dist - ORBIT_RADIUS) / ORBIT_RADIUS, -1.0, 1.0);
            vx = (tx + ctx.nx() * radialErr) * maxSpeed;
            vz = (tz + ctx.nz() * radialErr) * maxSpeed;
        }
        return new Vec3(vx, vy, vz);
    }

    @Override
    @Nullable
    public MissileEntity.Phase next(MissileEntity missile, FlightContext ctx) {

        return null;
    }

    @Override
    public String id() {
        return ID;
    }
}
