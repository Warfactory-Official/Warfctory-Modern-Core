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
import com.mojang.blaze3d.platform.GlStateManager;
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
            return;
        }

        AnimationController controller = controllers.computeIfAbsent(be, k -> new AnimationController());
        float now = (be.getLevel() != null ? (float) be.getLevel().getGameTime() : 0f) + partialTick;
        controller.advance(animated, model.animations, now);
        AnimationLoop loop = model.animations.get(controller.getCurrent());
        if (loop != null) {

            float override = animated.getAnimationOverride();
            float time = override >= 0f ? Math.min(override, 1f) * loop.getDuration() : controller.getTime();
            loop.update(time);
        }

        poseStack.pushPose();
        var transform = ModelTransformDebug.resolve(animated, machine.getFrontFacing());
        poseStack.translate(transform.x, transform.y, transform.z);
        var scale = ModelTransformDebug.resolveScale(animated, machine.getFrontFacing());
        poseStack.scale((float) scale.x, (float) scale.y, (float) scale.z);
        applyOrientation(poseStack, machine);


        Matrix4f pose = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(poseStack.last().pose());
        poseStack.popPose();


        final int prevVao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        final int prevArrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        final int prevElementArrayBuffer = GL11.glGetInteger(GL15.GL_ELEMENT_ARRAY_BUFFER_BINDING);
        final boolean prevCullFace = GL11.glGetBoolean(GL11.GL_CULL_FACE);
        final boolean prevDepthTest = GL11.glGetBoolean(GL11.GL_DEPTH_TEST);
        final boolean prevBlend = GL11.glGetBoolean(GL11.GL_BLEND);

        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GlStateManager._blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(true);


        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        final int prevTexture2 = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldLightLightmap(be));
        GL13.glActiveTexture(GL13.GL_TEXTURE0);


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
            restoreGlState(prevVao, prevArrayBuffer, prevElementArrayBuffer,
                    prevCullFace, prevDepthTest, prevBlend, prevTexture2);
        }
    }

    private static DynamicTexture worldLightTexture;


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
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, worldLightTexture.getId());
        worldLightTexture.getPixels().upload(0, 0, 0, false);
        return worldLightTexture.getId();
    }


    private static void restoreGlState(int prevVao, int prevArrayBuffer, int prevElementArrayBuffer,
                                       boolean prevCullFace, boolean prevDepthTest, boolean prevBlend,
                                       int prevTexture2) {
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, prevTexture2);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);

        if (!prevDepthTest) GL11.glDisable(GL11.GL_DEPTH_TEST);
        if (!prevBlend) GL11.glDisable(GL11.GL_BLEND);
        if (prevCullFace) GL11.glEnable(GL11.GL_CULL_FACE);
        else GL11.glDisable(GL11.GL_CULL_FACE);

        GL30.glBindVertexArray(prevVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, prevArrayBuffer);
        GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, prevElementArrayBuffer);
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
