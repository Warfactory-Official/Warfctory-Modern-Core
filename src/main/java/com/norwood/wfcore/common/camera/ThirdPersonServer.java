package com.norwood.wfcore.common.camera;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.norwood.wfcore.net.WFNet;

/**
 * Server half of the third-person rule: {@code /wfcore_thirdperson} (ops only) flips it, the world remembers it
 * ({@link ThirdPersonData}), and every client is told ({@link ThirdPersonPolicyMessage}).
 *
 * <pre>
 *   /wfcore_thirdperson           toggle
 *   /wfcore_thirdperson on|off    set
 *   /wfcore_thirdperson status    report
 * </pre>
 */
public final class ThirdPersonServer {

    public static final ThirdPersonServer INSTANCE = new ThirdPersonServer();

    /** Op level, as {@code /gamemode} and the other WFCore admin commands. */
    private static final int PERMISSION_LEVEL = 2;

    private ThirdPersonServer() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_thirdperson")
                .requires(source -> source.hasPermission(PERMISSION_LEVEL))
                .executes(ctx -> set(ctx, !ThirdPersonData.get(ctx.getSource().getServer()).isAllowed()))
                .then(Commands.literal("status").executes(ThirdPersonServer::status))
                .then(Commands.literal("on").executes(ctx -> set(ctx, true)))
                .then(Commands.literal("off").executes(ctx -> set(ctx, false)))
                .then(Commands.argument("allowed", BoolArgumentType.bool())
                        .executes(ctx -> set(ctx, BoolArgumentType.getBool(ctx, "allowed")))));
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            WFNet.sendToPlayer(player,
                    new ThirdPersonPolicyMessage(ThirdPersonData.get(player.getServer()).isAllowed()));
        }
    }

    private static int status(CommandContext<CommandSourceStack> ctx) {
        boolean allowed = ThirdPersonData.get(ctx.getSource().getServer()).isAllowed();
        ctx.getSource().sendSuccess(() -> Component.literal("Third person is " + (allowed ? "§aallowed" : "§clocked")
                + "§r. Riders keep their own camera either way."), false);
        return allowed ? 1 : 0;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, boolean allowed) {
        MinecraftServer server = ctx.getSource().getServer();
        ThirdPersonData.get(server).setAllowed(allowed);
        ThirdPersonPolicyMessage message = new ThirdPersonPolicyMessage(allowed);
        Component notice = Component.literal(allowed ? "Third person view is now allowed."
                : "Third person view is now locked.");
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            WFNet.sendToPlayer(player, message);
            player.displayClientMessage(notice, true);
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Third person " + (allowed ? "§aallowed" : "§clocked")
                + "§r for everyone."), true);
        return allowed ? 1 : 0;
    }
}
