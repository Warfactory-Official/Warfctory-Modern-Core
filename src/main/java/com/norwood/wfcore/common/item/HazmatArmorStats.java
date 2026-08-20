package com.norwood.wfcore.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.event.ItemAttributeModifierEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.minecraft.core.registries.BuiltInRegistries;

import com.norwood.wfcore.WFCore;

import java.util.Set;


@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class HazmatArmorStats {

    private static final Set<String> HAZMAT_PIECES = Set.of(
            "gtceu:hazmat_headpiece", "gtceu:hazmat_chestpiece", "gtceu:hazmat_leggings", "gtceu:hazmat_boots");

    /** One stable modifier UUID per armor slot, so the four pieces stack instead of overwriting each other. */

    private HazmatArmorStats() {}

    @SubscribeEvent
    public static void onArmorAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(armor);
        if (id == null || !HAZMAT_PIECES.contains(id.toString())) {
            return;
        }
        // 1.21 replaced the per-slot event with one carrying every slot's modifiers, so the slot is
        // expressed as the EquipmentSlotGroup on each modifier rather than filtered for up front. Modifier
        // identity also moved from a UUID to a ResourceLocation, which lets the id say what it is.
        EquipmentSlot slot = armor.getEquipmentSlot();
        ResourceLocation modifierId = WFCore.id("hazmat_armor/" + slot.getName());

        // Replace whatever armor value the suit shipped with a flat 1 per piece (4 total across the set).
        event.removeAllModifiersFor(Attributes.ARMOR);
        event.addModifier(Attributes.ARMOR,
                new AttributeModifier(modifierId, 1.0D, AttributeModifier.Operation.ADD_VALUE),
                EquipmentSlotGroup.bySlot(slot));
    }
}
