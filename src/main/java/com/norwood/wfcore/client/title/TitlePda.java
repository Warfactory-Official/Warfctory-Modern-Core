package com.norwood.wfcore.client.title;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.wf.gemrender.asset.GemRenderModels;
import com.wf.gemrender.asset.ModelCache;
import com.wf.gemrender.direct.DirectPass;
import com.wf.gemrender.direct.DirectRenderer;
import com.wf.gemrender.direct.TextureSlots;
import com.wf.gemrender.gltf.GemRenderGltfModel;
import com.wf.gemrender.gltf.NodeTable;
import com.wf.gemrender.texture.VariantUv;
import com.wf.wflib.view.api.ArmViewPose;
import com.wf.wflib.view.api.PlayerArms;
import com.wf.wflib.pda.api.client.PdaShell;
import com.wf.wflib.ui.api.UiContext;
import com.wf.wflib.ui.api.UiSurfaces;
import com.wf.wflib.ui.api.input.UiInput;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

import com.norwood.wfcore.WFCore;

/**
 * Held PDA and bare arms drawn through GemRender under a private perspective, the {@code screen} slot a
 * {@link UiSurfaces} texture of {@link TitleMenu}. The OS cursor is unprojected onto the screen quad.
 */
final class TitlePda {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("wflib", "models/pda/pda.glb");
    private static final Object SURFACE = new Object();
    private static final String SCREEN = "screen";
    private static final float SCREEN_WIDTH = 0.224f;
    private static final float SCREEN_HEIGHT = 0.14f;
    private static final float SCREEN_Z = 0.0115f;
    private static final int MAX_PIXELS_PER_UNIT = 8;
    private static final float FOV = 70;
    private static final float SCALE = 1.6f;
    private static final float BASE_TILT = -10;
    private static final Vector3f BASE_AT = new Vector3f(0, -0.02f, -0.34f);
    private static final int LIGHT = 0xF000F0;

    private static final Matrix4f PROJECTION = new Matrix4f();
    private static final Matrix4f LOCAL = new Matrix4f();
    private static final Matrix4f INVERSE = new Matrix4f();
    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Matrix4f UPPER = new Matrix4f();
    private static final Matrix4f LOWER = new Matrix4f();
    private static final Vector3f WRIST = new Vector3f();
    private static final Vector3f SHOULDER = new Vector3f();
    private static final Vector3f ELBOW = new Vector3f();
    private static final Vector3f ORIGIN = new Vector3f();
    private static final Vector3f RAY = new Vector3f();
    private static final Vector3f[] GRIPS = {new Vector3f(), new Vector3f()};
    private static final TextureSlots SLOTS = new TextureSlots() {
        @Override
        public ResourceLocation texture(String slot) {
            return SCREEN.equals(slot) ? surface : null;
        }

        @Override
        public boolean emissive(String slot) {
            return true;
        }
    };

    private static ModelCache.Handle<GemRenderGltfModel> handle;
    private static ResourceLocation surface;
    private static float cursorX;
    private static float cursorY;
    private static boolean onScreen;
    private static boolean hidden;
    private static boolean pressed;
    private static boolean latch;

    private TitlePda() {
    }

    static boolean cursorOnScreen() {
        return onScreen;
    }

    static float cursorX() {
        return cursorX;
    }

    static float cursorY() {
        return cursorY;
    }

    static void press(boolean down) {
        pressed = down;
        latch |= down;
    }

    /** Restores the OS cursor. */
    static void close() {
        pressed = false;
        latch = false;
        onScreen = false;
        showCursor();
    }

    /** @param mouseX, mouseY GUI-scaled position */
    static void render(GuiGraphics graphics, int guiWidth, int guiHeight, double mouseX, double mouseY, float time) {
        if (handle == null) {
            handle = GemRenderModels.handle(MODEL);
        }
        GemRenderGltfModel model = handle.get();
        PlayerArms arms = PlayerArms.get();
        if (model == null || arms == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        NodeTable table = model.layout().nodeTable();
        grip(table, "grip_right", GRIPS[0]);
        grip(table, "grip_left", GRIPS[1]);

        PROJECTION.identity().perspective((float) Math.toRadians(FOV),
                (float) mc.getWindow().getWidth() / mc.getWindow().getHeight(), 0.05f, 10);
        float swayX = (float) Math.sin(time * 0.55) * 0.006f;
        float swayY = (float) Math.sin(time * 0.8 + 1.3) * 0.004f;
        float tilt = BASE_TILT + (float) Math.sin(time * 0.43 + 0.6) * 1.2f;
        LOCAL.translation(BASE_AT.x + swayX, BASE_AT.y + swayY, BASE_AT.z).rotateX((float) Math.toRadians(tilt))
                .scale(SCALE);

        unproject(mouseX / guiWidth, mouseY / guiHeight);

        float ndcHalf = PROJECTION.m00() * SCREEN_WIDTH * SCALE / 2 / -BASE_AT.z;
        int pixelsPerUnit = Math.clamp(Math.round(Math.round(ndcHalf * mc.getWindow().getWidth()) / PdaShell.WIDTH), 1,
                MAX_PIXELS_PER_UNIT);
        surface = UiSurfaces.request(SURFACE, TitleMenu.layout(), UiContext.EMPTY, (int) PdaShell.WIDTH * pixelsPerUnit,
                (int) PdaShell.HEIGHT * pixelsPerUnit, PdaShell.WIDTH, PdaShell.HEIGHT, 0, 0xFF000000);
        UiInput input = UiSurfaces.input(SURFACE);
        if (input != null) {
            input.pointer(onScreen ? cursorX : -1000, onScreen ? cursorY : -1000,
                    pressed || latch ? UiInput.PRIMARY : 0);
            latch = false;
        }
        if (onScreen == !hidden) {
            if (onScreen) {
                GLFW.glfwSetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR, GLFW.GLFW_CURSOR_HIDDEN);
                hidden = true;
            } else {
                showCursor();
            }
        }

        graphics.flush();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(PROJECTION, VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.getModelViewStack().pushMatrix().identity();
        RenderSystem.applyModelViewMatrix();
        try {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            DirectRenderer.submit(model, null, 0, LOCAL, LIGHT, OverlayTexture.NO_OVERLAY, 0xFFFFFFFF,
                    DirectPass.HAND, VariantUv.NONE, SLOTS);
            for (int side = 0; side < 2; side++) {
                LOCAL.transformPosition(GRIPS[side], WRIST);
                ArmViewPose.shoulder(side == 1, SHOULDER);
                ArmViewPose.solve(WRIST, null, SHOULDER, side == 0 ? ArmViewPose.RIGHT_POLE : ArmViewPose.LEFT_POLE,
                        arms.lengths, UPPER, LOWER, ELBOW);
                arms.pose(side, UPPER, LOWER);
            }
            arms.submit(IDENTITY, LIGHT, 0xFFFFFFFF, mc.getSkinManager().getInsecureSkin(mc.getGameProfile()));
            DirectRenderer.flush(DirectPass.HAND);
        } finally {
            RenderSystem.getModelViewStack().popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
        }
    }

    /** Ray through the cursor (fractions of the window) against the screen plane, in shell units. */
    private static void unproject(double fx, double fy) {
        float ndcX = (float) (fx * 2 - 1);
        float ndcY = (float) (1 - fy * 2);
        RAY.set(ndcX / PROJECTION.m00(), ndcY / PROJECTION.m11(), -1);
        INVERSE.set(LOCAL).invert();
        INVERSE.transformPosition(ORIGIN.set(0, 0, 0));
        INVERSE.transformDirection(RAY);
        float t = (SCREEN_Z - ORIGIN.z) / RAY.z;
        float x = ORIGIN.x + RAY.x * t;
        float y = ORIGIN.y + RAY.y * t;
        cursorX = (x / SCREEN_WIDTH + 0.5f) * PdaShell.WIDTH;
        cursorY = (0.5f - y / SCREEN_HEIGHT) * PdaShell.HEIGHT;
        onScreen = t > 0 && cursorX >= 0 && cursorX < PdaShell.WIDTH && cursorY >= 0 && cursorY < PdaShell.HEIGHT;
    }

    private static void showCursor() {
        if (hidden) {
            GLFW.glfwSetInputMode(Minecraft.getInstance().getWindow().getWindow(), GLFW.GLFW_CURSOR,
                    GLFW.GLFW_CURSOR_NORMAL);
            hidden = false;
        }
    }

    private static void grip(NodeTable table, String name, Vector3f out) {
        int slot = table.slotOfName(name);
        out.set(table.restTranslation(slot, 0), table.restTranslation(slot, 1), table.restTranslation(slot, 2));
    }
}
