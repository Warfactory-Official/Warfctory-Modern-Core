package com.norwood.wfcore.client.render.gltf;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.Collection;
import java.util.List;

/**
 * Implemented by machines that drive an animated GLTF model through {@link GltfMachineRenderer}.
 * <p>
 * Mirrors the capabilities of the 1.12.2 {@code IAnimatedMTE}: an animation state, a per-frame run
 * flag (so the model can freeze in place on power loss and resume seamlessly), a transition policy
 * (finish the current loop before switching so the model never snaps), a render-space offset, and the
 * set of structure blocks the model visually replaces (hidden via the render mask).
 */
public interface IAnimatedMachine {

    /** Current animation state name, used to pick the {@link AnimationLoop}. */
    default String getAnimState() {
        return "default";
    }

    /**
     * Whether the animation clock should advance this frame. Returning false freezes the model on its
     * current frame (e.g. power loss); returning true again resumes from that frame.
     */
    default boolean isAnimationRunning() {
        return true;
    }

    /** How to leave {@code from} when the desired state becomes {@code to}. */
    default AnimTransition getAnimTransition(String from, String to) {
        return AnimTransition.FINISH_LOOP;
    }

    /** Local render-space offset applied before orientation, in blocks (mirrors 1.12.2 {@code getTransform}). */
    default Vec3 getModelTransform() {
        return Vec3.ZERO;
    }

    /** Local render-space scale applied before orientation, per axis (1.0 = model's built size). */
    default Vec3 getModelScale() {
        return new Vec3(1, 1, 1);
    }

    /** Whether the model should be drawn this frame (e.g. only when the multiblock is formed). */
    default boolean shouldRenderModel() {
        return true;
    }

    /**
     * Drives the current animation directly to this fraction {@code [0, 1]} of its duration, bypassing the
     * free-running clock (and {@link #isAnimationRunning()}). Use for a machine-controlled pose that isn't a
     * loop — e.g. a deploy/retract where the machine ramps the fraction up, holds, then ramps it back down.
     * Return a negative value (the default) to use the normal clock-driven playback instead.
     */
    default float getAnimationOverride() {
        return -1f;
    }

    /** Structure blocks the model visually replaces; hidden from chunk rendering while formed. */
    default Collection<BlockPos> getHiddenBlocks() {
        return List.of();
    }
}
