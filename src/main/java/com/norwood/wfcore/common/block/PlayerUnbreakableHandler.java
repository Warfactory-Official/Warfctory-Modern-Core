package com.norwood.wfcore.common.block;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import com.norwood.wfcore.common.data.WFTags;

/**
 * {@link WFTags#PLAYER_UNBREAKABLE}: hardness stays positive so explosions, mining charges and machines still break
 * the block; only real survival players are refused. BreakEvent covers instant paths (area tools, vein miners).
 */
public final class PlayerUnbreakableHandler {

    public static final PlayerUnbreakableHandler INSTANCE = new PlayerUnbreakableHandler();

    private PlayerUnbreakableHandler() {}

    private static boolean refused(Player player, BlockState state) {
        return state.is(WFTags.PLAYER_UNBREAKABLE) && !(player instanceof FakePlayer)
                && !player.getAbilities().instabuild;
    }

    @SubscribeEvent
    public void onBreakSpeed(PlayerEvent.BreakSpeed event) {
        if (refused(event.getEntity(), event.getState())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBreak(BlockEvent.BreakEvent event) {
        if (refused(event.getPlayer(), event.getState())) {
            event.setCanceled(true);
        }
    }
}
