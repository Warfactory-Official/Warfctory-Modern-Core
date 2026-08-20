package com.norwood.wfcore.common.mui;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine;
import com.gregtechceu.gtceu.common.mui.GTMultiblockTextUtil;
import com.gregtechceu.gtceu.utils.FormattingUtil;
import com.gregtechceu.gtceu.utils.GTUtil;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.api.widget.IWidget;
import brachy.modularui.drawable.GuiTextures;
import brachy.modularui.drawable.Icon;
import brachy.modularui.utils.Alignment;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widget.Widget;
import brachy.modularui.widgets.ListWidget;
import brachy.modularui.widgets.TextWidget;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * The wfcore counterpart to GregTech's {@code GTMultiblockTextUtil}, for the multiblocks that extend
 * {@link com.gregtechceu.gtceu.api.machine.multiblock.MultiblockControllerMachine} directly instead of
 * {@code WorkableElectricMultiblockMachine}.
 *
 * <p>
 * GregTech 8.0.0 hangs its status readout off {@code getWidgetsForDisplay}, but every helper that fills
 * that list — energy usage, working status — is typed against {@code WorkableElectricMultiblockMachine},
 * because it reads the machine's {@code RecipeLogic}. The wfcore controllers (transformer, mainframe,
 * radar, missile launcher, drill rig, research unit) have no recipe logic at all; they run their own
 * tick loops and track their own status flags. These helpers take plain suppliers instead, so those
 * machines produce lines that look identical to GregTech's without inventing a recipe logic to satisfy
 * the signature.
 *
 * <p>
 * The text and colours mirror {@code GTMultiblockTextUtil} exactly, so a wfcore multiblock reads the same
 * as a GregTech one sitting next to it.
 */
public final class WFMuiWidgets {

    /** Matches {@code WorkableElectricMultiblockMachine.MULTI_UI_TEXT_PANEL_WIDTH}. */
    public static final int TEXT_PANEL_WIDTH = 172;
    /** Matches {@code WorkableElectricMultiblockMachine.MULTI_UI_TEXT_PANEL_HEIGHT}. */
    public static final int TEXT_PANEL_HEIGHT = 136;

    private WFMuiWidgets() {}

    /**
     * The black status box that fills a standard multiblock panel, holding one widget per line.
     *
     * <p>
     * This is a copy of {@code WorkableElectricMultiblockMachine#getMainTextPanel}, which is not reachable
     * from a plain {@code MultiblockControllerMachine}. The list collapses disabled children, so a line
     * hidden with {@code setEnabledIf} leaves no gap.
     */
    public static Widget<?> displayBox(List<IWidget> lines) {
        var list = new ListWidget<>()
                .width(TEXT_PANEL_WIDTH - 6)
                .height(TEXT_PANEL_HEIGHT - 6)
                .childSeparator(Icon.EMPTY_2PX)
                .crossAxisAlignment(Alignment.CrossAxis.START)
                .collapseDisabledChildren()
                .posRel(Alignment.CenterLeft);
        list.children(lines);
        return new ParentWidget<>()
                .size(TEXT_PANEL_WIDTH, TEXT_PANEL_HEIGHT)
                .background(GuiTextures.DISPLAY)
                .child(list.left(3).top(3));
    }

    /**
     * The {@code gtceu.multiblock.max_energy_per_tick} line, driven by a caller-supplied voltage rather
     * than by an {@code EnergyContainerList} on a recipe-logic machine.
     */
    public static TextWidget<?> energyUsageLine(BooleanSupplier formed, BooleanSupplier active,
                                                LongSupplier voltage) {
        return Text.dynamic(() -> Component
                .translatable("gtceu.multiblock.max_energy_per_tick",
                        FormattingUtil.formatNumbers(voltage.getAsLong()),
                        Component.literal(GTValues.VNF[GTUtil.getFloorTierByVoltage(voltage.getAsLong())]))
                .withStyle(ChatFormatting.GRAY))
                .asWidget()
                .setEnabledIf($ -> formed.getAsBoolean() && active.getAsBoolean());
    }

    /** The paused / running / idling status line, in GregTech's wording and colours. */
    public static TextWidget<?> workingStatusLine(BooleanSupplier formed, BooleanSupplier workingEnabled,
                                                  BooleanSupplier active) {
        return workingStatusLine(formed, workingEnabled, active,
                () -> Component.translatable("gtceu.multiblock.running").withStyle(ChatFormatting.GREEN));
    }

    /** As {@link #workingStatusLine(BooleanSupplier, BooleanSupplier, BooleanSupplier)}, with custom running text. */
    public static TextWidget<?> workingStatusLine(BooleanSupplier formed, BooleanSupplier workingEnabled,
                                                  BooleanSupplier active, Supplier<Component> running) {
        return workingStatusLine(formed, workingEnabled, active, running,
                () -> Component.translatable("gtceu.multiblock.work_paused").withStyle(ChatFormatting.GOLD),
                () -> Component.translatable("gtceu.multiblock.idling").withStyle(ChatFormatting.GRAY));
    }

    /**
     * The full form, matching the three keys of LDLib's {@code setWorkingStatusKeys(idling, paused, running)}
     * for the machines that overrode them.
     */
    public static TextWidget<?> workingStatusLine(BooleanSupplier formed, BooleanSupplier workingEnabled,
                                                  BooleanSupplier active, Supplier<Component> running,
                                                  Supplier<Component> paused, Supplier<Component> idling) {
        return Text.dynamic(() -> {
            if (!formed.getAsBoolean()) return Component.empty();
            if (!workingEnabled.getAsBoolean()) return paused.get();
            if (active.getAsBoolean()) return running.get();
            return idling.get();
        })
                .asWidget()
                .setEnabledIf($ -> formed.getAsBoolean());
    }

    /** A line that is only drawn while the structure is formed. */
    public static TextWidget<?> formedLine(BooleanSupplier formed, Supplier<Component> text) {
        return Text.dynamic(text::get).asWidget().setEnabledIf($ -> formed.getAsBoolean());
    }

    /** A line that is only drawn while {@code condition} holds. */
    public static TextWidget<?> conditionalLine(BooleanSupplier condition, Supplier<Component> text) {
        return Text.dynamic(text::get).asWidget().setEnabledIf($ -> condition.getAsBoolean());
    }

    /**
     * GregTech's "structure is invalid" block, listing the pattern errors. This one <em>is</em> typed against
     * {@code MultiblockControllerMachine}, so it is passed straight through rather than reimplemented.
     */
    public static IWidget unformedWarning(MultiblockControllerMachine controller, PanelSyncManager syncManager) {
        return GTMultiblockTextUtil.addUnformedWarning(controller, syncManager);
    }
}
