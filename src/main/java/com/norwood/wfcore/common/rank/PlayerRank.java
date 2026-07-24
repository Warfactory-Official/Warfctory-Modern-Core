package com.norwood.wfcore.common.rank;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;

import java.util.function.UnaryOperator;

import javax.annotation.Nullable;


public enum PlayerRank {

    /** Vanity tag only. */
    ARTIST("Artist", ChatFormatting.LIGHT_PURPLE, false, true, null),
    SCRIPTER("Scripter", ChatFormatting.AQUA, false, true, null),
    MTNS("Mtns", ChatFormatting.DARK_AQUA, false, false, PlayerRank::likeInsert),
    SUPEROBAMA("SuperObama", ChatFormatting.GOLD, false, false, content -> content + " o algo"),
    PRESS("Press", ChatFormatting.YELLOW, true, true, null),
    MODERATOR("Moderator", ChatFormatting.GREEN, true, true, null),
    ADMIN("Admin", ChatFormatting.RED, true, true, null);

    private final String label;
    private final ChatFormatting color;
    private final boolean grantsReplay;
    private final boolean vanityTag;
    @Nullable
    private final UnaryOperator<String> contentTransform;

    PlayerRank(String label, ChatFormatting color, boolean grantsReplay, boolean vanityTag,
            @Nullable UnaryOperator<String> contentTransform) {
        this.label = label;
        this.color = color;
        this.grantsReplay = grantsReplay;
        this.vanityTag = vanityTag;
        this.contentTransform = contentTransform;
    }

    public String label() {
        return label;
    }

    /** True if this rank unlocks ReplayMod playback. */
    public boolean grantsReplay() {
        return grantsReplay;
    }

    /** True if this rank shows a coloured {@code [Label]} tag in chat and the tab list. */
    public boolean hasVanityTag() {
        return vanityTag;
    }

    /** A transform applied to the raw text of the player's chat messages, or {@code null} for none. */
    @Nullable
    public UnaryOperator<String> contentTransform() {
        return contentTransform;
    }

    /** The coloured {@code [Label]} component, or {@code null} when this rank has no vanity tag. */
    @Nullable
    public Component tag() {
        return vanityTag ? Component.literal("[" + label + "]").withStyle(color) : null;
    }

    /** Case-insensitive lookup by enum name (e.g. {@code "admin"}); {@code null} for an unknown id. */
    @Nullable
    public static PlayerRank byId(@Nullable String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim();
        for (PlayerRank rank : values()) {
            if (rank.name().equalsIgnoreCase(trimmed)) {
                return rank;
            }
        }
        return null;
    }

    /** {@code "Hello I am mtns"} -> {@code "Hello like I like am like mtns like"}. */
    private static String likeInsert(String content) {
        if (content == null || content.isBlank()) {
            return content;
        }
        String[] words = content.trim().split("\\s+");
        return String.join(" like ", words) + " like";
    }
}
