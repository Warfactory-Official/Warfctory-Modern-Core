package com.norwood.wfcore.common.item.behavior;

import com.gregtechceu.gtceu.api.blockentity.IPaintable;
import com.gregtechceu.gtceu.api.blockentity.PipeBlockEntity;
import com.gregtechceu.gtceu.api.capability.ICoverable;
import com.gregtechceu.gtceu.api.item.ComponentItem;
import com.gregtechceu.gtceu.api.item.IGTTool;
import com.gregtechceu.gtceu.api.item.component.IItemComponent;
import com.gregtechceu.gtceu.api.item.tool.GTToolType;
import com.gregtechceu.gtceu.api.item.tool.ToolHelper;
import com.gregtechceu.gtceu.common.data.GTItems;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;
import com.gregtechceu.gtceu.common.item.ColorSprayBehaviour;
import com.gregtechceu.gtceu.utils.input.SyncedKeyMappings;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import org.jetbrains.annotations.Nullable;

import java.util.Set;


@Mod.EventBusSubscriber(modid = WFCore.MOD_ID)
public final class PipeNetWalkerBehavior {

    private static final String DYE_SPRAY_SUFFIX = "_dye_spray_can";
    private static final String SOLVENT_SPRAY = "solvent_spray_can";

    private PipeNetWalkerBehavior() {}

    @SubscribeEvent
    public static void onRightClickPipe(PlayerInteractEvent.RightClickBlock event) {
        Player player = event.getEntity();
        if (!SyncedKeyMappings.TOOL_AOE_CHANGE.isKeyDown(player)) return;

        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        if (!(level.getBlockEntity(pos) instanceof PipeBlockEntity<?, ?> pipe)) return;

        Direction gridSide = ICoverable.determineGridSideHit(event.getHitVec());
        if (gridSide == null) gridSide = event.getHitVec().getDirection();

        ItemStack stack = event.getItemStack();

        ColorSprayBehaviour spray = getSprayBehaviour(stack);
        if (spray != null) {
            handleColoring(event, player, level, pos, pipe, gridSide, stack, spray);
            return;
        }

        handlePipeTuning(event, player, level, pos, pipe, gridSide, stack);
    }



    private static void handleColoring(PlayerInteractEvent.RightClickBlock event, Player player, Level level,
                                       BlockPos pos, PipeBlockEntity<?, ?> pipe, Direction gridSide, ItemStack stack,
                                       ColorSprayBehaviour spray) {
        Integer color = sprayColor(stack);
        if (color == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (level.isClientSide) return;

        int budget = spray.getUsesLeft(stack);
        if (budget <= 0) return;

        if (pipe.getPaintingColor() != color) {
            pipe.setPaintingColor(color);
        }

        int walked = PipeOperationWalker.collectPipeNet(level, pos, pipe, gridSide, TraverseOptions.coloring(color),
                budget);

        InteractionHand hand = event.getHand();
        int uses = Math.max(1, walked);
        for (int i = 0; i < uses; i++) {
            if (!spray.useItemDurability(player, hand, stack, GTItems.SPRAY_EMPTY.asStack())) break;
        }
        GTSoundEntries.SPRAY_CAN_TOOL.play(level, null, player.position(), 1.0f, 1.0f);
        player.swing(hand);
    }

    @Nullable
    private static ColorSprayBehaviour getSprayBehaviour(ItemStack stack) {
        if (stack.getItem() instanceof ComponentItem componentItem) {
            for (IItemComponent component : componentItem.getComponents()) {
                if (component instanceof ColorSprayBehaviour spray) {
                    return spray;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Integer sprayColor(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.equals(SOLVENT_SPRAY)) {
            return IPaintable.UNPAINTED_COLOR;
        }
        if (path.endsWith(DYE_SPRAY_SUFFIX)) {
            DyeColor dye = DyeColor.byName(path.substring(0, path.length() - DYE_SPRAY_SUFFIX.length()), null);
            if (dye != null) return dye.getMapColor().col;
        }
        return null;
    }

    private static void handlePipeTuning(PlayerInteractEvent.RightClickBlock event, Player player, Level level,
                                         BlockPos pos, PipeBlockEntity<?, ?> pipe, Direction gridSide, ItemStack stack) {
        Set<GTToolType> toolTypes = ToolHelper.getToolTypes(stack);
        if (toolTypes.isEmpty() || !toolTypes.contains(pipe.getPipeTuneTool())) return;

        TraverseOptions option = selectOperation(pipe, gridSide, player.isShiftKeyDown());
        if (option == null) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.CONSUME);
        if (level.isClientSide) return;

        CompoundTag toolTag = ToolHelper.getToolTag(stack);
        int maxWalks = toolTag.getInt(ToolHelper.MAX_DURABILITY_KEY) - toolTag.getInt(ToolHelper.DURABILITY_KEY);
        if (maxWalks <= 0) return;

        int walked = PipeOperationWalker.collectPipeNet(level, pos, pipe, gridSide, option, maxWalks);

        onActionDone(stack, player, level, event.getHand(), Mth.ceil(Math.sqrt(walked)));
    }

    @Nullable
    private static TraverseOptions selectOperation(PipeBlockEntity<?, ?> pipe, Direction gridSide, boolean sneaking) {
        if (pipe.isConnected(gridSide)) {
            if (sneaking) {
                if (!pipe.canHaveBlockedFaces()) return null;
                return pipe.isBlocked(gridSide) ? TraverseOptions.UNBLOCKING : TraverseOptions.BLOCKING;
            }
            return TraverseOptions.DISCONNECTING;
        }
        return sneaking ? null : TraverseOptions.CONNECTING;
    }

    private static void onActionDone(ItemStack stack, Player player, Level level, InteractionHand hand, int walked) {
        ToolHelper.damageItem(stack, player, walked);
        if (stack.getItem() instanceof IGTTool tool && tool.getSound() != null) {
            tool.getSound().playOnServer(level, player.blockPosition());
        }
        player.swing(hand);
    }
}
