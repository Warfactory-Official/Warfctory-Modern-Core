package com.norwood.wfcore.client.render.gltf;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.multiblock.util.RelativeDirection;
import com.mojang.math.Axis;
import com.norwood.wfcore.client.debug.ModelTransformDebug;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import com.wf.gemrender.gltf.AnimationDrive;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.render.GemRenderInstance;
import com.wf.gemrender.render.GemRenderInstanceTypes;
import com.wf.gemrender.render.PoseCache;
import com.wf.gemrender.render.PoseLod;

import dev.engine_room.flywheel.api.instance.Instance;
import dev.engine_room.flywheel.api.visualization.VisualizationContext;
import dev.engine_room.flywheel.lib.visual.AbstractBlockEntityVisual;
import dev.engine_room.flywheel.lib.visual.SimpleDynamicVisual;
import dev.engine_room.flywheel.lib.visualization.SimpleBlockEntityVisualizer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;

import org.joml.Matrix4f;

import java.util.Map;
import java.util.function.Consumer;

/**
 * Draws a machine's animated GLTF model as a single Flywheel instance, replacing the McGLTF
 * {@code GltfMachineRenderer}. The model is uploaded once and posed through GemRender's shared bone
 * palette, so a frame costs one instance write and one palette evaluation rather than a draw call and a
 * block of raw GL state per machine.
 * <p>
 * The type parameter is {@link MetaMachine} rather than a block entity because GregTech 8.0.0 folded
 * {@code MetaMachineBlockEntity} into {@code MetaMachine} — the machine now <em>is</em> the block entity.
 */
public class GemRenderMachineVisual<T extends MetaMachine> extends AbstractBlockEntityVisual<T>
                                   implements SimpleDynamicVisual {

    private final MachineModel model;
    private final GltfMachineLightSampler lightSampler;
    private final AnimationController controller = new AnimationController();

    private GemRenderInstance instance;

    /** Built once per clip, for the machines that drive their pose by parameter rather than by clock. */
    private AnimationDrive drive;

    /** Rebuilt only when the machine's facing or the debug editor's offsets change. */
    private final Matrix4f pose = new Matrix4f();
    private Direction poseFacing;
    private Vec3 poseOffset;
    private Vec3 poseScale;

    /** What was last asked of {@link RenderMaskManager}; the request itself happens on the client tick. */
    private boolean maskRequested;

    private int light = -1;

    public GemRenderMachineVisual(VisualizationContext ctx, T machine, float partialTick,
                                  MachineModel model, GltfMachineLightSampler lightSampler) {
        super(ctx, machine, partialTick);
        this.model = model;
        this.lightSampler = lightSampler;
    }

    @Override
    public void beginFrame(Context ctx) {
        if (!(blockEntity instanceof IAnimatedMachine animated)) {
            return;
        }

        boolean formed = animated.shouldRenderModel();
        GemRenderGltfModel gltf = model.model();

        if (!formed || gltf == null) {
            release();
            return;
        }

        if (!maskRequested) {
            maskRequested = true;
            // getHiddenBlocks walks the machine's structure pattern, so it is deferred to the tick that
            // applies the mask rather than run here on a Flywheel task thread.
            RenderMaskManager.requestMask(pos, animated::getHiddenBlocks);
        }

        if (instance == null) {
            instance = instancerProvider().instancer(GemRenderInstanceTypes.SKINNED, gltf.model())
                    .createInstance();
            instance.colorArgb(0xFFFFFFFF);
            light = -1;
            poseFacing = null;
        }

        Map<String, MachineClip> animations = model.animations();
        controller.advance(animated, animations, level.getGameTime() + ctx.partialTick());

        MachineClip clip = animations.get(controller.getCurrent());
        float time = clip == null ? 0f : timeOf(animated, clip);

        // Distance decides how coarse the pose is, never whether there is one. An instance carries an
        // offset into a bone palette that is rebuilt from nothing every frame, so a machine that sits a
        // frame out keeps an offset that by then belongs to whatever was evaluated in its place, and
        // draws itself with another model's bones. Flywheel's frame limiter is exactly that mistake --
        // past about 45 blocks it answers "skip" on one frame in two -- so it is not applied here, and
        // neither is a frustum test: a claim missed off screen is still a claim missed. PoseLod does the
        // job the limiter was reached for, widening the time bucket rather than dropping the frame.
        int lod = PoseLod.getInstance().levelAt(pos.distToCenterSqr(ctx.camera().getPosition()));

        PoseCache.Pose posed = PoseCache.getInstance()
                .pose(gltf.layout(), gltf.bounds(), gltf.morphs(),
                        clip == null ? null : clip.clip(), time, lod);

        boolean changed = updatePose(animated) | updateLightIfStale();

        if (instance.boneBase != posed.boneBase() || instance.morphBase != posed.morphBase()
                || !instance.boneSphere.equals(posed.sphere())) {
            instance.boneBase = posed.boneBase();
            instance.morphBase = posed.morphBase();
            instance.boneSphere.set(posed.sphere());
            changed = true;
        }

        // A stalled machine resolves to the same shared pose every frame; leaving it unchanged is what
        // keeps it off the upload path entirely.
        if (changed) {
            instance.setChanged();
        }
    }

    /**
     * Clip-local time: the controller's own clock, unless the machine drives the pose directly.
     * <p>
     * An override is a fraction of the clip rather than a moment in it — the interceptor ramps its dome
     * open and closed off {@code deployProgress}, which is a position, not a time. {@link AnimationDrive}
     * is the GemRender API for exactly that: it maps a parameter onto the clip and holds the end frames
     * outside the range, so a dome that is 40% deployed is at 40% of the animation whether it is opening
     * or closing.
     */
    private float timeOf(IAnimatedMachine animated, MachineClip clip) {
        float override = animated.getAnimationOverride();
        if (override < 0f) {
            return clip.timeAt(controller.getTime());
        }
        if (drive == null || drive.clip() != clip.clip()) {
            drive = AnimationDrive.ranged(clip.clip(), 0f, 1f);
        }
        return drive.timeAt(override);
    }

    @Override
    public void updateLight(float partialTick) {
        light = -1;
    }

    private boolean updateLightIfStale() {
        int packed = lightSampler.getLightLevel(blockEntity);
        if (packed == light) {
            return false;
        }
        light = packed;
        instance.light(packed);
        return true;
    }

    /**
     * Recomputes the model-to-world matrix when the machine's orientation or the live debug offsets move.
     * The BER built this on the pose stack every frame; here it survives between frames because a
     * multiblock's facing does not change while it is formed.
     */
    private boolean updatePose(IAnimatedMachine animated) {
        Direction front = blockEntity.getFrontFacing();
        Vec3 offset = ModelTransformDebug.resolve(animated, front);
        Vec3 scale = ModelTransformDebug.resolveScale(animated, front);

        if (front == poseFacing && offset.equals(poseOffset) && scale.equals(poseScale)) {
            return false;
        }
        poseFacing = front;
        poseOffset = offset;
        poseScale = scale;

        pose.translation(visualPos.getX() + (float) offset.x, visualPos.getY() + (float) offset.y,
                        visualPos.getZ() + (float) offset.z)
                .scale((float) scale.x, (float) scale.y, (float) scale.z);
        applyOrientation(pose, blockEntity);

        instance.pose.set(pose);
        return true;
    }

    private void release() {
        if (instance != null) {
            instance.delete();
            instance = null;
        }
        if (maskRequested) {
            maskRequested = false;
            RenderMaskManager.requestUnmask(pos);
        }
    }

    @Override
    protected void _delete() {
        release();
    }

    /**
     * Nothing. A crumbling overlay is drawn by re-rendering the instance into the breaking-texture pass,
     * and GemRender's instance type carries no overlay attribute — the per-vertex overlay channel holds
     * the morph set index. Breaking the controller block shows the vanilla overlay on the block itself.
     */
    @Override
    public void collectCrumblingInstances(Consumer<Instance> consumer) {
    }

    /** Orients the model for a multiblock controller, ported from the 1.12.2 {@code MteRenderer}. */
    private static void applyOrientation(Matrix4f pose, MetaMachine machine) {
        Direction front = machine.getFrontFacing();
        if (machine instanceof MultiblockControllerMachine controller) {
            Direction up = controller.getUpwardsFacing();
            boolean flipped = controller.isFlipped();
            if (flipped) {
                flip(pose, RelativeDirection.LEFT.getRelativeFacing(front, up, flipped));
            }
            rotateToFace(pose, front, up);
        } else if (front != null) {
            rotateToFace(pose, front, Direction.NORTH);
        }
    }

    private static void rotateToFace(Matrix4f pose, Direction face, Direction spin) {
        int angle = switch (spin) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        switch (face) {
            case UP -> {
                pose.scale(-1, 1, 1);
                pose.rotate(Axis.XP.rotationDegrees(90));
                pose.rotate(Axis.ZP.rotationDegrees(-angle));
            }
            case DOWN -> {
                pose.rotate(Axis.XP.rotationDegrees(270));
                pose.rotate(Axis.ZP.rotationDegrees(
                        spin == Direction.EAST || spin == Direction.WEST ? -angle : angle));
            }
            case EAST -> {
                pose.rotate(Axis.YP.rotationDegrees(270));
                pose.rotate(Axis.ZP.rotationDegrees(angle));
            }
            case WEST -> {
                pose.rotate(Axis.YP.rotationDegrees(90));
                pose.rotate(Axis.ZP.rotationDegrees(angle));
            }
            case NORTH -> pose.rotate(Axis.ZP.rotationDegrees(angle));
            case SOUTH -> {
                pose.rotate(Axis.YP.rotationDegrees(180));
                pose.rotate(Axis.ZP.rotationDegrees(angle));
            }
        }
    }

    private static void flip(Matrix4f pose, Direction facing) {
        pose.scale(facing.getStepX() == 0 ? 1 : -1, facing.getStepY() == 0 ? 1 : -1,
                facing.getStepZ() == 0 ? 1 : -1);
    }

    /** A visual factory for one machine type, sampling light from the given offsets. */
    public static <T extends MetaMachine> SimpleBlockEntityVisualizer.Factory<T> of(MachineModel model,
                                                                                    BlockPos... lightOffsets) {
        GltfMachineLightSampler sampler = lightOffsets.length == 0
                ? new GltfMachineLightSampler(BlockPos.ZERO)
                : new GltfMachineLightSampler(lightOffsets);
        return (ctx, machine, partialTick) ->
                new GemRenderMachineVisual<>(ctx, machine, partialTick, model, sampler);
    }
}
