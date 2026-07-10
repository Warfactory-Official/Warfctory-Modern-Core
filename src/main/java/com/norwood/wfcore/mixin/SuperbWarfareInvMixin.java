package com.norwood.wfcore.mixin;

import com.lowdragmc.lowdraglib.gui.modular.IUIHolder;
import com.lowdragmc.lowdraglib.gui.modular.ModularUI;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
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
import com.norwood.wfcore.IWFCoreVehicleUI;
import com.norwood.wfcore.SuperbOverrides;
import com.norwood.wfcore.capabilities.SyncedEntityFuelStorage;
import com.norwood.wfcore.config.WFCoreConfig;
import com.norwood.wfcore.gui.VehicleStorageUI;
import com.norwood.wfcore.gui.VehicleUIFactory;
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

import java.util.OptionalInt;

// Container and MenuProvider are declared here (the target VehicleEntity already implements both)
// so the Mixin annotation processor can resolve and remap the getContainerSize/createMenu injectors.
@Mixin(value = VehicleEntity.class)
public abstract class SuperbWarfareInvMixin extends Entity
                                            implements IVehicleFuelTank, Container, MenuProvider, IUIHolder,
                                            IWFCoreVehicleUI {

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
     * Replace the hardcoded container size with the configured vehicle container size.
     *
     * <p>
     * {@code remap = false} keeps the annotation processor from trying to remap the method (it
     * cannot, because the method is inherited from the {@link Container} interface rather than the
     * mixin's superclass). The mixin declares {@code implements Container} so that the build's
     * reobfuscation step renames this declaration to its SRG name for production, while the named
     * declaration matches the named dev runtime directly.
     *
     * @author MrNorwood
     * @reason Removing hardcoded limits
     */
    @Overwrite(remap = false)
    public int getContainerSize() {
        var data = computed();
        if (data == null) return 0;

        // Read the override by id directly (not the cached wfcore$vehicleId, which is still null when the
        // `items` field initializer first calls this during construction).
        var override = SuperbOverrides.getOverride(data.getId());
        if (override != null && override.hasStorageOverride()) {
            return override.storageSize();
        }

        var type = data.vehicleContainerType;
        return type == null ? 0 : type.getSize();
    }

    /**
     * Restore the vehicle inventory menu (commented out upstream).
     *
     * <p>
     * {@code remap = false} for the same reason as {@link #getContainerSize()}: {@code createMenu}
     * is inherited from {@link MenuProvider}, so remapping is handled by the build's reobfuscation
     * step (driven by the {@code implements MenuProvider} declaration) rather than the refmap.
     *
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

    // method = "baseTick" is the vanilla Entity#baseTick and must be remapped (default remap = true);
    // only the @At target (superbwarfare's hasEnergyStorage) keeps remap = false.
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
        var data = computed();
        return data == null ? null : data.getId();
    }

    // ----- WFCore ModularUI storage (only for vehicles with a configured storageSize) -----

    /**
     * For configured vehicles, open the resizable WFCore ModularUI instead of Superb Warfare's fixed-bucket menu.
     * Redirects the {@code player.openMenu(this)} calls in {@code openCustomInventoryScreen} and {@code interact}.
     * Unconfigured vehicles fall through to the vanilla {@code openMenu} path (native SBW menu, unchanged).
     */
    @Redirect(
              method = { "openCustomInventoryScreen", "interact" },
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/world/entity/player/Player;openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;"))
    private OptionalInt wfcore$openVehicleStorageUI(Player player, MenuProvider provider) {
        if (this.wfcore$usesModularStorage) {
            if (player instanceof ServerPlayer serverPlayer) {
                VehicleUIFactory.INSTANCE.openUI((VehicleEntity) (Object) this, serverPlayer);
            }
            return OptionalInt.empty();
        }
        return player.openMenu(provider);
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
