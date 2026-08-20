package com.norwood.wfcore.client;

import java.util.UUID;
import java.util.regex.Pattern;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

import com.flansmod.warforge.client.PlayerNametagCache.NamePlateData;
import com.flansmod.warforge.common.WarForgeMod;

import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectOpenHashSet;


public final class FactionTeammateCache {

    private FactionTeammateCache() {}

    /** Vanilla section-sign formatting stripper, mirroring WarForge's own nameplate cache key. */
    private static final Pattern FORMATTING = Pattern.compile("§.");

    /** Re-derive the local player's own faction name at most this often. */
    private static final long SELF_TTL_MS = 2_000L;
    /** Re-verify another player's membership at most this often. */
    private static final long MEMBER_TTL_MS = 2_000L;

    private static String selfFaction;
    private static long selfCheckedAt = Long.MIN_VALUE;

    /** Confirmed teammate UUIDs - the cached roster. */
    private static final ObjectOpenHashSet<UUID> members = new ObjectOpenHashSet<>();
    /** Per-player expiry (ms) of the last membership verdict, teammate or not, to throttle re-checks. */
    private static final Object2LongOpenHashMap<UUID> verifiedUntil = new Object2LongOpenHashMap<>();

    /** Whether {@code entity} is another player in the local player's faction. */
    public static boolean isTeammate(Entity entity) {
        if (!(entity instanceof Player) || entity == Minecraft.getInstance().player) {
            return false;
        }
        long now = System.currentTimeMillis();
        String myFaction = selfFaction(now);
        UUID id = entity.getUUID();
        if (myFaction == null) {
            // Factionless (or own faction not yet known): nobody is a teammate. Drop any stale roster.
            if (!members.isEmpty()) {
                members.clear();
                verifiedUntil.clear();
            }
            return false;
        }
        // Fast path: this player's verdict is still fresh.
        if (now < verifiedUntil.getLong(id)) {
            return members.contains(id);
        }
        NamePlateData data = WarForgeMod.NAMETAG_CACHE.requestIfAbsent(nameKey(entity));
        if (data == null) {
            // Awaiting a response (or factionless): retry next frame rather than caching a miss.
            members.remove(id);
            return false;
        }
        boolean teammate = myFaction.equals(data.name);
        if (teammate) {
            members.add(id);
        } else {
            members.remove(id);
        }
        verifiedUntil.put(id, now + MEMBER_TTL_MS);
        return teammate;
    }

    /** The local player's faction name, or {@code null} if factionless / not yet resolved. */
    private static String selfFaction(long now) {
        if (now - selfCheckedAt < SELF_TTL_MS) {
            return selfFaction;
        }
        selfCheckedAt = now;
        Player self = Minecraft.getInstance().player;
        if (self == null) {
            selfFaction = null;
        } else {
            // WarForge never requests your own nameplate, so prime it ourselves to learn our faction.
            NamePlateData data = WarForgeMod.NAMETAG_CACHE.requestIfAbsent(nameKey(self));
            selfFaction = data == null ? null : data.name;
        }
        return selfFaction;
    }

    private static String nameKey(Entity entity) {
        return FORMATTING.matcher(entity.getDisplayName().getString()).replaceAll("");
    }
}
