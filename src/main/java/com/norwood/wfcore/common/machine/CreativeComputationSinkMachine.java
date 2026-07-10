package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.capability.GTCapabilityHelper;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationProvider;
import com.gregtechceu.gtceu.api.capability.IOpticalComputationReceiver;
import com.gregtechceu.gtceu.api.gui.GuiTextures;
import com.gregtechceu.gtceu.api.machine.IMachineBlockEntity;
import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.api.machine.TickableSubscription;
import com.gregtechceu.gtceu.api.machine.feature.IUIMachine;

import com.lowdragmc.lowdraglib.gui.modular.ModularUI;
import com.lowdragmc.lowdraglib.gui.texture.GuiTextureGroup;
import com.lowdragmc.lowdraglib.gui.texture.ResourceBorderTexture;
import com.lowdragmc.lowdraglib.gui.texture.TextTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.SwitchWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.syncdata.annotation.DescSynced;
import com.lowdragmc.lowdraglib.syncdata.annotation.Persisted;
import com.lowdragmc.lowdraglib.syncdata.field.ManagedFieldHolder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
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
                                            implements IUIMachine, IOpticalComputationReceiver,
                                            IOpticalComputationProvider {

    public static final ManagedFieldHolder MANAGED_FIELD_HOLDER = new ManagedFieldHolder(
            CreativeComputationSinkMachine.class, MetaMachine.MANAGED_FIELD_HOLDER);

    /** Target CWU/t to drain each tick; set in the GUI. */
    @Persisted
    @DescSynced
    private int drainCWUt = 0;
    /** Whether the sink is actively draining. */
    @Persisted
    @DescSynced
    private boolean active = true;
    /** Rolling one-second sum of CWU actually drained; averaged into {@link #lastConsumedCWUt}. */
    private int consumedCWUPerSec = 0;
    /** Last computed per-second average of CWU/t actually drained (display only). */
    @DescSynced
    private int lastConsumedCWUt = 0;

    @Nullable
    private TickableSubscription computationSubs;

    public CreativeComputationSinkMachine(IMachineBlockEntity holder) {
        super(holder);
    }

    @Override
    public ManagedFieldHolder getFieldHolder() {
        return MANAGED_FIELD_HOLDER;
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
        BlockPos pos = getPos();
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

    private void setDrainCWUt(String text) {
        try {
            this.drainCWUt = Math.max(0, Integer.parseInt(text));
        } catch (NumberFormatException ignored) {
            // setNumbersOnly already restricts input; ignore transient partial edits.
        }
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        return new ModularUI(140, 95, this, entityPlayer)
                .background(GuiTextures.BACKGROUND)
                .widget(new LabelWidget(7, 7, "CWU/t"))
                .widget(new TextFieldWidget(9, 20, 122, 16,
                        () -> String.valueOf(drainCWUt), this::setDrainCWUt)
                        .setNumbersOnly(0, Integer.MAX_VALUE))
                .widget(new LabelWidget(7, 42, "gtceu.creative.computation.average"))
                .widget(new LabelWidget(7, 54, () -> String.valueOf(lastConsumedCWUt)))
                .widget(new SwitchWidget(9, 66, 122, 20, (clickData, value) -> setActive(value))
                        .setTexture(
                                new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON,
                                        new TextTexture("gtceu.creative.activity.off")),
                                new GuiTextureGroup(ResourceBorderTexture.BUTTON_COMMON,
                                        new TextTexture("gtceu.creative.activity.on")))
                        .setSupplier(() -> active));
    }
}
