package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.blockentity.BlockEntityCreationInfo;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IMuiMachine;
import com.gregtechceu.gtceu.api.machine.mui.MachineUIPanelBuilder;
import com.gregtechceu.gtceu.api.sync_system.annotations.SyncToClient;
import com.gregtechceu.gtceu.api.sync_system.annotations.SaveField;

import brachy.modularui.api.drawable.Text;
import brachy.modularui.drawable.Rectangle;
import brachy.modularui.factory.PosGuiData;
import brachy.modularui.screen.UISettings;
import brachy.modularui.value.sync.BooleanSyncValue;
import brachy.modularui.value.sync.IntSyncValue;
import brachy.modularui.value.sync.PanelSyncManager;
import brachy.modularui.widget.ParentWidget;
import brachy.modularui.widgets.TextWidget;
import brachy.modularui.widgets.ToggleButton;
import brachy.modularui.widgets.layout.Flow;
import brachy.modularui.widgets.textfield.TextFieldWidget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Creative computation sink: the mirror of GregTech's {@code creative_computation_provider}. Where the
 * provider feeds an unlimited, configurable CWU/t <em>into</em> a computation network, this block
 * <em>drains</em> a configurable CWU/t out of it — a debug/load-test tool for computation providers
 * (e.g. draining a {@link MainframeMachine} to verify its output and energy draw).
 *
 * <p>
 * It resolves its upstream provider by scanning its six neighbours for the optical-computation-provider
 * capability, so it works placed directly against a Computation Data Transmission hatch (or the creative
 * provider) and through optical fibre pipes. Like GregTech's receiver hatch it is also a pass-through
 * {@link IOpticalComputationProvider} (delegating to the resolved provider with a {@code seen} guard) so
 * that optical pipes connect to it.
 */
public class CreativeComputationSinkMachine extends MetaMachine
                                            implements IMuiMachine, IOpticalComputationReceiver,
                                            IOpticalComputationProvider {


    /** Target CWU/t to drain each tick; set in the GUI. */
    @SaveField
    @SyncToClient
    private int drainCWUt = 0;
    /** Whether the sink is actively draining. */
    @SaveField
    @SyncToClient
    private boolean active = true;
    /** Rolling one-second sum of CWU actually drained; averaged into {@link #lastConsumedCWUt}. */
    private int consumedCWUPerSec = 0;
    /** Last computed per-second average of CWU/t actually drained (display only). */
    @SyncToClient
    private int lastConsumedCWUt = 0;

    @Nullable
    private TickableSubscription computationSubs;

    public CreativeComputationSinkMachine(BlockEntityCreationInfo info) {
        super(info);
    }

    //////////////////// lifecycle / tick ////////////////////

    @Override
    public void onLoad() {
        super.onLoad();
        updateComputationSubscription();
    }

    protected void updateComputationSubscription() {
        if (active && !isRemote()) {
            if (computationSubs == null) {
                computationSubs = subscribeServerTick(this::updateComputationTick);
            }
        } else if (computationSubs != null) {
            computationSubs.unsubscribe();
            computationSubs = null;
            lastConsumedCWUt = 0;
            consumedCWUPerSec = 0;
        }
    }

    protected void updateComputationTick() {
        if (isRemote()) {
            return;
        }
        int consumed = 0;
        if (active && drainCWUt > 0) {
            Set<IOpticalComputationProvider> seen = new HashSet<>();
            seen.add(this);
            IOpticalComputationProvider provider = findNetProvider(seen);
            if (provider != null) {
                consumed = provider.requestCWUt(drainCWUt, false, seen);
            }
        }
        consumedCWUPerSec += consumed;
        if (getOffsetTimer() % 20 == 0) {
            lastConsumedCWUt = consumedCWUPerSec / 20;
            consumedCWUPerSec = 0;
        }
    }

    /** Scans the six neighbours for an optical-computation provider (a hatch, the creative provider, or a pipe). */
    @Nullable
    private IOpticalComputationProvider findNetProvider(Collection<IOpticalComputationProvider> seen) {
        Level level = getLevel();
        BlockPos pos = getBlockPos();
        if (level == null || pos == null) {
            return null;
        }
        for (Direction dir : Direction.values()) {
            IOpticalComputationProvider provider = GTCapabilityHelper.getOpticalComputationProvider(
                    level, pos.relative(dir), dir.getOpposite());
            if (provider != null && provider != this && !seen.contains(provider)) {
                return provider;
            }
        }
        return null;
    }

    //////////////////// IOpticalComputationReceiver ////////////////////

    @Override
    public IOpticalComputationProvider getComputationProvider() {
        Set<IOpticalComputationProvider> seen = new HashSet<>();
        seen.add(this);
        return findNetProvider(seen);
    }

    //////////////////// IOpticalComputationProvider (pass-through so pipes connect) ////////////////////

    @Override
    public int requestCWUt(int cwut, boolean simulate, Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        IOpticalComputationProvider provider = findNetProvider(seen);
        return provider == null ? 0 : provider.requestCWUt(cwut, simulate, seen);
    }

    @Override
    public int getMaxCWUt(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        IOpticalComputationProvider provider = findNetProvider(seen);
        return provider == null ? 0 : provider.getMaxCWUt(seen);
    }

    @Override
    public boolean canBridge(Collection<IOpticalComputationProvider> seen) {
        seen.add(this);
        // A sink is a terminal consumer, never a network bridge.
        return false;
    }

    //////////////////// config / UI ////////////////////

    public void setActive(boolean value) {
        this.active = value;
        updateComputationSubscription();
    }

    public boolean isActive() {
        return active;
    }

    /** No inventory, so the panel skips the player hotbar the way GregTech's creative provider does. */
    @Override
    public MachineUIPanelBuilder getPanelBuilder(PosGuiData data, PanelSyncManager syncManager, UISettings settings) {
        return MachineUIPanelBuilder.panelBuilder(this).attachInventory(false);
    }

    @Override
    public void buildMainUI(ParentWidget<?> mainWidget, PosGuiData guiData, PanelSyncManager syncManager,
                            UISettings settings) {
        IntSyncValue consumedSync = syncManager.getOrCreateSyncHandler("lastConsumedCWUt", IntSyncValue.class,
                () -> new IntSyncValue(() -> this.lastConsumedCWUt, v -> {}));

        mainWidget.child(Flow.column()
                .padding(10)
                .childPadding(5)
                .child(Flow.row()
                        .leftRel(0)
                        .childPadding(5)
                        .coverChildren()
                        .child(new TextWidget<>(Text.lang("gtceu.creative.computation.max_usage")))
                        .child(new TextFieldWidget()
                                .setNumbers(0, Integer.MAX_VALUE)
                                .value(new IntSyncValue(() -> drainCWUt, v -> drainCWUt = v).allowC2S())))
                .child(new Rectangle().color(0xFF555555).asWidget()
                        .height(1).widthRel(0.95f).marginBottom(4).marginTop(4))
                .child(Flow.row()
                        .leftRel(0)
                        .childPadding(5)
                        .coverChildren()
                        .child(Text.dynamic(() -> Text.lang("gtceu.creative.computation.average",
                                consumedSync.getIntValue()).getFormatted()).asWidget()))
                .child(new ToggleButton()
                        .value(new BooleanSyncValue(this::isActive, this::setActive).allowC2S())
                        .overlay(false, Text.lang("gtceu.creative.activity.off"))
                        .overlay(true, Text.lang("gtceu.creative.activity.on"))
                        .size(122, 20)));
    }
}
