package com.norwood.wfcore.common.machine;

import net.minecraft.world.phys.AABB;

/**
 * A machine whose model reaches far beyond its controller block and so needs an enlarged render volume.
 *
 * <p>
 * Up to 1.20.1 this was {@code BlockEntity#getRenderBoundingBox}, overridden on the block entity itself.
 * NeoForge 1.21.1 moved that hook onto the <em>renderer</em>
 * ({@code IBlockEntityRendererExtension#getRenderBoundingBox(T)}), so the block entity can no longer answer
 * for itself. Rather than push each machine's dimensions out to the renderer registration site — where the
 * reason for a given number would be invisible — the machines keep the knowledge and expose it here, and
 * {@code MissileLauncherRenderer} consults it.
 *
 * <p>
 * The animated GLTF machines no longer do: their models are Flywheel instances now, culled on the GPU
 * against the bounding sphere of the posed model that {@code PoseCache} hands back. Their
 * {@link #getRenderBounds()} is inert until something draws them through a renderer again.
 */
public interface IWideRenderBoundsMachine {

    /**
     * The volume the renderer must consider visible. Returning too small a box frustum-culls the model as
     * soon as the controller leaves the screen, even when the rest of the structure is still in view.
     */
    AABB getRenderBounds();
}
