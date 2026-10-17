package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.ColumnPosArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ColumnPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.data.worldgen.bedrockore.BedrockOreVeinSavedData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeVeinAllocation;

import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

/**
 * {@code /wfcore_resources at [pos]}: every weight rule's density / richness at a column, each system's paint /
 * assign pick for its chunk, plus the bedrock fluid / bedrock ore / WarForge vein rolled there. {@code catalog}:
 * every allocatable target -> {@code <server dir>/resource_catalog.json}. {@code dump <chunkX> <chunkZ> <n>}: zones,
 * rules and picks over an n^2 chunk window -> {@code <server dir>/resource_dump.json}. Both read by
 * {@code worldgen-workspace}.
 */
public final class ResourceCommands {

    public static final ResourceCommands INSTANCE = new ResourceCommands();

    private ResourceCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_resources")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("catalog").executes(ResourceCommands::catalog))
                .then(Commands.literal("dump")
                        .then(Commands.argument("chunkX", IntegerArgumentType.integer())
                                .then(Commands.argument("chunkZ", IntegerArgumentType.integer())
                                        .then(Commands.argument("n", IntegerArgumentType.integer(1, 512))
                                                .executes(ResourceCommands::dump)))))
                .then(Commands.literal("at")
                        .executes(ctx -> at(ctx, new ColumnPos(Mth.floor(ctx.getSource().getPosition().x),
                                Mth.floor(ctx.getSource().getPosition().z))))
                        .then(Commands.argument("pos", ColumnPosArgument.columnPos())
                                .executes(ctx -> at(ctx, ColumnPosArgument.getColumnPos(ctx, "pos"))))));
    }

    private static int at(CommandContext<CommandSourceStack> ctx, ColumnPos column) {
        ServerLevel level = ctx.getSource().getLevel();
        AllocationIndex index = AllocationIndexManager.get(level);
        StringBuilder out = new StringBuilder(String.format("%s @ %d %d: %d allocation file(s)",
                level.dimension().location(), column.x(), column.z(), index.sources()));
        int rules = 0;
        int cx = column.x() >> 4;
        int cz = column.z() >> 4;
        for (ResourceSystem system : ResourceSystem.values()) {
            for (var rule : new TreeMap<>(index.weightRules(system)).entrySet()) {
                out.append(String.format("%n  %s %s density x%.3f richness x%.3f", system.id, rule.getKey(),
                        rule.getValue().at(column.x(), column.z()), rule.getValue().richness(column.x(), column.z())));
                rules++;
            }
            var picks = index.picks(system, cx, cz);
            if (picks != null) {
                var cell = index.cell(system, cx, cz);
                for (var pick : picks) {
                    out.append(String.format("%n  %s cell %d,%d %s %s richness %.3f", system.id, cell.x(), cell.z(),
                            pick.painted() ? "painted" : "assigned by rule " + (pick.rule() + 1),
                            pick.target().isEmpty() ? "(empty)" : pick.target(), pick.richness()));
                }
            }
        }
        // Rolling persists, exactly as a drill / claim would.
        out.append(String.format("%n  rolled %s %s", ResourceSystem.BEDROCK_FLUID.id, key(BedrockFluidVeinSavedData
                .getOrCreate(level).getFluidVeinWorldEntry(cx, cz).getDefinition())));
        out.append(String.format("%n  rolled %s %s", ResourceSystem.BEDROCK_ORE.id, key(BedrockOreVeinSavedData
                .getOrCreate(level).getOreVeinWorldEntry(cx, cz).getDefinition())));
        if (WarforgeIntegration.isLoaded()) {
            out.append(String.format("%n  rolled %s %s", ResourceSystem.WARFORGE_VEIN.id,
                    WarforgeVeinAllocation.rolled(level, cx, cz)));
        }
        ctx.getSource().sendSuccess(() -> Component.literal(out.toString()).withStyle(ChatFormatting.GRAY), false);
        return rules;
    }

    /** Sample points: chunk centres of the window, row-major (z outer). */
    private static int dump(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        AllocationIndex index = AllocationIndexManager.get(level);
        int cx0 = IntegerArgumentType.getInteger(ctx, "chunkX");
        int cz0 = IntegerArgumentType.getInteger(ctx, "chunkZ");
        int n = IntegerArgumentType.getInteger(ctx, "n");
        JsonObject root = new JsonObject();
        root.addProperty("seed", level.getSeed());
        root.addProperty("chunk_x", cx0);
        root.addProperty("chunk_z", cz0);
        root.addProperty("n", n);
        JsonObject zones = new JsonObject();
        for (String name : new TreeSet<>(index.zoneNames())) {
            JsonArray values = new JsonArray();
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    values.add(index.zone(name, (cx0 + c) * 16 + 8.0, (cz0 + r) * 16 + 8.0));
                }
            }
            zones.add(name, values);
        }
        root.add("zones", zones);
        JsonArray rules = new JsonArray();
        for (int i = 0; i < index.ruleCount(); i++) {
            JsonArray density = new JsonArray();
            JsonArray richness = new JsonArray();
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    double[] v = index.rule(i, (cx0 + c) * 16 + 8.0, (cz0 + r) * 16 + 8.0);
                    density.add(v[0]);
                    richness.add(v[1]);
                }
            }
            JsonObject rule = new JsonObject();
            rule.add("density", density);
            rule.add("richness", richness);
            rules.add(rule);
        }
        root.add("rules", rules);
        JsonObject picks = new JsonObject();
        for (ResourceSystem system : ResourceSystem.values()) {
            if (!index.decides(system)) {
                continue;
            }
            JsonArray chunks = new JsonArray();
            for (int r = 0; r < n; r++) {
                for (int c = 0; c < n; c++) {
                    var p = index.picks(system, cx0 + c, cz0 + r);
                    JsonArray list = new JsonArray();
                    if (p != null) {
                        for (var pick : p) {
                            JsonObject o = new JsonObject();
                            o.addProperty("target", pick.target());
                            o.addProperty("richness", pick.richness());
                            o.addProperty("painted", pick.painted());
                            list.add(o);
                        }
                    }
                    chunks.add(p == null ? null : list);
                }
            }
            picks.add(system.id, chunks);
        }
        root.add("picks", picks);
        Path file = level.getServer().getServerDirectory().resolve("resource_dump.json");
        try {
            Files.writeString(file, new GsonBuilder().serializeNulls().serializeSpecialFloatingPointValues().create()
                    .toJson(root) + "\n");
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Could not write " + file + ": " + e));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Wrote " + file.toAbsolutePath()), false);
        return 1;
    }

    private static String key(@Nullable Holder<?> holder) {
        return holder == null ? "none" : holder.unwrapKey().map(k -> k.location().toString()).orElse("?");
    }

    private static int catalog(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        var access = level.registryAccess();
        JsonObject root = new JsonObject();
        root.add(ResourceSystem.DEPOSIT.id, list(WFDeposits.all(), t -> entry(t.id(), t.weight(),
                t.dimensions().stream().map(ResourceLocation::toString).toList())));
        root.add(ResourceSystem.ORE_VEIN.id, registry(access.registryOrThrow(GTRegistries.Keys.ORE_VEIN), d -> {
            JsonObject o = entry(null, d.weight(), d.dimensionFilter().stream().map(k -> k.location().toString())
                    .toList());
            o.addProperty("layer", d.layer().getSerializedName());
            return o;
        }));
        root.add(ResourceSystem.BEDROCK_FLUID.id, registry(access.registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID),
                d -> entry(null, d.getWeight(), d.getDimensionFilter().stream()
                        .map(k -> k.location().toString()).toList())));
        root.add(ResourceSystem.BEDROCK_ORE.id, registry(access.registryOrThrow(GTRegistries.Keys.BEDROCK_ORE),
                d -> entry(null, d.weight(), d.dimensionFilter().stream().map(k -> k.location().toString())
                        .toList())));
        root.add(ResourceSystem.PLACED_FEATURE.id, registry(access.registryOrThrow(Registries.PLACED_FEATURE),
                d -> new JsonObject()));
        root.add(ResourceSystem.WARFORGE_VEIN.id,
                WarforgeIntegration.isLoaded() ? WarforgeVeinAllocation.catalog() : new JsonArray());

        Path file = level.getServer().getServerDirectory().resolve("resource_catalog.json");
        try {
            Files.writeString(file, new GsonBuilder().setPrettyPrinting().create().toJson(root) + "\n");
        } catch (IOException e) {
            ctx.getSource().sendFailure(Component.literal("Could not write " + file + ": " + e));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal("Wrote " + file.toAbsolutePath()), false);
        return 1;
    }

    static JsonObject entry(ResourceLocation id, int weight, Collection<String> dimensions) {
        JsonObject o = new JsonObject();
        if (id != null) {
            o.addProperty("id", id.toString());
        }
        o.addProperty("weight", weight);
        JsonArray dims = new JsonArray();
        dimensions.stream().sorted().forEach(dims::add);
        o.add("dimensions", dims);
        return o;
    }

    private static <T> JsonArray registry(Registry<T> registry, Function<T, JsonObject> describe) {
        JsonArray out = new JsonArray();
        registry.entrySet().stream()
                .sorted(Comparator.comparing((Map.Entry<ResourceKey<T>, T> e) -> e.getKey().location()))
                .forEach(e -> {
                    JsonObject o = new JsonObject();
                    o.addProperty("id", e.getKey().location().toString());
                    describe.apply(e.getValue()).entrySet().forEach(p -> o.add(p.getKey(), p.getValue()));
                    out.add(o);
                });
        return out;
    }

    private static <T> JsonArray list(Collection<T> items, Function<T, JsonObject> describe) {
        JsonArray out = new JsonArray();
        items.forEach(item -> out.add(describe.apply(item)));
        return out;
    }
}
