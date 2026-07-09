package com.norwood.wfcore.client.render.gltf;

import com.gregtechceu.gtceu.api.blockentity.MetaMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.api.pattern.util.RelativeDirection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.Direction;

import com.modularmods.mcgltf.MCglTF;
import com.modularmods.mcgltf.RenderedGltfModel;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.debug.ModelTransformDebug;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import com.norwood.wfcore.mixin.LightTextureAccessor;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Renders a machine's animated GLTF model with McGLTF, replacing the 1.12.2 coremod's
 * {@code MteRenderer}/{@code GenericGLTF}/{@code AnimatedRenderQueue} pipeline with a standard 1.20.1
 * {@link BlockEntityRenderer}.
 * <p>
 * Each block entity keeps its own {@link AnimationController} (freezable clock; finishes the current
 * loop before switching states), advanced from world time + partial ticks. The model is drawn with raw
 * GL through McGLTF's vanilla render path, so the pose is composed as
 * {@code modelView * poseStack} and the GL bindings the library leaves dirty (VAO, buffers, active
 * texture unit) are reset afterwards to avoid leaking state into the rest of the frame.
 */
public class GltfMachineRenderer<T extends MetaMachineBlockEntity> implements BlockEntityRenderer<T> {

    private final MachineGltfModel model;
    private final Map<T, AnimationController> controllers = new WeakHashMap<>();

    public GltfMachineRenderer(MachineGltfModel model) {
        this.model = model;
    }

    @Override
    public void render(T be, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight, int packedOverlay) {
        MetaMachine machine = be.getMetaMachine();
        if (!(machine instanceof IAnimatedMachine animated)) {
            return;
        }

        boolean formed = animated.shouldRenderModel();

        boolean realLevel = be.getLevel() != null && be.getLevel() == Minecraft.getInstance().level;

        if (realLevel) {

            boolean masked = RenderMaskManager.isControllerMasked(be.getBlockPos());
            if (formed && !masked) {
                RenderMaskManager.addDisableModel(be.getBlockPos(), animated.getHiddenBlocks());
            } else if (!formed && masked) {
                RenderMaskManager.removeDisableModel(be.getBlockPos());
            }
        }

        if (!formed || !realLevel || model.scene == null || model.animations == null) {
            return; // not formed, off-world preview, or model not loaded on the client yet
        }

        AnimationController controller = controllers.computeIfAbsent(be, k -> new AnimationController());
        float now = (be.getLevel() != null ? (float) be.getLevel().getGameTime() : 0f) + partialTick;
        controller.advance(animated, model.animations, now);
        AnimationLoop loop = model.animations.get(controller.getCurrent());
        if (loop != null) {
            loop.update(controller.getTime());
        }

        poseStack.pushPose();
        var transform = ModelTransformDebug.resolve(animated, machine.getFrontFacing());
        poseStack.translate(transform.x, transform.y, transform.z);
        applyOrientation(poseStack, machine);

        // McGLTF's vanilla path uploads CURRENT_POSE as the shader ModelViewMat, so it must already
        // include the camera view rotation that vanilla bakes into RenderSystem's model-view matrix.
        Matrix4f pose = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        poseStack.popPose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();

        // mcgltf draws immediately with the vanilla entity shader but never binds the lightmap, and the
        // model's per-vertex TEXCOORD_1 lightmap UVs are a fixed-but-dim second UV set, so the dish would be
        // either black (unbound) or stuck at ~half light. Mirror the 1.12.2 path
        // (setLightmapTextureCoords(getCombinedLight(pos.up(15)))): bind a 1x1 texture holding the real
        // lightmap colour sampled at the dish's open-sky position, so it tracks day/night world light.
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldLightLightmap(be));
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        // mcgltf's vanilla path folds each node's normal into the static CURRENT_NORMAL for lighting, but
        // the library never initialises it — pair it with the pose (inverse-transpose of the model-view's
        // 3x3) or it NPEs in applyTransformVanilla on the first node.
        RenderedGltfModel.setCurrentPose(pose);
        RenderedGltfModel.setCurrentNormal(new Matrix3f(pose).invert().transpose());
        try {
            if (MCglTF.getInstance().isShaderModActive()) {
                model.scene.renderForShaderMod();
            } else {
                model.scene.renderForVanilla();
            }
        } catch (RuntimeException e) {
            WFCore.LOGGER.error("Failed to render GLTF model {}", model.getModelLocation(), e);
        } finally {
            restoreGlState();
        }
    }

    private static DynamicTexture worldLightTexture;

    /**
     * A 1x1 texture holding the vanilla lightmap colour at the dish's open-sky position (controller + 15,
     * mirroring the 1.12.2 {@code getCombinedLight(pos.up(15))}). Re-sampled each frame so the model is lit
     * by real world light — bright at noon, dim at night — instead of its dim per-vertex {@code TEXCOORD_1}.
     */
    private static int worldLightLightmap(MetaMachineBlockEntity be) {
        if (worldLightTexture == null) {
            worldLightTexture = new DynamicTexture(new NativeImage(1, 1, false));
        }
        int color = 0xFFFFFFFF;
        if (be.getLevel() != null) {
            int packed = LevelRenderer.getLightColor(be.getLevel(), be.getBlockPos().above(15));
            NativeImage pixels = ((LightTextureAccessor) Minecraft.getInstance().gameRenderer.lightTexture())
                    .wfcore$getLightPixels();
            if (pixels != null) {
                color = pixels.getPixelRGBA(LightTexture.block(packed), LightTexture.sky(packed));
            }
        }
        worldLightTexture.getPixels().setPixelRGBA(0, 0, color);
        worldLightTexture.upload();
        return worldLightTexture.getId();
    }

    /**
     * McGLTF draws with its own VAO/buffers/shader and only restores the shader program. Reset the
     * remaining bindings so vanilla's batched draws later in the frame are not corrupted. The library
     * bypasses {@link RenderSystem}, so the raw GL state is reset directly and {@link RenderSystem}'s
     * cached active texture is resynced to match.
     */
    private static void restoreGlState() {
        GL30.glBindVertexArray(0);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
    }

    /** Orients the model for a multiblock controller, ported from the 1.12.2 {@code MteRenderer}. */
    private static void applyOrientation(PoseStack poseStack, MetaMachine machine) {
        Direction front = machine.getFrontFacing();
        if (machine instanceof MultiblockControllerMachine controller) {
            Direction up = controller.getUpwardsFacing();
            boolean flipped = controller.isFlipped();
            if (flipped) {
                flip(poseStack, RelativeDirection.LEFT.getRelative(front, up, flipped));
            }
            rotateToFace(poseStack, front, up);
        } else if (front != null) {
            rotateToFace(poseStack, front, Direction.NORTH);
        }
    }

    private static void rotateToFace(PoseStack poseStack, Direction face, Direction spin) {
        int angle = switch (spin) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
        switch (face) {
            case UP -> {
                poseStack.scale(-1, 1, 1);
                rotX(poseStack, 90);
                rotZ(poseStack, -angle);
            }
            case DOWN -> {
                rotX(poseStack, 270);
                rotZ(poseStack, spin == Direction.EAST || spin == Direction.WEST ? -angle : angle);
            }
            case EAST -> {
                rotY(poseStack, 270);
                rotZ(poseStack, angle);
            }
            case WEST -> {
                rotY(poseStack, 90);
                rotZ(poseStack, angle);
            }
            case NORTH -> rotZ(poseStack, angle);
            case SOUTH -> {
                rotY(poseStack, 180);
                rotZ(poseStack, angle);
            }
        }
    }

    private static void flip(PoseStack poseStack, Direction facing) {
        float fX = facing.getStepX() == 0 ? 1 : -1;
        float fY = facing.getStepY() == 0 ? 1 : -1;
        float fZ = facing.getStepZ() == 0 ? 1 : -1;
        poseStack.scale(fX, fY, fZ);
    }

    private static void rotX(PoseStack poseStack, float deg) {
        poseStack.mulPose(com.mojang.math.Axis.XP.rotationDegrees(deg));
    }

    private static void rotY(PoseStack poseStack, float deg) {
        poseStack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(deg));
    }

    private static void rotZ(PoseStack poseStack, float deg) {
        poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(deg));
    }

    @Override
    public boolean shouldRenderOffScreen(T be) {
        return true; // the dish is far taller than the controller block's own AABB
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
