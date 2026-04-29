package com.norwood.wfcore.mixin;

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
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.atsuishio.superbwarfare.init.ModMenuTypes;
import com.atsuishio.superbwarfare.menu.VehicleMenu;
import com.norwood.wfcore.IVehicleFuelTank;
import com.norwood.wfcore.SuperbFuelOverride;
import com.norwood.wfcore.capabilities.SyncedEntityFuelStorage;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.handlers.WFCoreFuelHandler;
import com.norwood.wfcore.serializer.WFCoreSerializers;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = VehicleEntity.class)
public abstract class SuperbWarfareInvMixin extends Entity implements IVehicleFuelTank {

    @Unique
    private static final EntityDataAccessor<FluidStack> FUEL = SynchedEntityData.defineId(VehicleEntity.class,
            WFCoreSerializers.FLUID_STACK_ENTITY_DATA_SERIALIZER);

    @Unique
    protected SyncedEntityFuelStorage wfcore$fluidTank;
    @Unique
    protected LazyOptional<IFluidHandler> wfcore$fuel;
    @Unique
    protected boolean wfcore$usesFluidFuel;
    @Unique
    @Nullable
    protected String wfcore$vehicleId;

    public SuperbWarfareInvMixin(EntityType<?> entityType, Level level) {
        super(entityType, level);
    }

    @Inject(method = "defineSynchedData", at = @At("TAIL"))
    private void wfcore$defineFluidData(CallbackInfo ci) {
        this.getEntityData().define(FUEL, FluidStack.EMPTY);
    }

    @Shadow(remap = false)
    public abstract boolean hasEnergyStorage();

    @Shadow(remap = false)
    public abstract DefaultVehicleData computed();

    @Override
    public SyncedEntityFuelStorage getFluidTank() {
        return this.wfcore$fluidTank;
    }

    @Unique
    public EntityDataAccessor<FluidStack> getFuelAccessor() {
        return FUEL;
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void wfcore$setFluidTank(EntityType<?> type, Level level, CallbackInfo ci) {
        this.wfcore$vehicleId = wfcore$getVehicleId();
        var override = this.wfcore$vehicleId == null ? null : SuperbFuelOverride.getOverride(this.wfcore$vehicleId);
        if (override == null) {
            this.wfcore$usesFluidFuel = false;
            this.wfcore$fluidTank = null;
            this.wfcore$fuel = LazyOptional.empty();
            return;
        }

        this.wfcore$usesFluidFuel = true;
        this.wfcore$fluidTank = new SyncedEntityFuelStorage(override.maxFuel(), (VehicleEntity) (Object) this, FUEL);
        this.wfcore$fuel = LazyOptional.of(() -> wfcore$fluidTank);
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

    /**
     * @author MrNorwood
     * @reason Removing hardcoded limits
     */
    @Overwrite(remap = false)
    public int getContainerSize() {
        var type = computed().vehicleContainerType;
        if (type == null) return 0;
        return type.getSize();
    }

    /**
     * @author MrNorwood
     * @reason Restoring commented code
     */
    @Overwrite(remap = false)
    public @Nullable AbstractContainerMenu createMenu(int pContainerId, @NotNull Inventory pPlayerInventory,
                                                      Player pPlayer) {
        if (!pPlayer.isSpectator()) {
            var computed = computed();
            var type = computed.vehicleContainerType;
            if (type == null) return null;

            var upgrade = computed.hasUpgradeSlots;
            var menu = switch (type) {
                case MINI -> upgrade ? ModMenuTypes.VEHICLE_MENU_MINI_UPGRADE.get() :
                        ModMenuTypes.VEHICLE_MENU_MINI.get();
                case SMALL -> upgrade ? ModMenuTypes.VEHICLE_MENU_SMALL_UPGRADE.get() :
                        ModMenuTypes.VEHICLE_MENU_SMALL.get();
                case MEDIUM -> upgrade ? ModMenuTypes.VEHICLE_MENU_MEDIUM_UPGRADE.get() :
                        ModMenuTypes.VEHICLE_MENU_MEDIUM.get();
                case LARGE -> upgrade ? ModMenuTypes.VEHICLE_MENU_LARGE_UPGRADE.get() :
                        ModMenuTypes.VEHICLE_MENU_LARGE.get();
                case HUGE -> upgrade ? ModMenuTypes.VEHICLE_MENU_HUGE_UPGRADE.get() :
                        ModMenuTypes.VEHICLE_MENU_HUGE.get();
                default -> null;
            };
            if (menu == null) return null;

            return new VehicleMenu(menu, pContainerId, pPlayerInventory, (Container) (Object) this, type.getRow(),
                    type.getCol(), upgrade);
        }
        return null;
    }

    @Redirect(
              method = "baseTick",
              at = @At(value = "INVOKE",
                       target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;hasEnergyStorage()Z"),
              remap = false)
    private boolean wfcore$redirectFuelBlock(VehicleEntity instance) {
        String vehicleId = this.wfcore$vehicleId;
        if (vehicleId == null) {
            return false;
        }
        if (instance.tickCount % WFCoreConfig.getRefuelIntervalTicks() == 0) {
            WFCoreFuelHandler.handleVehicleRefueling(instance, vehicleId);
        }
        return false;
    }

    @Inject(method = "getEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$getFluidAsEnergy(CallbackInfoReturnable<Integer> cir) {
        if (this.wfcore$usesFluidFuel && this.wfcore$fluidTank != null) {
            cir.setReturnValue(this.wfcore$fluidTank.getFluidAmount() * WFCoreConfig.getEnergyToFluidRatio());
        }
    }

    @Inject(method = "getMaxEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$getMaxFluidAsEnergy(CallbackInfoReturnable<Integer> cir) {
        if (this.wfcore$usesFluidFuel && this.wfcore$fluidTank != null) {
            cir.setReturnValue(this.wfcore$fluidTank.getCapacity() * WFCoreConfig.getEnergyToFluidRatio());
        }
    }

    @Inject(method = "consumeEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$consumeFluidAsEnergy(int amount, CallbackInfo ci) {
        if (this.wfcore$usesFluidFuel && this.wfcore$fluidTank != null) {
            FluidStack currentFluid = this.wfcore$fluidTank.getFluid();

            if (!currentFluid.isEmpty()) {
                String id = this.wfcore$vehicleId;
                float efficiency = 1.0f;

                var override = id == null ? null : SuperbFuelOverride.getOverride(id);
                if (override != null) {
                    efficiency = override.fluidConsumptionMap().getOrDefault(currentFluid.getFluid(), 1.0f);
                }

                double effectiveRatio = WFCoreConfig.getEnergyToFluidRatio() * Math.max(0.0001d, efficiency);
                int mbToDrain = (int) Math.ceil(amount / effectiveRatio);

                if (mbToDrain > 0) {
                    this.wfcore$fluidTank.drain(mbToDrain, IFluidHandler.FluidAction.EXECUTE);
                }
            }
            ci.cancel();
        }
    }

    @Inject(method = "setEnergy", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$setFluidAsEnergy(int amount, CallbackInfo ci) {
        if (this.wfcore$usesFluidFuel && this.wfcore$fluidTank != null) {
            int targetMb = amount / WFCoreConfig.getEnergyToFluidRatio();
            int currentMb = this.wfcore$fluidTank.getFluidAmount();
            FluidStack currentFluid = this.wfcore$fluidTank.getFluid();

            if (targetMb > currentMb && !currentFluid.isEmpty()) {
                FluidStack refill = currentFluid.copy();
                refill.setAmount(targetMb - currentMb);
                this.wfcore$fluidTank.fill(refill, IFluidHandler.FluidAction.EXECUTE);
            } else if (targetMb < currentMb) {
                this.wfcore$fluidTank.drain(currentMb - targetMb, IFluidHandler.FluidAction.EXECUTE);
            }
            ci.cancel();
        }
    }

    @Unique
    @Nullable
    private String wfcore$getVehicleId() {
        var data = computed();
        return data == null ? null : data.getId();
    }
}
