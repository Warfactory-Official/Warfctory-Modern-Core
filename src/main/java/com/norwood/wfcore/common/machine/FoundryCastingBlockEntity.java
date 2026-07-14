package com.norwood.wfcore.common.machine;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.chemical.material.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

import com.norwood.wfcore.common.block.FoundryCastingBlock;
import com.norwood.wfcore.common.data.FoundryMolds;
import com.norwood.wfcore.common.data.WFMaterials;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Shared block entity of the Foundry Casting Basin and Foundry Mold Caster (ported from HBM's
 * {@code TileEntityFoundryCastingBase}). Holds one GregTech casting mold (slot {@link #SLOT_MOLD}), one
 * finished casting (slot {@link #SLOT_OUTPUT}) and a fill-only molten-metal tank.
 * <p>
 * Molten metal arrives as GT material fluids through the Forge fluid capability (fire-clay pipes push it in).
 * A pour is accepted only while a matching-size mold is installed, the output slot is empty, and the fluid's
 * material actually has an item of the mold's shape — and the amount needed is GT's own per-shape material
 * amount, so the tank's capacity depends on both the mold and the metal (see {@link FoundryMolds.Mold}).
 * Once full, a {@link #COOLOFF_TICKS} cool-off runs and the casting lands in the output slot. Which mold size
 * fits comes from the owning {@link FoundryCastingBlock}, so one BE type serves both blocks.
 */
public class FoundryCastingBlockEntity extends BlockEntity {

    public static final int SLOT_MOLD = 0;
    public static final int SLOT_OUTPUT = 1;
    /** Ticks the full melt takes to solidify (HBM parity: 10 seconds). */
    public static final int COOLOFF_TICKS = 200;

    private final ItemStackHandler inventory = new ItemStackHandler(2) {

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (slot == SLOT_MOLD) {
                FoundryMolds.Mold mold = FoundryMolds.get(stack);
                return mold != null && mold.size() == getMoldSize();
            }
            return false; // output slot is filled by casting only
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        protected void onContentsChanged(int slot) {
            sync();
        }
    };

    private FluidStack stored = FluidStack.EMPTY;
    private int cooloff = COOLOFF_TICKS;

    private final IFluidHandler tank = new IFluidHandler() {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tankIdx) {
            return stored;
        }

        @Override
        public int getTankCapacity(int tankIdx) {
            return getCapacity();
        }

        @Override
        public boolean isFluidValid(int tankIdx, @NotNull FluidStack stack) {
            return costOf(stack) > 0;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            int cost = costOf(resource);
            if (cost <= 0) {
                return 0;
            }
            int filled = Math.min(cost - stored.getAmount(), resource.getAmount());
            if (filled <= 0) {
                return 0;
            }
            if (action.execute()) {
                if (stored.isEmpty()) {
                    stored = new FluidStack(resource, filled);
                } else {
                    stored.grow(filled);
                }
                sync();
            }
            return filled;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY; // fill-only: the melt leaves as the cast item (HBM parity)
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    };

    private final LazyOptional<IFluidHandler> fluidCap = LazyOptional.of(() -> tank);
    private final LazyOptional<IItemHandler> itemCap;

    public FoundryCastingBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        this.itemCap = LazyOptional.of(ExternalItemView::new);
    }

    //////////////////// casting ////////////////////

    public static void serverTick(Level level, BlockPos pos, BlockState state, FoundryCastingBlockEntity be) {
        int capacity = be.getCapacity();
        if (capacity > 0 && be.stored.getAmount() >= capacity
                && be.inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            if (--be.cooloff <= 0) {
                FoundryMolds.Mold mold = FoundryMolds.get(be.inventory.getStackInSlot(SLOT_MOLD));
                Material material = ChemicalHelper.getMaterial(be.stored.getFluid());
                ItemStack out = mold == null ? ItemStack.EMPTY : mold.outputFor(material);
                if (!out.isEmpty()) {
                    be.inventory.setStackInSlot(SLOT_OUTPUT, out);
                }
                be.stored = FluidStack.EMPTY;
                be.cooloff = COOLOFF_TICKS;
                be.sync();
            }
        } else {
            be.cooloff = COOLOFF_TICKS;
        }
    }

    /**
     * The mB of {@code stack} one casting needs — i.e. the tank's capacity for that melt — or 0 if it can't
     * be poured right now (not molten metal, no/incompatible mold, output slot full, or a different metal is
     * already inside).
     */
    private int costOf(FluidStack stack) {
        if (stack.isEmpty() || !WFMaterials.isMoltenMetal(stack)) {
            return 0;
        }
        if (!inventory.getStackInSlot(SLOT_OUTPUT).isEmpty()) {
            return 0;
        }
        if (!stored.isEmpty() && !stored.isFluidEqual(stack)) {
            return 0;
        }
        FoundryMolds.Mold mold = FoundryMolds.get(inventory.getStackInSlot(SLOT_MOLD));
        if (mold == null) {
            return 0;
        }
        return mold.costFor(ChemicalHelper.getMaterial(stack.getFluid()));
    }

    //////////////////// accessors (block interactions + renderer) ////////////////////

    public int getMoldSize() {
        return getBlockState().getBlock() instanceof FoundryCastingBlock block ? block.getMoldSize() : -1;
    }

    /**
     * How much melt fills the installed mold: the exact per-material cost once a metal is inside, otherwise
     * the mold's nominal cost. It must be non-zero while empty — pushers work out their room as
     * {@code capacity - stored}, so advertising 0 would stop any pour from ever being offered.
     */
    public int getCapacity() {
        FoundryMolds.Mold mold = FoundryMolds.get(inventory.getStackInSlot(SLOT_MOLD));
        if (mold == null) {
            return 0;
        }
        return stored.isEmpty()
                ? mold.baseCost()
                : mold.costFor(ChemicalHelper.getMaterial(stored.getFluid()));
    }

    public ItemStack getMoldStack() {
        return inventory.getStackInSlot(SLOT_MOLD);
    }

    public ItemStack getOutputStack() {
        return inventory.getStackInSlot(SLOT_OUTPUT);
    }

    public FluidStack getStored() {
        return stored;
    }

    public boolean isTankEmpty() {
        return stored.isEmpty();
    }

    public void setMold(ItemStack stack) {
        inventory.setStackInSlot(SLOT_MOLD, stack);
    }

    /** Removes and returns the finished casting (or EMPTY). */
    public ItemStack takeOutput() {
        ItemStack out = inventory.getStackInSlot(SLOT_OUTPUT);
        if (!out.isEmpty()) {
            inventory.setStackInSlot(SLOT_OUTPUT, ItemStack.EMPTY);
        }
        return out;
    }

    /** Marks dirty and pushes the new state to watching clients (the BER renders live contents). */
    private void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    //////////////////// capabilities ////////////////////

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluidCap.cast();
        }
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        fluidCap.invalidate();
        itemCap.invalidate();
    }

    /** Automation view: insert molds (slot 0), extract castings (slot 1) — never extract the mold. */
    private final class ExternalItemView implements IItemHandler {

        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return inventory.getStackInSlot(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return slot == SLOT_MOLD ? inventory.insertItem(slot, stack, simulate) : stack;
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            return slot == SLOT_OUTPUT ? inventory.extractItem(slot, amount, simulate) : ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return inventory.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == SLOT_MOLD && inventory.isItemValid(slot, stack);
        }
    }

    //////////////////// persistence + client sync (DepositBlockEntity pattern) ////////////////////

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", inventory.serializeNBT());
        tag.put("Fluid", stored.writeToNBT(new CompoundTag()));
        tag.putInt("Cooloff", cooloff);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        inventory.deserializeNBT(tag.getCompound("Inventory"));
        stored = FluidStack.loadFluidStackFromNBT(tag.getCompound("Fluid"));
        cooloff = tag.contains("Cooloff") ? tag.getInt("Cooloff") : COOLOFF_TICKS;
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket pkt) {
        CompoundTag tag = pkt.getTag();
        if (tag != null) {
            load(tag);
        }
    }
}
