package com.norwood.wfcore.mixin;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModMenuTypes;
import com.atsuishio.superbwarfare.menu.VehicleMenu;
import com.norwood.wfcore.IVehicleFuelTank;
import com.norwood.wfcore.SuperbFuelOverride;
import com.norwood.wfcore.capabilities.SyncedEntityFuelStorage;
import com.norwood.wfcore.handlers.WFCoreFuelHandler;
import com.norwood.wfcore.serializer.WFCoreSerializers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidTank;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = VehicleEntity.class)
public abstract class SuperbWarfareInvMixin extends Entity implements IVehicleFuelTank {
    @Unique
    public static final EntityDataAccessor<FluidStack> FUEL = SynchedEntityData.defineId(VehicleEntity.class, WFCoreSerializers.FLUID_STACK_ENTITY_DATA_SERIALIZER);

    static {
        System.out.println("DEBUG: Mixin For Superb Loaded Successfully!");
    }
    @Shadow(remap = false) public abstract boolean hasEnergyStorage();

    @Unique
    protected SyncedEntityFuelStorage wfcore$fluidTank;
    @Unique
    protected LazyOptional<IFluidTank> wfcore$fuel;

    @Override
    public SyncedEntityFuelStorage getFluidTank() {
        return this.wfcore$fluidTank;
    }


    public SuperbWarfareInvMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Shadow(remap = false)
    public abstract DefaultVehicleData computed();

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$setFluidTank(EntityType<?> type, Level level, CallbackInfo ci) {
        this.wfcore$fluidTank = new SyncedEntityFuelStorage(SuperbFuelOverride.overrideDataMap.get(computed().getId()).MaxFuel(), (VehicleEntity) (Object) this);
        wfcore$fuel = LazyOptional.of(() -> wfcore$fluidTank);
    }


    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void wfcore$writeFuelNbt(CompoundTag compound, CallbackInfo ci) {
        if (wfcore$fluidTank != null) {
            compound.put("WFCoreFuel", wfcore$fluidTank.writeToNBT(new CompoundTag()));
        }
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void wfcore$readFuelNbt(CompoundTag compound, CallbackInfo ci) {
        if (wfcore$fluidTank != null && compound.contains("WFCoreFuel")) {
            wfcore$fluidTank.readFromNBT(compound.getCompound("WFCoreFuel"));
        }
    }

    @Unique
    public EntityDataAccessor<FluidStack> getFuelAccessor() {
        return FUEL;
    }

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



    @Redirect(
            method = "baseTick",
            at = @At(value = "INVOKE", target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;hasEnergyStorage()Z"),
            remap = false
    )
    private boolean wfcore$redirectFuelBlock(VehicleEntity instance) {
        if (instance.tickCount % 20 == 0) {
            WFCoreFuelHandler.handleVehicleRefueling(instance, computed().getId());
        }

        return false;
    }





}
