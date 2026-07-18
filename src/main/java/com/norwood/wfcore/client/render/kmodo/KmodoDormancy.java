package com.norwood.wfcore.client.render.kmodo;

import com.atsuishio.superbwarfare.entity.vehicle.base.AutoAimableEntity;
import com.atsuishio.superbwarfare.entity.vehicle.base.GeoVehicleEntity;

public final class KmodoDormancy {

    private static final int STABLE_FRAMES = 3;
    private static final int PROBE_INTERVAL = 20;
    private static final String NO_TARGET = "undefined";

    private boolean dormant;
    private int stableCount;
    private long lastHash;
    private boolean hasHash;
    private long lastProbeTick;
    private boolean hasProbeTick;

    public boolean needsUpdate(GeoVehicleEntity e, boolean visualMoved) {
        if (!KmodoConfig.dormancyEnabled()) {
            dormant = false;
            return true;
        }
        if (!dormant) {
            return true;
        }
        if (visualMoved || wakeSignal(e)) {
            wake();
            return true;
        }
        long now = e.tickCount;
        if (!hasProbeTick || now - lastProbeTick >= PROBE_INTERVAL) {
            lastProbeTick = now;
            hasProbeTick = true;
            return true;
        }
        return false;
    }

    public void recordPose(long hash, long tick) {
        if (!KmodoConfig.dormancyEnabled()) {
            dormant = false;
            lastHash = hash;
            hasHash = true;
            lastProbeTick = tick;
            hasProbeTick = true;
            return;
        }
        if (hasHash && hash == lastHash) {
            if (!dormant && ++stableCount >= STABLE_FRAMES) {
                dormant = true;
            }
        } else {
            stableCount = 0;
            dormant = false;
        }
        lastHash = hash;
        hasHash = true;
        lastProbeTick = tick;
        hasProbeTick = true;
    }

    public boolean isDormant() {
        return dormant && KmodoConfig.dormancyEnabled();
    }

    public enum State {
        ACTIVE,
        SETTLING,
        DORMANT
    }

    public State state() {
        if (!KmodoConfig.dormancyEnabled()) {
            return State.ACTIVE;
        }
        if (dormant) {
            return State.DORMANT;
        }
        if (stableCount > 0) {
            return State.SETTLING;
        }
        return State.ACTIVE;
    }

    private void wake() {
        dormant = false;
        stableCount = 0;
    }

    private boolean wakeSignal(GeoVehicleEntity e) {
        if (e.getControllingPassenger() != null) {
            return true;
        }
        if (e.isVehicle()) {
            return true;
        }
        if (e.getDeltaMovement().lengthSqr() > 1.0e-6) {
            return true;
        }
        if (e.getX() != e.xOld || e.getY() != e.yOld || e.getZ() != e.zOld) {
            return true;
        }
        if (e.getYRot() != e.yRotO || e.getXRot() != e.xRotO) {
            return true;
        }
        if (e.getTurretYRot() != e.getTurretYRotO() || e.getTurretXRot() != e.getTurretXRotO()) {
            return true;
        }
        if (e.getGunYRot() != e.getGunYRotO() || e.getGunXRot() != e.getGunXRotO()) {
            return true;
        }
        if (e.getCannonRecoilTime() > 0) {
            return true;
        }
        if (!NO_TARGET.equals(e.getAiTurretTargetUUID()) || !NO_TARGET.equals(e.getAiPassengerWeaponTargetUUID())) {
            return true;
        }
        if (e instanceof AutoAimableEntity a && a.getActive()) {
            return true;
        }
        return e.isOnFire();
    }
}
