package com.norwood.wfcore.client.render.gltf;

import com.modularmods.mcgltf.animation.InterpolatedChannel;

import java.util.List;

/**
 * A single GLTF animation, wrapping its interpolated channels. Ported from the 1.12.2 coremod
 * ({@code wfcore.common.render.AnimationLoop}). Drives the shared model's nodes when {@link #update}
 * is called with the controller's current time.
 */
public class AnimationLoop {

    public final List<InterpolatedChannel> animation;

    private boolean loop = false;
    private float duration = -1f;

    public AnimationLoop(List<InterpolatedChannel> animation) {
        this.animation = animation;
    }

    public boolean isLoop() {
        return loop;
    }

    public void setLoop(boolean loop) {
        this.loop = loop;
    }

    public void update(float t) {
        float dur = getDuration();
        if (t >= dur) {
            t = loop ? t % dur : dur;
        }
        for (InterpolatedChannel channel : animation) {
            channel.update(t);
        }
    }

    public float getDuration() {
        if (duration < 0f) {
            float max = 0f;
            for (InterpolatedChannel c : animation) {
                float[] keys = c.getKeys();
                if (keys.length > 0) {
                    max = Math.max(max, keys[keys.length - 1]);
                }
            }
            duration = max;
        }
        return duration;
    }
}
