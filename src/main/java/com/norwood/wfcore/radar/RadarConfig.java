package com.norwood.wfcore.radar;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import com.norwood.wfcore.WFCore;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Radar target whitelist. Keyed directly by block registry name (GregTech Modern machines are
 * identified by their registry name rather than block/item meta), mapping to a richness value.
 */
public final class RadarConfig {

    private static final Object2IntOpenHashMap<ResourceLocation> WHITELIST = new Object2IntOpenHashMap<>();

    private static final String DEFAULT_CONFIG = """
            # Radar target whitelist.
            # Every machine/block listed here is visible to the radar and contributes to a base's score.
            # Use registry names directly; GregTech Modern machines are keyed by their registry name.
            #   value = richness contributed to the combined base score (defaults to 1).
            machines:
              - id: gtceu:electric_blast_furnace
                value: 10
              - id: gtceu:large_chemical_reactor
                value: 25
              - id: minecraft:furnace
                value: 1
            """;

    static {
        WHITELIST.defaultReturnValue(0);
    }

    private RadarConfig() {}

    public static boolean isWhitelisted(ResourceLocation id) {
        return WHITELIST.containsKey(id);
    }

    public static int getValue(ResourceLocation id) {
        return WHITELIST.getInt(id);
    }

    public static void load() {
        WHITELIST.clear();
        Path dir = FMLPaths.CONFIGDIR.get().resolve(WFCore.MOD_ID);
        Path file = dir.resolve("radar.yaml");
        try {
            Files.createDirectories(dir);
            if (Files.notExists(file) || Files.size(file) == 0) {
                Files.writeString(file, DEFAULT_CONFIG, StandardCharsets.UTF_8);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                parse(reader);
            }
        } catch (IOException | RuntimeException e) {
            WFCore.LOGGER.error("Failed to load radar config from {}", file, e);
        }
    }

    private static void parse(Reader reader) {
        Object loaded = new Yaml().load(reader);
        if (!(loaded instanceof Map<?, ?> root)) {
            return;
        }
        if (!(root.get("machines") instanceof List<?> list)) {
            return;
        }
        for (Object entry : list) {
            if (!(entry instanceof Map<?, ?> map)) {
                continue;
            }
            Object idObj = map.get("id");
            if (idObj == null) {
                continue;
            }
            ResourceLocation id = ResourceLocation.tryParse(String.valueOf(idObj).trim());
            if (id == null) {
                WFCore.LOGGER.warn("Ignoring invalid radar target id '{}'", idObj);
                continue;
            }
            int value = map.get("value") instanceof Number n ? n.intValue() : 1;
            WHITELIST.put(id, value);
        }
        WFCore.LOGGER.info("Loaded {} radar targets", WHITELIST.size());
    }
}
