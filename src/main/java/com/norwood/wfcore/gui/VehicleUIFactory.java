package com.norwood.wfcore.gui;

import com.lowdragmc.lowdraglib.gui.factory.UIFactory;
import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.IWFCoreVehicleUI;
import com.norwood.wfcore.WFCore;

/**
 * LDLib {@link UIFactory} that opens the WFCore vehicle-storage ModularUI for a {@link VehicleEntity}.
 *
 * <p>
 * The holder sync data carries the entity id <em>and</em> the server-authoritative slot/column counts, so the client
 * rebuilds an identically-sized grid regardless of its local {@code wfcore.toml}. Register {@link #INSTANCE} on both
 * sides ({@code WFCore} common setup) so the client can resolve the open packet.
 */
public class VehicleUIFactory extends UIFactory<VehicleEntity> {

    public static final VehicleUIFactory INSTANCE = new VehicleUIFactory();

    private VehicleUIFactory() {
        super(WFCore.id("vehicle_storage"));
    }

    @Override
    protected ModularUI createUITemplate(VehicleEntity holder, Player entityPlayer) {
        return holder instanceof IUIHolder uiHolder ? uiHolder.createUI(entityPlayer) : null;
    }

    @Override
    protected VehicleEntity readHolderFromSyncData(FriendlyByteBuf syncData) {
        int entityId = syncData.readVarInt();
        int slots = syncData.readVarInt();
        int cols = syncData.readVarInt();

        var level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        Entity entity = level.getEntity(entityId);
        if (!(entity instanceof VehicleEntity vehicle)) {
            return null;
        }
        if (vehicle instanceof IWFCoreVehicleUI ui) {
            ui.wfcore$setSyncedUiSize(slots, cols);
        }
        return vehicle;
    }

    @Override
    protected void writeHolderToSyncData(FriendlyByteBuf syncData, VehicleEntity holder) {
        syncData.writeVarInt(holder.getId());
        syncData.writeVarInt(holder.getContainerSize());
        syncData.writeVarInt(holder instanceof IWFCoreVehicleUI ui ? ui.wfcore$uiColumns() : 9);
    }
}
