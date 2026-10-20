package com.norwood.wfcore.common.worldgen;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;
import com.norwood.wfcore.common.worldgen.rock.RockLayers;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.function.Function;

/**
 * {@code /wfcore_density dump}: parity oracle for {@code worldgen-workspace}'s Go evaluator.
 * {@code <server dir>/density_request.json} {@code {functions, router, surface, rock_layers, points: [[x,y,z]]}} ->
 * {@code density_dump.json}, doubles as raw long bits.
 */
public final class DensityDumpCommands {

    public static final DensityDumpCommands INSTANCE = new DensityDumpCommands();

    private static final Map<String, Function<NoiseRouter, DensityFunction>> ROUTER = Map.ofEntries(
            Map.entry("barrier", NoiseRouter::barrierNoise),
            Map.entry("fluid_level_floodedness", NoiseRouter::fluidLevelFloodednessNoise),
            Map.entry("fluid_level_spread", NoiseRouter::fluidLevelSpreadNoise),
            Map.entry("lava", NoiseRouter::lavaNoise),
            Map.entry("temperature", NoiseRouter::temperature),
            Map.entry("vegetation", NoiseRouter::vegetation),
            Map.entry("continents", NoiseRouter::continents),
            Map.entry("erosion", NoiseRouter::erosion),
            Map.entry("depth", NoiseRouter::depth),
            Map.entry("ridges", NoiseRouter::ridges),
            Map.entry("initial_density_without_jaggedness", NoiseRouter::initialDensityWithoutJaggedness),
            Map.entry("final_density", NoiseRouter::finalDensity));

    private DensityDumpCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_density")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("dump").executes(DensityDumpCommands::dump)));
    }

    private static int dump(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        Path dir = ctx.getSource().getServer().getServerDirectory();
        try {
            JsonObject request = JsonParser.parseString(Files.readString(dir.resolve("density_request.json")))
                    .getAsJsonObject();
            RandomState randomState = level.getChunkSource().randomState();
            JsonArray points = request.getAsJsonArray("points");
            JsonObject values = new JsonObject();
            var registry = level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION);
            var wiring = new AllocationIndex.Wiring(randomState);
            for (var id : array(request, "functions")) {
                var key = ResourceKey.create(Registries.DENSITY_FUNCTION, ResourceLocation.parse(id));
                DensityFunction function = registry.getHolderOrThrow(key).value().mapAll(wiring);
                values.add(id, sample(function, points));
            }
            for (var target : array(request, "router")) {
                var accessor = ROUTER.get(target);
                if (accessor == null) {
                    throw new IllegalArgumentException("unknown router target " + target);
                }
                values.add("router/" + target, sample(accessor.apply(randomState.router()), points));
            }
            if (request.has("surface") && request.get("surface").getAsBoolean()) {
                var generator = level.getChunkSource().getGenerator();
                JsonArray heights = new JsonArray();
                for (var p : points) {
                    var xyz = p.getAsJsonArray();
                    heights.add(generator.getBaseHeight(xyz.get(0).getAsInt(), xyz.get(2).getAsInt(),
                            Heightmap.Types.WORLD_SURFACE_WG, level, randomState));
                }
                values.add("surface", heights);
            }
            if (request.has("rock_layers") && request.get("rock_layers").getAsBoolean()) {
                RockLayers layers = RockLayers.of(level).orElseThrow(() -> new IllegalStateException("no rock layers"));
                DensityFunction surface = layers.surface(randomState);
                JsonArray tops = new JsonArray();
                for (var p : points) {
                    var xyz = p.getAsJsonArray();
                    double s = RockLayers.column(surface, xyz.get(0).getAsInt(), xyz.get(2).getAsInt());
                    JsonArray row = new JsonArray();
                    row.add(layers.deeprock().top(s));
                    row.add(layers.deepslate().top(s));
                    row.add(layers.gtY(xyz.get(1).getAsInt(), s));
                    tops.add(row);
                }
                values.add("rock_layers", tops);
                // ns per surface corner, after an equal warm-up pass
                long started = 0;
                double sink = 0;
                for (int pass = 0; pass < 2; pass++) {
                    started = System.nanoTime();
                    for (int k = 0; k < 20_000; k++) {
                        sink += RockLayers.corner(surface, k * 37 % 9000 * 4, k * 53 % 9000 * 4);
                    }
                }
                values.addProperty("rock_corner_ns", (System.nanoTime() - started) / 20_000.0);
                values.addProperty("rock_sink", sink);
            }
            JsonObject out = new JsonObject();
            out.addProperty("seed", level.getSeed());
            out.add("points", points);
            out.add("values", values);
            Files.writeString(dir.resolve("density_dump.json"), out.toString());
        } catch (IOException | RuntimeException e) {
            ctx.getSource().sendFailure(Component.literal("density dump failed: " + e));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("density_dump.json written"), false);
        return 1;
    }

    private static Iterable<String> array(JsonObject request, String key) {
        if (!request.has(key)) {
            return java.util.List.of();
        }
        return request.getAsJsonArray(key).asList().stream().map(e -> e.getAsString()).toList();
    }

    private static JsonArray sample(DensityFunction function, JsonArray points) {
        JsonArray out = new JsonArray();
        for (var p : points) {
            var xyz = p.getAsJsonArray();
            double v = function.compute(new DensityFunction.SinglePointContext(xyz.get(0).getAsInt(),
                    xyz.get(1).getAsInt(), xyz.get(2).getAsInt()));
            out.add(Long.toString(Double.doubleToRawLongBits(v)));
        }
        return out;
    }
}
