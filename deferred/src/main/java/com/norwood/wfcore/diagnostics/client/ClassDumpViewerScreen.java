package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;

import brachy.modularui.screen.CustomModularScreen;
import brachy.modularui.screen.ModularPanel;
import brachy.modularui.screen.viewport.ModularGuiContext;
import org.lwjgl.glfw.GLFW;


public class ClassDumpViewerScreen extends CustomModularScreen {

    private final long generation;

    public ClassDumpViewerScreen(long generation) {
        super(WFCore.MOD_ID);
        this.generation = generation;
    }

    @Override
    public ModularPanel<?> buildUI(ModularGuiContext context) {
        return ClassDumpViewerGui.build();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (super.keyPressed(keyCode, scanCode, modifiers)) {
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_UP) {
            ClassDumpViewerGui.navigate(-1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_DOWN) {
            ClassDumpViewerGui.navigate(1);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            ClassDumpViewerClient.applyClassFilter();
            return true;
        }
        return false;
    }

    @Override
    public void onClose() {
        super.onClose();
        ClassDumpViewerClient.onScreenClosed(generation);
    }
}
