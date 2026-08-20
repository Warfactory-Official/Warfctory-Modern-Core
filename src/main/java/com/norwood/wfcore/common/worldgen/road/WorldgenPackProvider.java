package com.norwood.wfcore.common.worldgen.road;

import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.BuiltInPackSource;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.event.AddPackFindersEvent;

import com.norwood.wfcore.WFCore;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Offers the generated {@code wf_worldgen} datapack in the world-creation data pack list, read from a plain
 * directory beside the game dir rather than from wfcore's jar.
 *
 * <p>The pack is layer 1 of {@code worldgen-workspace/DESIGN.md} — terrain, climate and the road network — and it
 * is deliberately <em>not</em> shipped inside wfcore: the design keeps layer 1 a standalone datapack that never
 * depends on the mod, and reading it from disk means regenerating it with {@code build.py} takes effect on the
 * next launch instead of on the next jar rebuild.
 *
 * <p>Path is {@code <gamedir>/wf_worldgen}, overridable with {@code -Dwfcore.worldgenPack=<dir>}. The Gradle
 * {@code copyWorldgenPack} task stages {@code worldgen-workspace/dist/wf_worldgen} there for the dev runs.
 */
public final class WorldgenPackProvider {

    public static final String PACK_ID = "wf_worldgen";
    private static final String PATH_PROPERTY = "wfcore.worldgenPack";

    private WorldgenPackProvider() {}

    public static void addPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.SERVER_DATA) {
            return;
        }
        Path dir = packDirectory();
        if (!Files.isDirectory(dir) || !Files.isRegularFile(dir.resolve("pack.mcmeta"))) {
            WFCore.LOGGER.debug("No worldgen datapack at {}; skipping", dir);
            return;
        }

        PackLocationInfo location = new PackLocationInfo(PACK_ID,
                Component.literal("Warfactory Worldgen"),
                PackSource.BUILT_IN,
                Optional.empty());
        // Not alwaysActive: the terrain layout is a world-creation choice, so it has to be togglable there.
        Pack pack = Pack.readMetaAndCreate(location,
                BuiltInPackSource.fromName(info -> new PathPackResources(info, dir)),
                PackType.SERVER_DATA,
                new PackSelectionConfig(false, Pack.Position.TOP, false));
        if (pack == null) {
            WFCore.LOGGER.warn("Worldgen datapack at {} has an unreadable pack.mcmeta", dir);
            return;
        }
        WFCore.LOGGER.info("Offering worldgen datapack from {}", dir);
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }

    private static Path packDirectory() {
        String override = System.getProperty(PATH_PROPERTY);
        return override != null ? Path.of(override) : FMLPaths.GAMEDIR.get().resolve(PACK_ID);
    }
}
