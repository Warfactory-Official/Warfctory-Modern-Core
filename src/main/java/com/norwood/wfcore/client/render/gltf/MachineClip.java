package com.norwood.wfcore.client.render.gltf;

import com.wf.gemrender.gltf.GltfAnimation;

/**
 * One of a machine model's animations, plus whether it repeats. Replaces the 1.12.2-derived
 * {@code AnimationLoop}: GemRender's {@link GltfAnimation} is a value that samples into a caller's
 * buffer rather than an object that writes into the shared model, so all that is left to carry beside
 * it is wfcore's own {@code <name>_loop} convention.
 */
public record MachineClip(GltfAnimation clip, boolean loop) {

    public float duration() {
        return clip.duration();
    }

    /** Clip-local time for a controller clock, wrapped or held at the last frame per {@link #loop}. */
    public float timeAt(float seconds) {
        float duration = duration();
        if (duration <= 0f) {
            return 0f;
        }
        if (seconds < duration) {
            return seconds;
        }
        return loop ? clip.loop(seconds) : duration;
    }
}
