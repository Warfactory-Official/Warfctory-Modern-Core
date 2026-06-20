package com.norwood.wfcore.common.machine;

import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;

/**
 * Per-block-entity playback state that gives GeckoLib machine animations the same capabilities as the
 * 1.12.2 McGLTF framework:
 * <ul>
 * <li>a clock that freezes in place on power loss and resumes seamlessly ({@link #tick}),</li>
 * <li>finishing the current loop before switching states so the model never snaps ({@link #handle}).</li>
 * </ul>
 *
 * <p>
 * GeckoLib derives all playback from the animatable's tick, so {@link #tick} is used as the block
 * entity's tick source (a freezable clock) and the single controller predicate delegates to
 * {@link #handle}. Loop boundaries are read back from the controller's current animation length, which
 * keeps this helper in lockstep with GeckoLib's own internal loop wrapping.
 */
public final class MachineAnimator {

    /** How to leave the current animation when the desired state changes. */
    public enum Transition {
        /** Switch immediately. */
        SNAP,
        /** Keep playing the current animation until it loops back to its start, then switch. */
        FINISH_LOOP
    }

    private double clock;
    private double lastSource;
    private boolean started;

    private String currentKey;
    private String queuedKey;
    private RawAnimation queuedAnim;
    private double cycleStart;
    private double lastWrapped;

    /**
     * Freezable animation clock, used as the block entity's GeckoLib tick source. Repeated calls within
     * one frame are harmless: {@code source} is constant per frame, so the delta is zero.
     *
     * @param source    a monotonically increasing tick source (e.g. {@code RenderUtils.getCurrentTick()})
     * @param advancing whether the clock should advance this frame; false freezes the model in place
     */
    public double tick(double source, boolean advancing) {
        if (!started) {
            lastSource = source;
            started = true;
        }
        double delta = Math.max(0d, source - lastSource);
        lastSource = source;
        if (advancing) {
            clock += delta;
        }
        return clock;
    }

    /**
     * Reconciles the desired state with what is playing and switches at the right moment.
     *
     * @param state       the controller state passed to the predicate
     * @param desiredKey  stable name of the desired animation
     * @param desiredAnim the desired animation
     * @param transition  how to leave the current animation when {@code desiredKey} differs
     */
    public PlayState handle(AnimationState<?> state, String desiredKey, RawAnimation desiredAnim,
                            Transition transition) {
        AnimationController<?> controller = state.getController();

        if (currentKey == null) {
            switchTo(controller, desiredKey, desiredAnim);
            return PlayState.CONTINUE;
        }

        if (desiredKey.equals(currentKey)) {
            queuedKey = null;
            queuedAnim = null;
        } else if (!desiredKey.equals(queuedKey)) {
            if (transition == Transition.SNAP) {
                switchTo(controller, desiredKey, desiredAnim);
                return PlayState.CONTINUE;
            }
            queuedKey = desiredKey;
            queuedAnim = desiredAnim;
        }

        if (queuedKey != null) {
            var current = controller.getCurrentAnimation();
            double length = current != null ? current.animation().length() : 0d;
            double wrapped = length > 0d ? (clock - cycleStart) % length : 0d;
            if (length <= 0d || wrapped < lastWrapped) {
                switchTo(controller, queuedKey, queuedAnim);
            } else {
                lastWrapped = wrapped;
            }
        }

        return PlayState.CONTINUE;
    }

    private void switchTo(AnimationController<?> controller, String key, RawAnimation anim) {
        controller.setAnimation(anim);
        currentKey = key;
        queuedKey = null;
        queuedAnim = null;
        cycleStart = clock;
        lastWrapped = 0d;
    }
}
