package com.norwood.wfcore.common.compute;

import com.gregtechceu.gtceu.api.GTValues;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.norwood.wfcore.common.data.WFCovers;
import com.norwood.wfcore.common.data.WFItems;

/** Adds the 1.12.2 CPU/RAM stat tooltips so registered compute items identify themselves and show their stats. */
public final class WFComputeTooltips {

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();

        CPURegistry.CPUEntry cpu = CPURegistry.getEntry(stack);
        if (cpu != null) {
            var tooltip = event.getToolTip();
            tooltip.add(Component.translatable("wfcore.tooltip.cpu_stats").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("wfcore.tooltip.efficiency").append(": ")
                    .append(Component.literal(String.format("%.1f%%", cpu.efficiency() * 100))
                            .withStyle(ChatFormatting.GREEN))
                    .withStyle(ChatFormatting.GRAY));
            tooltip.add(Component.translatable("wfcore.tooltip.power_draw").append(": ")
                    .append(Component.literal(cpu.minPower() + " - " + cpu.maxPower() + " EU/t")
                            .withStyle(ChatFormatting.RED))
                    .withStyle(ChatFormatting.GRAY));
            if (cpu.efficiency() < 0.6) {
                tooltip.add(
                        Component.translatable("wfcore.tooltip.high_heat_warning").withStyle(ChatFormatting.DARK_RED));
            }
        }

        RAMRegistry.RAMEntry ram = RAMRegistry.getEntry(stack);
        if (ram != null) {
            var tooltip = event.getToolTip();
            tooltip.add(Component.translatable("wfcore.tooltip.ram_stats").withStyle(ChatFormatting.GOLD));
            tooltip.add(Component.translatable("wfcore.tooltip.throughput").append(": ")
                    .append(Component.literal(ram.throughput() + " CWU/t").withStyle(ChatFormatting.LIGHT_PURPLE))
                    .withStyle(ChatFormatting.GRAY));
        }

        for (int tier : WFCovers.FAN_TIERS) {
            var cover = WFItems.COOLING_FAN_COVERS[tier];
            if (cover != null && stack.is(cover.get())) {
                var tooltip = event.getToolTip();
                tooltip.add(Component.translatable("wfcore.tooltip.cooling_fan_cover.line1")
                        .withStyle(ChatFormatting.AQUA));
                tooltip.add(
                        Component.translatable("wfcore.tooltip.cooling_fan_cover.line2", tier + 1, GTValues.VN[tier])
                                .withStyle(ChatFormatting.GRAY));
                break;
            }
        }
    }
}
