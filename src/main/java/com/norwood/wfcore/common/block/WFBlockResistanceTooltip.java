package com.norwood.wfcore.common.block;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/** Shows a golden blast-resistance tooltip on any block item whose resistance exceeds 30, override or vanilla. */
public final class WFBlockResistanceTooltip {

    private static final float TOOLTIP_THRESHOLD = 30f;

    @SubscribeEvent
    public void onItemTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof BlockItem blockItem)) return;

        Block block = blockItem.getBlock();
        float resistance = block.getExplosionResistance();
        if (resistance <= TOOLTIP_THRESHOLD) return;

        event.getToolTip().add(Component.translatable("wfcore.tooltip.blast_resistance",
                trimmed(resistance)).withStyle(ChatFormatting.GOLD));
    }

    private static String trimmed(float value) {
        return value == Math.floor(value) ? String.valueOf((int) value) : String.valueOf(value);
    }
}
