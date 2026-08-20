package com.norwood.wfcore.integration.kubejs;

import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.radar.RadarConfig;
import com.norwood.wfcore.radar.WFRadarScripts;

import java.util.Locale;
import java.util.Map;

/**
 * KubeJS binding exposed as {@code WFRadar} in <b>startup scripts</b>. It lets a pack shape what the radar
 * detects — the target whitelist and the default DBSCAN tuning — without editing {@code wfcore-radar.toml}.
 *
 * <p>
 * Operations are recorded and replayed at common setup (after the block registry is frozen), then stored in
 * {@link RadarConfig} so they also survive a config reload. Unknown ids/tiers are skipped with a warning
 * rather than crashing.
 *
 * <pre>{@code
 * // startup_scripts/radar.js
 * WFRadar
 *     // every GregTech machine of tier HV and above becomes a radar target (richness 1)
 *     .whitelistMachinesAtLeast('hv')
 *     // bump a couple of high-value multiblocks
 *     .whitelist('gtceu:fusion_reactor', 50)
 *     .whitelist('gtceu:large_chemical_reactor', 25)
 *     // drop one you don't care about
 *     .removeFromWhitelist('minecraft:furnace')
 *     // retune the default clustering
 *     .eps(160)
 *     .minPts(12)
 * }</pre>
 */
public class WFRadarBindings {

    /** Add {@code id} to the radar whitelist with richness 1. */
    public WFRadarBindings whitelist(String id) {
        return whitelist(id, 1);
    }

    /** Add {@code id} to the radar whitelist with the given richness value. */
    public WFRadarBindings whitelist(String id, int value) {
        ResourceLocation rl = parse(id);
        if (rl != null) {
            WFRadarScripts.enqueue(() -> RadarConfig.scriptAdd(rl, value));
        }
        return this;
    }

    /** Remove {@code id} from the radar whitelist, even if the config lists it. */
    public WFRadarBindings removeFromWhitelist(String id) {
        ResourceLocation rl = parse(id);
        if (rl != null) {
            WFRadarScripts.enqueue(() -> RadarConfig.scriptRemove(rl));
        }
        return this;
    }

    /** Whitelist every GregTech machine whose voltage tier is {@code >= minTier} (ULV=0, LV=1, ... HV=3, ...). */
    public WFRadarBindings whitelistMachinesAtLeast(int minTier) {
        return whitelistMachinesAtLeast(minTier, 1);
    }

    /** As {@link #whitelistMachinesAtLeast(int)} with an explicit richness value. */
    public WFRadarBindings whitelistMachinesAtLeast(int minTier, int value) {
        WFRadarScripts.enqueue(() -> addMachinesAtLeast(minTier, value));
        return this;
    }

    /** Whitelist every GregTech machine of the named tier or higher, e.g. {@code 'hv'}. */
    public WFRadarBindings whitelistMachinesAtLeast(String tierName) {
        return whitelistMachinesAtLeast(tierName, 1);
    }

    /** As {@link #whitelistMachinesAtLeast(String)} with an explicit richness value. */
    public WFRadarBindings whitelistMachinesAtLeast(String tierName, int value) {
        int tier = resolveTier(tierName);
        if (tier >= 0) {
            WFRadarScripts.enqueue(() -> addMachinesAtLeast(tier, value));
        }
        return this;
    }

    /** Override the default DBSCAN neighbourhood radius (blocks). */
    public WFRadarBindings eps(int value) {
        WFRadarScripts.enqueue(() -> RadarConfig.scriptEps(value));
        return this;
    }

    /** Override the default DBSCAN minimum cluster size. */
    public WFRadarBindings minPts(int value) {
        WFRadarScripts.enqueue(() -> RadarConfig.scriptMinPts(value));
        return this;
    }

    /** Whether {@code id} is currently on the whitelist (reads live state; run after startup to be meaningful). */
    public boolean isWhitelisted(String id) {
        ResourceLocation rl = parse(id);
        return rl != null && RadarConfig.isWhitelisted(rl);
    }

    /** The current richness value for {@code id}, or 0 if it is not whitelisted. */
    public int getValue(String id) {
        ResourceLocation rl = parse(id);
        return rl == null ? 0 : RadarConfig.getValue(rl);
    }

    private static void addMachinesAtLeast(int minTier, int value) {
        int count = 0;
        for (Map.Entry<ResourceKey<Block>, Block> entry : BuiltInRegistries.BLOCK.entrySet()) {
            if (entry.getValue() instanceof MetaMachineBlock machine && machine.definition.getTier() >= minTier) {
                RadarConfig.scriptAdd(entry.getKey().location(), value);
                count++;
            }
        }
        WFCore.LOGGER.info("WFRadar: whitelisted {} GregTech machine(s) at tier >= {} (richness {})",
                count, minTier, value);
    }

    private static int resolveTier(String name) {
        if (name != null) {
            String wanted = name.trim();
            for (int i = 0; i < GTValues.VN.length; i++) {
                if (GTValues.VN[i].equalsIgnoreCase(wanted)) {
                    return i;
                }
            }
        }
        WFCore.LOGGER.warn("WFRadar: unknown voltage tier '{}' (expected one of {})",
                name, String.join(", ", GTValues.VN).toLowerCase(Locale.ROOT));
        return -1;
    }

    private static ResourceLocation parse(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) {
            WFCore.LOGGER.warn("WFRadar: ignoring invalid id '{}'", id);
        }
        return rl;
    }
}
