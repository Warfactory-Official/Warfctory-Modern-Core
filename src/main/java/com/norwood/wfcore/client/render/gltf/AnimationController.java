package com.norwood.wfcore.client.render.gltf;

import com.google.common.collect.ImmutableMap;

/**
 * Per-machine playback state. Ported from the 1.12.2 coremod
 * ({@code wfcore.common.render.AnimationController}).
 * <p>
 * Drives a real clock advanced by frame deltas instead of deriving time straight from world time. This
 * is what lets a machine freeze mid-animation on power loss and resume seamlessly, and lets it finish
 * its current loop before switching states so the model never snaps between poses. One instance lives
 * per rendered block entity; it mutates the shared model's nodes only through the loop it selects,
 * immediately before that block entity is drawn.
 */
public final class AnimationController {

    private String current;
    private String queued;
    private float time;
    private boolean started;
    private float lastTicks;

    public String getCurrent() {
        return current;
    }

    public float getTime() {
        return time;
    }

    /**
     * Reconciles the desired state with what is playing and advances the clock for this frame.
     *
     * @param machine    source of the desired state, run flag and transition policy
     * @param animations the renderer's loop map (key -> loop)
     * @param nowTicks   world time plus partial ticks, used to derive the frame delta
     */
    public void advance(IAnimatedMachine machine, ImmutableMap<String, AnimationLoop> animations, float nowTicks) {
        if (animations == null || animations.isEmpty()) {
            return;
        }

        String desired = machine.getAnimState();
        if (!started) {
            current = animations.containsKey(desired) ? desired : animations.keySet().iterator().next();
            queued = null;
            time = 0f;
            lastTicks = nowTicks;
            started = true;
            return;
        }

        float dt = Math.max(0f, nowTicks - lastTicks) / 20f;
        lastTicks = nowTicks;

        if (desired.equals(current)) {
            queued = null;
        } else if (animations.containsKey(desired) && !desired.equals(queued)) {
            if (machine.getAnimTransition(current, desired) == AnimTransition.SNAP) {
                current = desired;
                queued = null;
                time = 0f;
            } else {
                queued = desired;
            }
        }

        if (machine.isAnimationRunning()) {
            time += dt;
        }

        AnimationLoop loop = animations.get(current);
        if (loop == null) {
            return;
        }
        float dur = loop.getDuration();

        if (dur <= 0f) {
            if (queued != null && animations.containsKey(queued)) {
                current = queued;
                queued = null;
            }
            time = 0f;
            return;
        }

        if (time >= dur) {
            if (queued != null && animations.containsKey(queued)) {
                float carry = time - dur;
                current = queued;
                queued = null;
                float nextDur = animations.get(current).getDuration();
                time = nextDur > 0f ? carry % nextDur : 0f;
            } else if (loop.isLoop()) {
                time %= dur;
            } else {
                time = dur;
            }
        }
    }
}
