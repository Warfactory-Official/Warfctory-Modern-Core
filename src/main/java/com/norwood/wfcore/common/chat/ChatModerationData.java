package com.norwood.wfcore.common.chat;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;


public class ChatModerationData extends SavedData {

    private static final String DATA_NAME = "wfcore_chat_moderation";

    /**
     * An active mute. {@code expiresAt} is an epoch-millis deadline, or {@code 0} for a permanent mute.
     * {@code source} is who issued it (player name or "Server") and {@code name} the muted player's last
     * known name, kept only so {@code /wfcore_chat list} can show it without a profile lookup.
     */
    public record MuteEntry(long expiresAt, String reason, String source, String name) {
        public boolean permanent() {
            return expiresAt == 0L;
        }

        public boolean isExpired(long now) {
            return !permanent() && now >= expiresAt;
        }
    }

    private final Map<UUID, MuteEntry> mutes = new HashMap<>();

    /** Runtime blacklist additions, stored as entered; matched case-insensitively unless config says otherwise. */
    private final Set<String> words = new LinkedHashSet<>();

    public ChatModerationData() {}

    public ChatModerationData(CompoundTag tag) {
        ListTag muteList = tag.getList("Mutes", Tag.TAG_COMPOUND);
        for (int i = 0; i < muteList.size(); i++) {
            CompoundTag e = muteList.getCompound(i);
            UUID id = new UUID(e.getLong("UUIDMost"), e.getLong("UUIDLeast"));
            mutes.put(id, new MuteEntry(
                    e.getLong("ExpiresAt"),
                    e.getString("Reason"),
                    e.getString("Source"),
                    e.getString("Name")));
        }
        ListTag wordList = tag.getList("Words", Tag.TAG_STRING);
        for (int i = 0; i < wordList.size(); i++) {
            String word = wordList.getString(i);
            if (!word.isBlank()) {
                words.add(word);
            }
        }
    }

    public static ChatModerationData get(ServerLevel anyLevel) {
        ServerLevel overworld = anyLevel.getServer().overworld();
        return overworld.getDataStorage().computeIfAbsent(ChatModerationData::new, ChatModerationData::new, DATA_NAME);
    }

    /** Adds/replaces a mute. {@code expiresAt} is epoch millis, or {@code 0} for permanent. */
    public void mute(UUID id, String name, long expiresAt, String reason, String source) {
        mutes.put(id, new MuteEntry(expiresAt, reason, source, name));
        setDirty();
    }

    /** Removes a mute; returns {@code true} if the player was muted. */
    public boolean unmute(UUID id) {
        if (mutes.remove(id) != null) {
            setDirty();
            return true;
        }
        return false;
    }


    @Nullable
    public MuteEntry getActiveMute(UUID id, long now) {
        MuteEntry entry = mutes.get(id);
        if (entry == null) {
            return null;
        }
        if (entry.isExpired(now)) {
            mutes.remove(id);
            setDirty();
            return null;
        }
        return entry;
    }

    public List<Map.Entry<UUID, MuteEntry>> activeMutes(long now) {
        List<Map.Entry<UUID, MuteEntry>> out = new ArrayList<>();
        boolean purged = false;
        var it = mutes.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, MuteEntry> e = it.next();
            if (e.getValue().isExpired(now)) {
                it.remove();
                purged = true;
            } else {
                out.add(Map.entry(e.getKey(), e.getValue()));
            }
        }
        if (purged) {
            setDirty();
        }
        return out;
    }



    /** Adds a runtime blacklist word/phrase; returns {@code false} if blank or already present. */
    public boolean addWord(String word) {
        String trimmed = word.trim();
        if (trimmed.isEmpty() || containsIgnoreCase(trimmed)) {
            return false;
        }
        words.add(trimmed);
        setDirty();
        return true;
    }

    /** Removes a runtime blacklist word/phrase (case-insensitive); returns {@code true} if one was removed. */
    public boolean removeWord(String word) {
        String trimmed = word.trim();
        if (words.removeIf(w -> w.equalsIgnoreCase(trimmed))) {
            setDirty();
            return true;
        }
        return false;
    }

    private boolean containsIgnoreCase(String word) {
        for (String w : words) {
            if (w.equalsIgnoreCase(word)) {
                return true;
            }
        }
        return false;
    }

    /** An unmodifiable snapshot of the runtime blacklist words. */
    public Set<String> words() {
        return new LinkedHashSet<>(words);
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag muteList = new ListTag();
        for (Map.Entry<UUID, MuteEntry> entry : mutes.entrySet()) {
            MuteEntry m = entry.getValue();
            CompoundTag e = new CompoundTag();
            e.putLong("UUIDMost", entry.getKey().getMostSignificantBits());
            e.putLong("UUIDLeast", entry.getKey().getLeastSignificantBits());
            e.putLong("ExpiresAt", m.expiresAt());
            e.putString("Reason", m.reason());
            e.putString("Source", m.source());
            e.putString("Name", m.name());
            muteList.add(e);
        }
        tag.put("Mutes", muteList);

        ListTag wordList = new ListTag();
        for (String word : words) {
            wordList.add(StringTag.valueOf(word));
        }
        tag.put("Words", wordList);
        return tag;
    }

}
