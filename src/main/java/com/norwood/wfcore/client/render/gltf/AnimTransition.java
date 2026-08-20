package com.norwood.wfcore.client.render.gltf;

/**
 * Policy describing how a machine leaves its current animation when the desired state changes.
 * Ported verbatim from the 1.12.2 coremod.
 */
public enum AnimTransition {
    /** Switch to the new animation immediately. May visually snap. */
    SNAP,
    /** Keep playing the current animation until it reaches its (loop) end, then switch. Avoids snapping. */
    FINISH_LOOP
}
