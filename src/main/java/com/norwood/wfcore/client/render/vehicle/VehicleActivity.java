package com.norwood.wfcore.client.render.vehicle;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.Entity;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;

/**
 * Decides whether a Superb Warfare vehicle is "active" (moving / driven / animating) and therefore must keep
 * rendering through GeckoLib, or "idle" and eligible for the cheap baked static-mesh path.
 * <p>
 * The raw predicate reads only getters verified to exist on the compiled 0.8.9
 * {@link VehicleEntity} (javap-checked — note the input flags are {@code forwardInputDown()} etc., <em>not</em>
 * {@code getForwardInputDown()}). A per-entity linger (hysteresis) then keeps a vehicle "active" for a short
 * window after it last read active, so a vehicle that briefly stops does not thrash the VBO swap and any wheel
 * or turret spin-down finishes on the GeckoLib path before we freeze it.
 */
public final class VehicleActivity {

    private VehicleActivity() {}

    private static final float EPS_F = 1.0e-3f;
    private static final double EPS_D = 1.0e-3;

    /** Last game-time (ticks) at which a vehicle read raw-active; used for the idle linger. */
    private static final Map<Entity, Long> LAST_ACTIVE = new WeakHashMap<>();

    /** True while the vehicle is doing anything that visibly animates the model this frame. */
    public static boolean isActiveRaw(VehicleEntity v) {
        if (v.getFirstPassenger() != null) return true;
        if (Math.abs(v.getPower()) > EPS_F) return true;
        if (v.getEngineStart() || v.getEngineStartOver()) return true;
        if (v.getDeltaMovement().lengthSqr() > 1.0e-6) return true;
        if (Math.abs(v.getAbsoluteSpeed()) > EPS_D) return true;
        if (v.forwardInputDown() || v.backInputDown() || v.leftInputDown() || v.rightInputDown()
                || v.upInputDown() || v.downInputDown() || v.fireInputDown() || v.sprintInputDown()) {
            return true;
        }
        if (Math.abs(v.getRecoilShake()) > EPS_D) return true;                       // just fired
        if (v.hasTurret()) {
            if (Math.abs(v.getTurretYRot() - v.getTurretYRotO()) > EPS_F) return true; // turret slew
            if (Math.abs(v.getTurretXRot() - v.getTurretXRotO()) > EPS_F) return true;
        }
        if (Math.abs(v.getPropellerRot() - v.getPropellerRotO()) > EPS_F) return true; // prop / wheel / track spin
        return Math.abs(v.getRoll() - v.getPrevRoll()) > EPS_F;                        // banking
    }

    /** Active this frame, or within {@link WFVehicleRenderConfig#IDLE_HOLD_TICKS} of the last active frame. */
    public static boolean isActive(VehicleEntity v) {
        // A wreck never animates, so it is always eligible for the static path (drawn with its wreck texture).
        if (v.isWreck() || v.getSympatheticDetonated()) {
            return false;
        }
        long now = v.level().getGameTime();
        boolean raw = isActiveRaw(v);
        if (raw) {
            LAST_ACTIVE.put(v, now);
            return true;
        }
        Long last = LAST_ACTIVE.get(v);
        return last != null && (now - last) <= WFVehicleRenderConfig.IDLE_HOLD_TICKS;
    }
}
