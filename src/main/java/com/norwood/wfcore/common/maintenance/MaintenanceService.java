package com.norwood.wfcore.common.maintenance;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * Maintenance mode. While active only server operators may be connected: a non-operator is kicked the
 * moment it finishes logging in, and every non-operator already online is kicked when an operator switches
 * it on. Toggle it live with {@code /wfcore maintenance on|off|status} (op level 2). The on/off state is
 * written back to {@code wfcore.toml} (see {@link WFCoreConfig#setMaintenanceEnabled(boolean)}) so the lock
 * survives a restart - the server stays locked until an operator turns it off.
 */
public final class MaintenanceService {

    public static final MaintenanceService INSTANCE = new MaintenanceService();

    private MaintenanceService() {}

    // ---------------------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------------------

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore")
                .then(Commands.literal("maintenance")
                        .requires(source -> source.hasPermission(2))
                        .executes(this::commandStatus)
                        .then(Commands.literal("status").executes(this::commandStatus))
                        .then(Commands.literal("on").executes(ctx -> commandSet(ctx, true)))
                        .then(Commands.literal("off").executes(ctx -> commandSet(ctx, false)))));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (!WFCoreConfig.isMaintenanceEnabled()) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isOperator(player)) {
            player.sendSystemMessage(Component.literal("[wfcore] Maintenance mode is ON - only operators can join.")
                    .withStyle(ChatFormatting.YELLOW));
            return;
        }
        player.connection.disconnect(maintenanceKickMessage());
    }

    // ---------------------------------------------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------------------------------------------

    private int commandStatus(CommandContext<CommandSourceStack> ctx) {
        boolean on = WFCoreConfig.isMaintenanceEnabled();
        ctx.getSource().sendSuccess(() -> Component.literal("Maintenance mode is " + (on ? "ON" : "OFF") + ".")
                .withStyle(on ? ChatFormatting.RED : ChatFormatting.GREEN), false);
        return on ? 1 : 0;
    }

    private int commandSet(CommandContext<CommandSourceStack> ctx, boolean enable) {
        CommandSourceStack source = ctx.getSource();
        if (WFCoreConfig.isMaintenanceEnabled() == enable) {
            source.sendSuccess(
                    () -> Component.literal("Maintenance mode is already " + (enable ? "ON" : "OFF") + "."), false);
            return 0;
        }

        WFCoreConfig.setMaintenanceEnabled(enable);

        MinecraftServer server = source.getServer();
        int kicked = enable ? kickNonOperators(server) : 0;

        source.sendSuccess(() -> Component.literal("Maintenance mode is now " + (enable ? "ON" : "OFF")
                + (enable ? " (kicked " + kicked + " non-operator player(s))." : "."))
                .withStyle(enable ? ChatFormatting.RED : ChatFormatting.GREEN), true);

        announce(server, enable, source);
        WFCore.LOGGER.info("[wfcore-maintenance] {} by {}{}",
                enable ? "enabled" : "disabled",
                source.getTextName(),
                enable ? " (kicked " + kicked + " non-op player(s))" : "");
        return 1;
    }

    // ---------------------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------------------

    /** Kick every currently-online non-operator. Returns the number kicked. */
    private int kickNonOperators(MinecraftServer server) {
        if (server == null) {
            return 0;
        }
        Component reason = maintenanceKickMessage();
        // Snapshot first: disconnecting mutates the live player list we would otherwise be iterating.
        List<ServerPlayer> toKick = new ArrayList<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!isOperator(player)) {
                toKick.add(player);
            }
        }
        for (ServerPlayer player : toKick) {
            player.connection.disconnect(reason);
        }
        return toKick.size();
    }

    /** Tell online operators that maintenance was toggled, and who did it. */
    private void announce(MinecraftServer server, boolean enable, CommandSourceStack source) {
        if (server == null) {
            return;
        }
        Component line = Component.literal("[wfcore] Maintenance mode " + (enable ? "ENABLED" : "DISABLED")
                + " by " + source.getTextName() + ".")
                .withStyle(enable ? ChatFormatting.RED : ChatFormatting.GREEN);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (isOperator(player)) {
                player.sendSystemMessage(line);
            }
        }
    }

    public static Component maintenanceKickMessage() {
        String msg = WFCoreConfig.getMaintenanceKickMessage();
        if (msg == null || msg.isBlank()) {
            msg = "The server is down for maintenance.";
        }
        return Component.literal(msg);
    }

    /**
     * True when a login by this profile should be refused for maintenance: maintenance is on and the
     * profile is not an operator. Called from the {@code PlayerList#canPlayerLogin} mixin so the refusal
     * happens in the login phase and the client shows a clean disconnect message (like a ban), not the
     * "Connection Lost" screen a post-join disconnect produces.
     */
    public static boolean isLoginRefused(MinecraftServer server, GameProfile profile) {
        return WFCoreConfig.isMaintenanceEnabled() && !isOperator(server, profile);
    }

    /** True if the player is a server operator (ops list, singleplayer owner, or permission level &gt;= 2). */
    public static boolean isOperator(ServerPlayer player) {
        if (isOperator(player.getServer(), player.getGameProfile())) {
            return true;
        }
        return player.hasPermissions(2);
    }

    /** Operator check from a bare profile, for use before a {@link ServerPlayer} exists (i.e. at login). */
    public static boolean isOperator(MinecraftServer server, GameProfile profile) {
        if (server == null) {
            return false;
        }
        return server.getPlayerList().isOp(profile) || server.isSingleplayerOwner(profile);
    }
}
