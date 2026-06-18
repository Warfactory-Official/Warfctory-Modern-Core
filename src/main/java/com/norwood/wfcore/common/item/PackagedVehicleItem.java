package com.norwood.wfcore.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The "output" of a vehicle factory recipe: a marker item whose NBT carries the entity id of the
 * vehicle to spawn (e.g. {@code superbwarfare:lav_150}). Keeping the vehicle in the recipe's item
 * output is what makes the recipe scriptable via KubeJS/GroovyScript. The factory machine consumes
 * this item and spawns the entity instead of ever handing it to the player.
 */
public class PackagedVehicleItem extends Item {

    public static final String ENTITY_TAG = "entity";

    public PackagedVehicleItem(Properties properties) {
        super(properties);
    }

    public static ItemStack of(ResourceLocation entityId) {
        ItemStack stack = new ItemStack(com.norwood.wfcore.common.data.WFItems.PACKAGED_VEHICLE.get());
        stack.getOrCreateTag().putString(ENTITY_TAG, entityId.toString());
        return stack;
    }

    @Nullable
    public static ResourceLocation getEntityId(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains(ENTITY_TAG)) {
            return null;
        }
        return ResourceLocation.tryParse(tag.getString(ENTITY_TAG));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        ResourceLocation id = getEntityId(stack);
        tooltip.add(Component.literal("Vehicle: " + (id == null ? "<none>" : id)).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.literal("Produced by a vehicle factory").withStyle(ChatFormatting.DARK_GRAY));
    }
}
