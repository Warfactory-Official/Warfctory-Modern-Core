package com.norwood.wfcore.mixin;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;

import com.atsuishio.superbwarfare.data.vehicle.DefaultVehicleData;
import com.atsuishio.superbwarfare.entity.vehicle.base.VehicleEntity;
import com.norwood.wfcore.IVehicleFuelTank;
import com.norwood.wfcore.IWFCoreVehicleUI;
import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.capabilities.SyncedEntityFuelStorage;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.gui.VehicleStorageUI;
import com.norwood.wfcore.gui.VehicleUIFactory;
import com.norwood.wfcore.handlers.WFCoreFuelHandler;
import com.norwood.wfcore.serializer.WFCoreSerializers;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * WFCore fuel + storage overrides for Superb Warfare vehicles.
 *
 * <p>
 * As of Superb Warfare 0.8.9, {@code VehicleEntity} no longer implements {@link net.minecraft.world.Container} or
 * {@link net.minecraft.world.MenuProvider}: its storage is a {@code VehicleContainerHandler} item-handler capability
 * and it builds its own per-size container menus in {@code createMenu}/{@code openMenu}. So this mixin no longer
 * declares those interfaces or overwrites {@code createMenu}; instead it intercepts {@code openMenu} to redirect
 * configured vehicles to the WFCore ModularUI, and adjusts {@code getContainerSize} for configured storage sizes.
 */
@Mixin(value = VehicleEntity.class)
public abstract class SuperbWarfareInvMixin extends Entity implements IVehicleFuelTank, IUIHolder, IWFCoreVehicleUI {

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
    @Unique
    protected boolean wfcore$usesModularStorage;
    @Unique
    protected int wfcore$uiSlots = -1;
    @Unique
    protected int wfcore$uiCols = -1;
    // Carries the sub-millibucket remainder of fuel-per-tick between consumeEnergy calls (see below).
    @Unique
    private double wfcore$drainRemainder;

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

    // getContainerSize is Superb Warfare's own method (not net.minecraft.world.Container's) in 0.8.9, so it is
    // shadowed with remap = false and matched by its literal name.
    @Shadow(remap = false)
    public abstract int getContainerSize();

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
        var override = this.wfcore$vehicleId == null ? null : SuperbOverrides.getOverride(this.wfcore$vehicleId);
        this.wfcore$usesModularStorage = override != null && override.hasStorageOverride();

        if (override == null || !override.hasFuelOverride()) {
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
     * Give configured vehicles the WFCore-defined storage size instead of Superb Warfare's fixed
     * per-container-type size. {@code getContainerSize} drives {@code resizeItems()}, so a larger value grows the
     * backing item handler and the WFCore ModularUI renders that many slots. Unconfigured vehicles fall through to
     * the vanilla-sized container.
     *
     * <p>
     * The override is read by id from {@code computed()} directly (not the cached {@link #wfcore$vehicleId}, which
     * is still null when {@code getContainerSize} is called during early construction, before the {@code <init>}
     * tail sets it).
     */
    @Inject(method = "getContainerSize", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$overrideContainerSize(CallbackInfoReturnable<Integer> cir) {
        var id = wfcore$typeId();
        if (id == null) {
            return;
        }
        var override = SuperbOverrides.getOverride(id);
        if (override != null && override.hasStorageOverride()) {
            cir.setReturnValue(override.storageSize());
        }
    }


    @Inject(method = "hasMenu", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$overrideHasMenu(CallbackInfoReturnable<Boolean> cir) {
        if (wfcore$hasStorageOverride()) {
            cir.setReturnValue(true);
        }
    }

    /** True when this vehicle's entity-type id carries a WFCore storage override. */
    @Unique
    private boolean wfcore$hasStorageOverride() {
        var id = wfcore$typeId();
        var override = id == null ? null : SuperbOverrides.getOverride(id);
        return override != null && override.hasStorageOverride();
    }

    /
    @Unique
    @Nullable
    private String wfcore$typeId() {
        var key = ForgeRegistries.ENTITY_TYPES.getKey(this.getType());
        return key == null ? null : key.toString();
    }


    @Redirect(
              method = "baseTick",
              at = @At(value = "INVOKE",
                       target = "Lcom/atsuishio/superbwarfare/entity/vehicle/base/VehicleEntity;hasEnergyStorage()Z",
                       remap = false))
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

            if (!currentFluid.isEmpty() && amount > 0) {
                String id = this.wfcore$vehicleId;
                float efficiency = 1.0f;

                var override = id == null ? null : SuperbOverrides.getOverride(id);
                if (override != null) {
                    efficiency = override.fluidConsumptionMap().getOrDefault(currentFluid.getFluid(), 1.0f);
                }

                double effectiveRatio = WFCoreConfig.getEnergyToFluidRatio() * Math.max(0.0001d, efficiency);

                // Superb Warfare calls consumeEnergy() every tick with a small energy cost. Rounding each call
                // up with ceil() forced a minimum of 1 mB drained per tick (20 mB/s), emptying the tank in
                // seconds no matter the real cost. Convert to a fractional mB and carry the sub-millibucket
                // remainder across ticks so the drain tracks actual energy consumption.
                this.wfcore$drainRemainder += amount / effectiveRatio;
                int mbToDrain = (int) this.wfcore$drainRemainder;

                if (mbToDrain > 0) {
                    this.wfcore$drainRemainder -= mbToDrain;
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
        return wfcore$typeId();
    }

    // ----- WFCore ModularUI storage (only for vehicles with a configured storageSize) -----

    /**
     * For configured vehicles, open the resizable WFCore ModularUI instead of Superb Warfare's native container
     * menu. Superb Warfare funnels every inventory-open path ({@code openCustomInventoryScreen} and {@code interact})
     * through {@code openMenu}, so intercepting it here covers them all. Unconfigured vehicles fall through to the
     * native menu.
     */
    @Inject(method = "openMenu", at = @At("HEAD"), cancellable = true, remap = false)
    private void wfcore$openVehicleStorageUI(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && wfcore$hasStorageOverride()) {
            VehicleUIFactory.INSTANCE.openUI((VehicleEntity) (Object) this, serverPlayer);
            ci.cancel();
        }
    }

    @Override
    public ModularUI createUI(Player entityPlayer) {
        boolean client = this.level().isClientSide;
        int slots = (client && this.wfcore$uiSlots >= 0) ? this.wfcore$uiSlots : this.getContainerSize();
        int cols = this.wfcore$uiColumns();
        return VehicleStorageUI.build((VehicleEntity) (Object) this, entityPlayer, slots, cols);
    }

    @Override
    public boolean isInvalid() {
        return this.isRemoved();
    }

    @Override
    public boolean isRemote() {
        return this.level().isClientSide;
    }

    @Override
    public void markAsDirty() {
        // Entity item data is persisted on world/chunk save via addAdditionalSaveData; no per-change dirtying needed.
    }

    @Override
    public void wfcore$setSyncedUiSize(int slots, int cols) {
        this.wfcore$uiSlots = slots;
        this.wfcore$uiCols = cols;
    }

    @Override
    public int wfcore$uiColumns() {
        if (this.level().isClientSide && this.wfcore$uiCols > 0) {
            return this.wfcore$uiCols;
        }
        var override = this.wfcore$vehicleId == null ? null : SuperbOverrides.getOverride(this.wfcore$vehicleId);
        return override != null ? override.columnsOrDefault() : 9;
    }
}
