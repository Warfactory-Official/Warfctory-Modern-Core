package com.norwood.wfcore.integration.wflib;

import com.gregtechceu.gtceu.api.data.chemical.material.properties.HazardProperty;
import com.wf.wflib.core.api.WFSystems;
import com.wf.wflib.labs.api.LabShelter;
import com.wf.wflib.labs.api.LabShelters;
import com.wf.wflib.mist.api.MistEntity;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.item.HazmatArmorStats;

import java.util.ArrayList;
import java.util.List;

/** A full GregTech PPE set makes wflib gas clouds pass over the wearer. */
public final class GasProtection {
    private GasProtection() {}

    public static void install() {
        MistEntity.IMMUNITY.register(WFCore.id("gt_ppe"), 0,
                e -> e instanceof Player p && HazardProperty.ProtectionType.FULL.isProtected(p));
        if (WFSystems.enabled("labs")) {
            LabShelters.register(new FullHazmat());
        }
    }

    /** Occupant dressed in the four hazmat pieces, then asked {@link MistEntity#isAffectable}. */
    private static final class FullHazmat implements LabShelter {
        @Override
        public String id() {
            return WFCore.id("gt_hazmat").toString();
        }

        @Override
        public double shield(LivingEntity occupant, String kind, double amount) {
            if (!"gas".equals(kind)) {
                return amount;
            }
            List<EquipmentSlot> slots = new ArrayList<>();
            List<ItemStack> before = new ArrayList<>();
            for (String piece : HazmatArmorStats.HAZMAT_PIECES) {
                ArmorItem item = (ArmorItem) BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(piece))
                        .orElseThrow(() -> new IllegalStateException("no item " + piece));
                slots.add(item.getEquipmentSlot());
                before.add(occupant.getItemBySlot(item.getEquipmentSlot()));
                occupant.setItemSlot(item.getEquipmentSlot(), new ItemStack(item));
            }
            try {
                return MistEntity.isAffectable(occupant) ? amount : 0.0;
            } finally {
                for (int i = 0; i < slots.size(); i++) {
                    occupant.setItemSlot(slots.get(i), before.get(i));
                }
            }
        }
    }
}
