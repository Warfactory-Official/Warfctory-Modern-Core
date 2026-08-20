package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;

import brachy.modularui.screen.CustomModularScreen;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.ModularGuiContext;
import org.lwjgl.glfw.GLFW;

/**
 * Host screen for the capture browser. It carries no container and no sync manager — {@code /wfcore_diag view}
 * pushes the catalog to the client and {@link DiagViewerClient} owns everything from there, so the screen only
 * has to build the panel and hand back its frame textures when it goes away.
 */
public class DiagViewerScreen extends CustomModularScreen {

    public DiagViewerScreen() {
        super(WFCore.MOD_ID);
    }

    @Override
    public ModularPanel<?> buildUI(ModularGuiContext context) {
        return DiagViewerGui.build();
    }

    /**
     * Up/Down step through the captures on the active tab. {@code super} runs first so a focused widget keeps
     * first claim on the key; the search box is single-line and ignores Up/Down, so you can type a name and
     * then arrow through the results without unfocusing it. Left/Right stay unbound for the same reason —
     * they are the text cursor's.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            DiagViewerGui.navigate(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            DiagViewerGui.navigate(1);
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        // Also fires when Refresh replaces this screen with a fresh one; re-fetching a capture is cheap next
        // to leaving several full-resolution DynamicTextures resident for the rest of the session.
        DiagViewerClient.releaseTextures();
    }
}
