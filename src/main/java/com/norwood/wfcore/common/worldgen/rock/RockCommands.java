package com.norwood.wfcore.common.worldgen.rock;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.gregtechceu.gtceu.api.data.chemical.ChemicalHelper;
import com.gregtechceu.gtceu.api.data.tag.TagPrefix;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.data.WFBlocks;

import java.io.IOException;
import java.nio.file.Files;
import java.util.Map;
import java.util.TreeMap;

/**
 * {@code /wfcore_rock census <x> <z> <r>}: (2r+1)^2 chunks around (x, z), generated if needed. Per vertical zone
 * relative to the column's {@link RockLayers} tops: block kinds, GT ores by host; per column: terrain top vs
 * predicted surface, highest deeprock / lowest stone vs deeprock top. -> {@code <server dir>/rock_census_<x>_<z>.json}.
 */
@EventBusSubscriber(modid = WFCore.MOD_ID)
public final class RockCommands {

    private RockCommands() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_rock")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("census")
                        .then(Commands.argument("x", IntegerArgumentType.integer())
                                .then(Commands.argument("z", IntegerArgumentType.integer())
                                        .then(Commands.argument("r", IntegerArgumentType.integer(0, 8))
                                                .executes(RockCommands::census))))));
    }

    // zones: above deepslate dither, deepslate dither, deepslate, deeprock dither, deeprock
    private static final String[] ZONES = { "stone", "slate_blend", "slate", "rock_blend", "rock" };

    private static int census(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        int x0 = IntegerArgumentType.getInteger(ctx, "x"), z0 = IntegerArgumentType.getInteger(ctx, "z");
        int r = IntegerArgumentType.getInteger(ctx, "r");
        RockLayers rock = RockLayers.of(level).orElse(null);
        if (rock == null) {
            ctx.getSource().sendFailure(Component.literal("no rock layers in " + level.dimension().location()));
            return 0;
        }
        DensityFunction surface = rock.surface(level.getChunkSource().randomState());
        BlockState deeprock = WFBlocks.DEEPROCK.getDefaultState();
        @SuppressWarnings("unchecked")
        Map<String, Integer>[] zones = new Map[ZONES.length];
        for (int i = 0; i < zones.length; i++) {
            zones[i] = new TreeMap<>();
        }
        Map<Integer, Integer> depthError = new TreeMap<>(); // terrain top - predicted surface floor
        Map<Integer, Integer> rockTopGap = new TreeMap<>(); // deeprock top - 1 - highest deeprock (blend: 0..7)
        int columns = 0, stoneUnderRock = 0, rockAboveTop = 0, cavedColumns = 0;
        JsonArray samples = new JsonArray();
        int cx0 = x0 >> 4, cz0 = z0 >> 4;
        for (int cx = cx0 - r; cx <= cx0 + r; cx++) {
            for (int cz = cz0 - r; cz <= cz0 + r; cz++) {
                LevelChunk chunk = level.getChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int x = (cx << 4) + lx, z = (cz << 4) + lz;
                        double s = RockLayers.column(surface, x, z);
                        int t = rock.deeprock().top(s), u = rock.deepslate().top(s);
                        int top = Integer.MIN_VALUE, highestRock = Integer.MIN_VALUE;
                        int lowestStone = Integer.MAX_VALUE;
                        boolean caved = false;
                        for (int y = level.getMaxBuildHeight() - 1; y >= level.getMinBuildHeight(); y--) {
                            LevelChunkSection section = chunk.getSection(chunk.getSectionIndex(y));
                            if (section.hasOnlyAir()) {
                                y = (y >> 4 << 4);
                                continue;
                            }
                            BlockState state = section.getBlockState(lx, y & 15, lz);
                            String kind = kind(state, deeprock);
                            if (top == Integer.MIN_VALUE) {
                                if (kind.equals("air") || kind.equals("fluid") || kind.equals("plant")) {
                                    continue;
                                }
                                top = y;
                            }
                            if (kind.equals("air") && y < top - 4 && y >= t) {
                                caved = true;
                            }
                            if (kind.equals("deeprock") && highestRock == Integer.MIN_VALUE) {
                                highestRock = y;
                            }
                            if (kind.equals("stone") || kind.equals("deepslate")) {
                                lowestStone = y;
                            }
                            int zone = y >= u ? 0 : y >= u - rock.blend() ? 1 : y >= t ? 2 : y >= t - rock.blend() ? 3 : 4;
                            zones[zone].merge(kind, 1, Integer::sum);
                        }
                        columns++;
                        if (caved) {
                            cavedColumns++;
                        }
                        depthError.merge(top - (int) Math.floor(s), 1, Integer::sum);
                        if (highestRock != Integer.MIN_VALUE) {
                            rockTopGap.merge(t - 1 - highestRock, 1, Integer::sum);
                            if (highestRock >= t) {
                                rockAboveTop++;
                            }
                        }
                        if (lowestStone < t - rock.blend()) {
                            stoneUnderRock++;
                        }
                        if (lx == 8 && lz == 8) {
                            JsonObject col = new JsonObject();
                            col.addProperty("x", x);
                            col.addProperty("z", z);
                            col.addProperty("surface", s);
                            col.addProperty("terrain_top", top);
                            col.addProperty("deepslate_top", u);
                            col.addProperty("deeprock_top", t);
                            col.addProperty("highest_deeprock", highestRock);
                            col.addProperty("lowest_stone", lowestStone);
                            col.addProperty("caved", caved);
                            samples.add(col);
                        }
                    }
                }
            }
        }
        JsonObject out = new JsonObject();
        out.addProperty("x", x0);
        out.addProperty("z", z0);
        out.addProperty("chunks", (2 * r + 1) * (2 * r + 1));
        out.addProperty("columns", columns);
        out.addProperty("caved_columns", cavedColumns);
        out.addProperty("stone_or_deepslate_below_deeprock_blend", stoneUnderRock);
        out.addProperty("deeprock_at_or_above_top", rockAboveTop);
        out.add("terrain_top_minus_surface", histogram(depthError));
        out.add("deeprock_top_gap", histogram(rockTopGap));
        JsonObject zoneJson = new JsonObject();
        for (int i = 0; i < ZONES.length; i++) {
            JsonObject z = new JsonObject();
            zones[i].forEach(z::addProperty);
            zoneJson.add(ZONES[i], z);
        }
        out.add("zones", zoneJson);
        out.add("columns_at_chunk_centres", samples);
        var file = ctx.getSource().getServer().getServerDirectory().resolve("rock_census_" + x0 + "_" + z0 + ".json");
        try {
            Files.writeString(file, out.toString());
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("rock census: " + e));
            return 0;
        }
        int n = columns;
        int bad = stoneUnderRock + rockAboveTop;
        ctx.getSource().sendSuccess(() -> Component.literal("rock census " + n + " columns, " + bad
                + " boundary violations -> " + file.getFileName()), false);
        return 1;
    }

    private static JsonObject histogram(Map<Integer, Integer> h) {
        JsonObject o = new JsonObject();
        h.forEach((k, v) -> o.addProperty(Integer.toString(k), v));
        return o;
    }

    /** Coarse block kind; GT ores as {@code ore_<prefix>}. */
    private static String kind(BlockState state, BlockState deeprock) {
        if (state.isAir()) {
            return "air";
        }
        if (!state.getFluidState().isEmpty() && !state.blocksMotion()) {
            return "fluid";
        }
        if (state.is(deeprock.getBlock())) {
            return "deeprock";
        }
        if (state.is(Blocks.DEEPSLATE)) {
            return "deepslate";
        }
        if (state.is(Blocks.STONE)) {
            return "stone";
        }
        TagPrefix prefix = ChemicalHelper.getPrefix(state.getBlock());
        if (TagPrefix.ORES.containsKey(prefix)) {
            return "ore_" + prefix.name;
        }
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced()) {
            return "plant";
        }
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
    }
}
