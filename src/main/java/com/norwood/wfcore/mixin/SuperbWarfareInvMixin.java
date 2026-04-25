package com.norwood.wfcore.mixin;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModMenuTypes;
import com.atsuishio.superbwarfare.menu.VehicleMenu;
import com.norwood.wfcore.WFCore;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = VehicleEntity.class)
public abstract class SuperbWarfareInvMixin {
    static {
        System.out.println("DEBUG: Mixin For Superb Loaded Successfully!");
    }

    @Shadow(remap = false) public abstract DefaultVehicleData computed();

    /**
     * @author MrNorwood
     * @reason Bruh
     */
    @Overwrite(remap = false)
    public int getContainerSize() {
        var type = computed().vehicleContainerType;
        if (type == null) return 0;
        return computed().vehicleContainerType.getSize();
    }

    /**
     * @author MrNorwood
     * @reason Restore inventory size functionality
     */
    @Overwrite(remap = false)
    public @Nullable AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory, Player pPlayer) {
        if (!pPlayer.isSpectator()) {
            var computed = computed();
            var type = computed.vehicleContainerType;
            if (type == null) return null;

            var upgrade = computed.hasUpgradeSlots;
            var menu = switch (type) {
                case MINI ->
                        upgrade ? ModMenuTypes.VEHICLE_MENU_MINI_UPGRADE.get() : ModMenuTypes.VEHICLE_MENU_MINI.get();
                case SMALL ->
                        upgrade ? ModMenuTypes.VEHICLE_MENU_SMALL_UPGRADE.get() : ModMenuTypes.VEHICLE_MENU_SMALL.get();
                case MEDIUM ->
                        upgrade ? ModMenuTypes.VEHICLE_MENU_MEDIUM_UPGRADE.get() : ModMenuTypes.VEHICLE_MENU_MEDIUM.get();
                case LARGE ->
                        upgrade ? ModMenuTypes.VEHICLE_MENU_LARGE_UPGRADE.get() : ModMenuTypes.VEHICLE_MENU_LARGE.get();
                case HUGE ->
                        upgrade ? ModMenuTypes.VEHICLE_MENU_HUGE_UPGRADE.get() : ModMenuTypes.VEHICLE_MENU_HUGE.get();
                default -> null;
            };
            if (menu == null) return null;

            return new VehicleMenu(menu, pContainerId, pPlayerInventory, (Container) this, type.getRow(), type.getCol(), upgrade);
        }
        return null;
    }
}
