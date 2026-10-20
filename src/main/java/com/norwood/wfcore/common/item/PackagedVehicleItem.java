package com.norwood.wfcore.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import com.norwood.wfcore.common.data.WFDataComponents;
import com.norwood.wfcore.common.data.WFItems;
import com.norwood.wfcore.integration.combinedassault.VehicleGate;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The "output" of a vehicle factory recipe: a marker item carrying the entity id of the vehicle to spawn.
 * Keeping the vehicle in the recipe's item output is what makes the recipe scriptable via
 * KubeJS/GroovyScript. The factory machine consumes this item and spawns the entity instead of ever handing
 * it to the player.
 *
 * <p>
 * The id used to live in free-form item NBT under an {@code entity} key; 1.20.5 replaced that with typed
 * components, so it is now {@link WFDataComponents#PACKAGED_VEHICLE}.
 */
public class PackagedVehicleItem extends Item {

    public PackagedVehicleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceLocation vehicleId) {
        ItemStack stack = new ItemStack(WFItems.PACKAGED_VEHICLE.get());
        stack.set(WFDataComponents.PACKAGED_VEHICLE.get(), vehicleId);
        return stack;
    }

    @Nullable
    public static ResourceLocation getVehicleId(ItemStack stack) {
        return stack.get(WFDataComponents.PACKAGED_VEHICLE.get());
    }

    @Override
    public Component getName(ItemStack stack) {
        Component name = VehicleGate.name(getVehicleId(stack));
        return name != null ? name : super.getName(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext level, List<Component> tooltip,
                                TooltipFlag flag) {
        tooltip.add(Component.literal("Packaged vehicle — deploy to spawn it").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Produced by a vehicle factory").withStyle(ChatFormatting.DARK_GRAY));
    }

}
