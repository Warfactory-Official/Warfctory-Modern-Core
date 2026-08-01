package com.norwood.wfcore.common.chat;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.config.WFCoreConfig;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public final class ChatModeration {

    public static final ChatModeration INSTANCE = new ChatModeration();

    private ChatModeration() {}

     @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        if (!WFCoreConfig.isChatModerationEnabled()) {
            return;
        }
        ServerPlayer player = event.getPlayer();
        ServerLevel level = player.serverLevel();
        ChatModerationData data = ChatModerationData.get(level);

        long now = System.currentTimeMillis();
        ChatModerationData.MuteEntry mute = data.getActiveMute(player.getUUID(), now);
        if (mute != null) {
            event.setCanceled(true);
            player.sendSystemMessage(muteNotice(mute, now));
            return;
        }

        if (!WFCoreConfig.isChatFilterEnabled()) {
            return;
        }
        if (WFCoreConfig.isChatFilterExemptOps() && player.hasPermissions(2)) {
            return;
        }
        Pattern pattern = effectivePattern(data);
        if (pattern == null) {
            return;
        }
        String raw = event.getRawText();
        Matcher matcher = pattern.matcher(raw);
        if (!matcher.find()) {
            return;
        }

        if (WFCoreConfig.getChatFilterAction() == FilterAction.BLOCK) {
            event.setCanceled(true);
            if (WFCoreConfig.isChatFilterNotifySender()) {
                player.sendSystemMessage(Component.literal("§cYour message was blocked (blacklisted language)."));
            }
            WFCore.LOGGER.info("wfcore chat: blocked message from {}: {}", player.getGameProfile().getName(), raw);
        } else {
            String censored = pattern.matcher(raw).replaceAll(mr -> mask(mr.group().length()));
            event.setMessage(Component.literal(censored));
            if (WFCoreConfig.isChatFilterNotifySender()) {
                player.sendSystemMessage(Component.literal("§7Your message was censored (blacklisted language)."));
            }
        }
    }


    private static Pattern effectivePattern(ChatModerationData data) {
        Set<String> words = new LinkedHashSet<>(WFCoreConfig.getChatBlacklist());
        words.addAll(data.words());
        return buildPattern(words, WFCoreConfig.isChatFilterCaseSensitive(), WFCoreConfig.isChatFilterWholeWord());
    }


    private static Pattern buildPattern(Set<String> words, boolean caseSensitive, boolean wholeWord) {
        StringBuilder sb = new StringBuilder();
        for (String word : words) {
            String trimmed = word.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (sb.length() > 0) {
                sb.append('|');
            }
            if (wholeWord) {
                sb.append("(?<![\\p{L}\\p{N}])").append(Pattern.quote(trimmed)).append("(?![\\p{L}\\p{N}])");
            } else {
                sb.append(Pattern.quote(trimmed));
            }
        }
        if (sb.length() == 0) {
            return null;
        }
        int flags = caseSensitive ? 0 : (Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        return Pattern.compile(sb.toString(), flags);
    }

    private static String mask(int length) {
        String ch = WFCoreConfig.getChatCensorChar();
        char c = ch.isEmpty() ? '*' : ch.charAt(0);
        return String.valueOf(c).repeat(Math.max(1, length));
    }

    private static Component muteNotice(ChatModerationData.MuteEntry mute, long now) {
        StringBuilder sb = new StringBuilder("§cYou are muted");
        if (!mute.permanent()) {
            sb.append(" for ").append(formatRemaining(mute.expiresAt() - now));
        }
        if (!mute.reason().isBlank()) {
            sb.append("§c: §7").append(mute.reason());
        } else {
            sb.append("§c.");
        }
        return Component.literal(sb.toString());
    }
    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_chat")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("mute")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(ctx -> applyMute(ctx, 0, ""))
                                .then(Commands.argument("minutes", IntegerArgumentType.integer(0))
                                        .executes(ctx -> applyMute(ctx,
                                                IntegerArgumentType.getInteger(ctx, "minutes"), ""))
                                        .then(Commands.argument("reason", StringArgumentType.greedyString())
                                                .executes(ctx -> applyMute(ctx,
                                                        IntegerArgumentType.getInteger(ctx, "minutes"),
                                                        StringArgumentType.getString(ctx, "reason")))))))
                .then(Commands.literal("unmute")
                        .then(Commands.argument("targets", GameProfileArgument.gameProfile())
                                .executes(this::unmute)))
                .then(Commands.literal("list")
                        .executes(this::listMutes))
                .then(Commands.literal("filter")
                        .then(Commands.literal("add")
                                .then(Commands.argument("word", StringArgumentType.greedyString())
                                        .executes(this::filterAdd)))
                        .then(Commands.literal("remove")
                                .then(Commands.argument("word", StringArgumentType.greedyString())
                                        .executes(this::filterRemove)))
                        .then(Commands.literal("list")
                                .executes(this::filterList))
                        .then(Commands.literal("test")
                                .then(Commands.argument("text", StringArgumentType.greedyString())
                                        .executes(this::filterTest)))));
    }

    private int applyMute(CommandContext<CommandSourceStack> ctx, int minutes, String reason)
            throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        ServerLevel level = src.getLevel();
        ChatModerationData data = ChatModerationData.get(level);
        MinecraftServer server = src.getServer();

        long now = System.currentTimeMillis();
        long expiresAt = minutes <= 0 ? 0L : now + minutes * 60_000L;
        String source = src.getTextName();
        String durationText = minutes <= 0 ? "permanently" : "for " + formatRemaining(minutes * 60_000L);

        int count = 0;
        for (GameProfile profile : targets) {
            UUID id = profile.getId();
            String name = profile.getName() != null ? profile.getName() : id.toString();
            data.mute(id, name, expiresAt, reason, source);
            count++;

            ServerPlayer online = server.getPlayerList().getPlayer(id);
            if (online != null) {
                online.sendSystemMessage(muteNotice(new ChatModerationData.MuteEntry(expiresAt, reason, source, name),
                        now));
            }
            String suffix = reason.isBlank() ? "" : " §7(" + reason + "§7)";
            src.sendSuccess(() -> Component.literal("§aMuted §f" + name + "§a " + durationText + "§r" + suffix), true);
        }
        if (count == 0) {
            src.sendFailure(Component.literal("No matching players."));
        }
        return count;
    }

    private int unmute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        Collection<GameProfile> targets = GameProfileArgument.getGameProfiles(ctx, "targets");
        ChatModerationData data = ChatModerationData.get(src.getLevel());

        int unmuted = 0;
        for (GameProfile profile : targets) {
            String name = profile.getName() != null ? profile.getName() : profile.getId().toString();
            if (data.unmute(profile.getId())) {
                unmuted++;
                src.sendSuccess(() -> Component.literal("§aUnmuted §f" + name + "§a."), true);
                ServerPlayer online = src.getServer().getPlayerList().getPlayer(profile.getId());
                if (online != null) {
                    online.sendSystemMessage(Component.literal("§aYou have been unmuted."));
                }
            } else {
                src.sendFailure(Component.literal("§e" + name + " was not muted."));
            }
        }
        return unmuted;
    }

    private int listMutes(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ChatModerationData data = ChatModerationData.get(src.getLevel());
        long now = System.currentTimeMillis();
        List<Map.Entry<UUID, ChatModerationData.MuteEntry>> active = data.activeMutes(now);
        if (active.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§7No players are muted."), false);
            return 0;
        }
        src.sendSuccess(() -> Component.literal("§b" + active.size() + "§r muted player(s):"), false);
        for (Map.Entry<UUID, ChatModerationData.MuteEntry> e : active) {
            ChatModerationData.MuteEntry m = e.getValue();
            String when = m.permanent() ? "permanent" : formatRemaining(m.expiresAt() - now) + " left";
            String reason = m.reason().isBlank() ? "" : " §7— " + m.reason();
            src.sendSuccess(() -> Component.literal("§7  • §f" + m.name() + " §7(" + when + ", by " + m.source()
                    + ")" + reason), false);
        }
        return active.size();
    }

    private int filterAdd(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String word = StringArgumentType.getString(ctx, "word");
        ChatModerationData data = ChatModerationData.get(src.getLevel());
        if (data.addWord(word)) {
            src.sendSuccess(() -> Component.literal("§aAdded §f\"" + word.trim() + "\"§a to the chat blacklist."), true);
            return 1;
        }
        src.sendFailure(Component.literal("§e\"" + word.trim() + "\" is blank or already blacklisted."));
        return 0;
    }

    private int filterRemove(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String word = StringArgumentType.getString(ctx, "word");
        ChatModerationData data = ChatModerationData.get(src.getLevel());
        if (data.removeWord(word)) {
            src.sendSuccess(() -> Component.literal("§aRemoved §f\"" + word.trim() + "\"§a from the chat blacklist."),
                    true);
            return 1;
        }
        src.sendFailure(Component.literal("§e\"" + word.trim() + "\" is not a runtime blacklist word. "
                + "§7(Words set in wfcore.toml are edited there.)"));
        return 0;
    }

    private int filterList(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ChatModerationData data = ChatModerationData.get(src.getLevel());
        List<String> config = WFCoreConfig.getChatBlacklist();
        Set<String> runtime = data.words();

        src.sendSuccess(() -> Component.literal("§bChat blacklist§r §7(action: "
                + WFCoreConfig.getChatFilterAction() + ", whole-word: " + WFCoreConfig.isChatFilterWholeWord()
                + ", case-sensitive: " + WFCoreConfig.isChatFilterCaseSensitive() + ")"), false);
        src.sendSuccess(() -> Component.literal("§7  config (wfcore.toml): §f"
                + (config.isEmpty() ? "(none)" : String.join(", ", config))), false);
        src.sendSuccess(() -> Component.literal("§7  runtime (/wfcore_chat filter): §f"
                + (runtime.isEmpty() ? "(none)" : String.join(", ", runtime))), false);
        return config.size() + runtime.size();
    }

    private int filterTest(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        String text = StringArgumentType.getString(ctx, "text");
        ChatModerationData data = ChatModerationData.get(src.getLevel());
        Pattern pattern = effectivePattern(data);
        if (pattern == null || !pattern.matcher(text).find()) {
            src.sendSuccess(() -> Component.literal("§a✔ clean §7— this message would pass."), false);
            return 0;
        }
        if (WFCoreConfig.getChatFilterAction() == FilterAction.BLOCK) {
            src.sendSuccess(() -> Component.literal("§c✘ blocked §7— this message would be cancelled."), false);
        } else {
            String censored = pattern.matcher(text).replaceAll(mr -> mask(mr.group().length()));
            src.sendSuccess(() -> Component.literal("§e✘ censored §7→ §f" + censored), false);
        }
        return 1;
    }

    /** Renders a millisecond span as a coarse human duration, e.g. {@code "2h 5m"}, {@code "45s"}. */
    private static String formatRemaining(long millis) {
        long totalSeconds = Math.max(0, millis / 1000L);
        long days = totalSeconds / 86_400L;
        long hours = (totalSeconds % 86_400L) / 3_600L;
        long minutes = (totalSeconds % 3_600L) / 60L;
        long seconds = totalSeconds % 60L;
        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (sb.length() == 0) {
            sb.append(seconds).append('s');
        }
        return sb.toString().trim();
    }
}
