package com.norwood.wfcore.common.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.integration.warforge.WarforgeDefenseProbe;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Testing tool: right-click to print the WarForge siege difficulty of the chunk the player stands in. */
public class SiegeDefenseTesterItem extends Item {

    public SiegeDefenseTesterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            if (!WarforgeIntegration.isLoaded()) {
                player.displayClientMessage(
                        Component.translatable("wfcore.tool.siege_tester.no_warforge").withStyle(ChatFormatting.RED),
                        false);
            } else {
                for (Component line : WarforgeDefenseProbe.sample(serverLevel, player.blockPosition())) {
                    player.displayClientMessage(line, false);
                }
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide());
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("wfcore.tool.siege_tester.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
