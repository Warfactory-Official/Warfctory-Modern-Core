package com.norwood.wfcore.serializer;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.syncher.EntityDataSerializer;
import net.minecraftforge.fluids.FluidStack;

import org.jetbrains.annotations.NotNull;

public class WFCoreSerializers {

    public static final EntityDataSerializer<FluidStack> FLUID_STACK_ENTITY_DATA_SERIALIZER = new EntityDataSerializer<>() {

        @Override
        public void write(FriendlyByteBuf friendlyByteBuf, FluidStack fluidStack) {
            friendlyByteBuf.writeFluidStack(fluidStack);
        }

        @Override
        public @NotNull FluidStack read(FriendlyByteBuf buf) {
            return buf.readFluidStack();
        }

        @Override
        public @NotNull FluidStack copy(FluidStack fluidStack) {
            return fluidStack.copy();
        }
    };
}
