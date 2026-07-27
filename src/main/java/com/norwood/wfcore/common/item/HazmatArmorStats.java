package com.norwood.wfcore.common.item;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import com.norwood.wfcore.WFCore;

import java.util.Set;
import java.util.UUID;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class HazmatArmorStats {

    private static final Set<String> HAZMAT_PIECES = Set.of(
            "gtceu:hazmat_headpiece", "gtceu:hazmat_chestpiece", "gtceu:hazmat_leggings", "gtceu:hazmat_boots");

    /** One stable modifier UUID per armor slot, so the four pieces stack instead of overwriting each other. */
    private static final UUID HEAD_UUID = UUID.fromString("d1f5a9c2-1b3e-4a6d-8c7f-0a1b2c3d4e5f");
    private static final UUID CHEST_UUID = UUID.fromString("e2a6b0d3-2c4f-5b7e-9d80-1b2c3d4e5f60");
    private static final UUID LEGS_UUID = UUID.fromString("f3b7c1e4-3d50-6c8f-ae91-2c3d4e5f6071");
    private static final UUID FEET_UUID = UUID.fromString("a4c8d2f5-4e61-7d90-bfa2-3d4e5f607182");

    private HazmatArmorStats() {}

    @SubscribeEvent
    public static void onArmorAttributes(ItemAttributeModifierEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ArmorItem armor)) {
            return;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(armor);
        if (id == null || !HAZMAT_PIECES.contains(id.toString())) {
            return;
        }
        EquipmentSlot slot = armor.getEquipmentSlot();
        if (event.getSlotType() != slot) {
            return;
        }
        UUID uuid = switch (slot) {
            case HEAD -> HEAD_UUID;
            case CHEST -> CHEST_UUID;
            case LEGS -> LEGS_UUID;
            case FEET -> FEET_UUID;
            default -> null;
        };
        if (uuid == null) {
            return;
        }
        // Replace whatever armor value the suit shipped with a flat 1 per piece (4 total across the set).
        event.removeAttribute(Attributes.ARMOR);
        event.addModifier(Attributes.ARMOR,
                new AttributeModifier(uuid, "wfcore hazmat armor", 1.0D, AttributeModifier.Operation.ADDITION));
    }
}
