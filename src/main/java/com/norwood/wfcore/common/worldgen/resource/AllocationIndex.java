package com.norwood.wfcore.common.worldgen.resource;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.synth.BlendedNoise;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

import com.gregtechceu.gtceu.api.data.worldgen.bedrockfluid.BedrockFluidVeinSavedData;
import com.gregtechceu.gtceu.api.registry.GTRegistries;
import com.gregtechceu.gtceu.config.ConfigHolder;

import com.mojang.datafixers.util.Either;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.deposit.DepositNode;
import com.norwood.wfcore.common.deposit.DepositRegion;
import com.norwood.wfcore.common.deposit.WFDeposits;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import com.norwood.wfcore.integration.warforge.WarforgeVeinAllocation;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Every {@link ResourceAllocation} for one level, compiled: zones wired to this level's noise, targets checked.
 * Evaluation mirrored by {@code worldgen-workspace} allocation.go; change both.
 */
public final class AllocationIndex {

    public static final AllocationIndex EMPTY = new AllocationIndex(0L, new EnumMap<>(ResourceSystem.class),
            new EnumMap<>(ResourceSystem.class), new EnumMap<>(ResourceSystem.class),
            new EnumMap<>(ResourceSystem.class), new EnumMap<>(ResourceSystem.class), new IdentityHashMap<>(),
            new IdentityHashMap<>(), List.of(), List.of(), 0);

    @FunctionalInterface
    interface Field {

        double at(double x, double z);
    }

    /** Weight rules on one target: density and richness multiply across rules. */
    public static final class Target {

        private final List<Field> density = new ArrayList<>();
        private final List<Field> richness = new ArrayList<>();

        public double at(double x, double z) {
            double m = 1.0;
            for (Field field : density) {
                m *= field.at(x, z);
            }
            return m;
        }

        public double richness(double x, double z) {
            double r = 1.0;
            for (Field field : richness) {
                r *= field.at(x, z);
            }
            return r;
        }

        private Target with(Target other) {
            Target t = new Target();
            t.density.addAll(density);
            t.density.addAll(other.density);
            t.richness.addAll(richness);
            t.richness.addAll(other.richness);
            return t;
        }
    }

    /** What a system cell holds before the stock roll; target "" => forced empty; rule -1 => painted. */
    public record Pick(String target, double richness, boolean painted, int rule) {}

    /** System cell of a chunk and the block point its fields are sampled at. */
    public record Cell(int x, int z, double sampleX, double sampleZ) {}

    private record AssignRule(int index, List<String> targets, double[] shares, Field density, Field richness,
            @Nullable Field select) {

        String pick(long seed, ResourceSystem system, Cell cell) {
            double s = select != null ? select.at(cell.sampleX(), cell.sampleZ())
                    : AllocationHash.unit(AllocationHash.hash(seed, AllocationHash.salt("select/" + system.id),
                            cell.x(), cell.z()));
            for (int k = 0; k < shares.length; k++) {
                if (s < shares[k]) {
                    return targets.get(k);
                }
            }
            return targets.getLast();
        }
    }

    private final long seed;
    private final EnumMap<ResourceSystem, Map<String, Target>> weights;
    private final EnumMap<ResourceSystem, Target> wildcard;
    private final EnumMap<ResourceSystem, List<AssignRule>> assign;
    private final EnumMap<ResourceSystem, Long2ObjectOpenHashMap<List<Pick>>> paint;
    private final EnumMap<ResourceSystem, Integer> grids;
    private final IdentityHashMap<PlacedFeature, Target> features;
    private final IdentityHashMap<PlacedFeature, String> featureIds;
    private final List<DepositNode> depositNodes;
    private final List<DepositRegion> depositRegions;
    private final int sources;
    // diagnostics (dump / at): every file's zones by name, rules in file order as {density, richness}
    private Map<String, Field> zoneFields = Map.of();
    private List<Field[]> ruleFields = List.of();

    private AllocationIndex(long seed, EnumMap<ResourceSystem, Map<String, Target>> weights,
            EnumMap<ResourceSystem, Target> wildcard, EnumMap<ResourceSystem, List<AssignRule>> assign,
            EnumMap<ResourceSystem, Long2ObjectOpenHashMap<List<Pick>>> paint,
            EnumMap<ResourceSystem, Integer> grids, IdentityHashMap<PlacedFeature, Target> features,
            IdentityHashMap<PlacedFeature, String> featureIds, List<DepositNode> depositNodes,
            List<DepositRegion> depositRegions, int sources) {
        this.seed = seed;
        this.weights = weights;
        this.wildcard = wildcard;
        this.assign = assign;
        this.paint = paint;
        this.grids = grids;
        this.features = features;
        this.featureIds = featureIds;
        this.depositNodes = depositNodes;
        this.depositRegions = depositRegions;
        this.sources = sources;
    }

    public boolean has(ResourceSystem system) {
        return weights.containsKey(system) || wildcard.containsKey(system) || assign.containsKey(system) ||
                paint.containsKey(system);
    }

    public boolean decides(ResourceSystem system) {
        return assign.containsKey(system) || paint.containsKey(system);
    }

    @Nullable
    public Target target(ResourceSystem system, String target) {
        Target t = weights.getOrDefault(system, Map.of()).get(target);
        return t != null ? t : wildcard.get(system);
    }

    /** Targets named by weight rules, for listings; {@code *} under its own key. */
    public Map<String, Target> weightRules(ResourceSystem system) {
        Map<String, Target> out = new HashMap<>(weights.getOrDefault(system, Map.of()));
        Target any = wildcard.get(system);
        if (any != null) {
            out.put("*", any);
        }
        return out;
    }

    public double multiplier(ResourceSystem system, String target, double x, double z) {
        Target t = target(system, target);
        return t == null ? 1.0 : t.at(x, z);
    }

    public double richness(ResourceSystem system, String target, double x, double z) {
        Target t = target(system, target);
        return t == null ? 1.0 : t.richness(x, z);
    }

    @Nullable
    public Target feature(PlacedFeature feature) {
        return features.isEmpty() ? null : features.get(feature);
    }

    /** Id of a placed feature some assign rule or paint names. */
    @Nullable
    public String decidedFeature(PlacedFeature feature) {
        return featureIds.isEmpty() ? null : featureIds.get(feature);
    }

    public Cell cell(ResourceSystem system, int chunkX, int chunkZ) {
        return switch (system) {
            case ORE_VEIN, BEDROCK_ORE -> {
                int g = grids.get(system);
                int cx = Math.floorDiv(chunkX, g);
                int cz = Math.floorDiv(chunkZ, g);
                yield new Cell(cx, cz, cx * g * 16 + 8.0, cz * g * 16 + 8.0);
            }
            case BEDROCK_FLUID -> {
                int g = grids.get(system);
                int cx = Math.floorDiv(chunkX, g);
                int cz = Math.floorDiv(chunkZ, g);
                double span = g * 16;
                yield new Cell(cx, cz, (cx + 0.5) * span, (cz + 0.5) * span);
            }
            default -> new Cell(chunkX, chunkZ, chunkX * 16 + 8.0, chunkZ * 16 + 8.0);
        };
    }

    /**
     * What the chunk's system cell holds before the stock roll; null => stock roll. Exclusive systems: one pick;
     * placed features: one per assigned or painted feature.
     */
    @Nullable
    public List<Pick> picks(ResourceSystem system, int chunkX, int chunkZ) {
        if (!decides(system)) {
            return null;
        }
        Cell cell = cell(system, chunkX, chunkZ);
        var painted = paint.get(system);
        List<Pick> held = painted == null ? null : painted.get(ChunkPos.asLong(cell.x(), cell.z()));
        List<AssignRule> rules = assign.getOrDefault(system, List.of());
        if (system.exclusive()) {
            if (held != null) {
                return held;
            }
            double u = AllocationHash.unit(AllocationHash.hash(seed, AllocationHash.salt(system.id), cell.x(),
                    cell.z()));
            double acc = 0.0;
            for (AssignRule r : rules) {
                double p = Math.min(r.density().at(cell.sampleX(), cell.sampleZ()), 1.0);
                if (u < acc + p) {
                    return List.of(new Pick(r.pick(seed, system, cell), r.richness().at(cell.sampleX(),
                            cell.sampleZ()), false, r.index()));
                }
                acc += p;
                if (acc >= 1.0) {
                    break;
                }
            }
            return null;
        }
        List<Pick> out = held == null ? new ArrayList<>() : new ArrayList<>(held);
        for (AssignRule r : rules) {
            for (String t : r.targets()) {
                if (out.stream().anyMatch(q -> q.target().equals(t))) {
                    continue;
                }
                double u = AllocationHash.unit(AllocationHash.hash(seed, AllocationHash.salt(system.id + "/" + t),
                        cell.x(), cell.z()));
                if (u < Math.min(r.density().at(cell.sampleX(), cell.sampleZ()), 1.0)) {
                    out.add(new Pick(t, r.richness().at(cell.sampleX(), cell.sampleZ()), false, r.index()));
                }
            }
        }
        return out.isEmpty() ? null : out;
    }

    public List<DepositNode> depositNodes() {
        return depositNodes;
    }

    public List<DepositRegion> depositRegions() {
        return depositRegions;
    }

    public int sources() {
        return sources;
    }

    public Set<String> zoneNames() {
        return zoneFields.keySet();
    }

    public double zone(String name, double x, double z) {
        return zoneFields.get(name).at(x, z);
    }

    public int ruleCount() {
        return ruleFields.size();
    }

    /** {density, richness} of rule i (file order, all files concatenated). */
    public double[] rule(int i, double x, double z) {
        Field[] f = ruleFields.get(i);
        return new double[] { f[0].at(x, z), f[1].at(x, z) };
    }

    public boolean isEmpty() {
        return sources == 0;
    }

    static AllocationIndex build(ServerLevel level) {
        Registry<ResourceAllocation> registry = level.registryAccess().registry(ResourceAllocation.REGISTRY)
                .orElse(null);
        if (registry == null) {
            return EMPTY;
        }
        long seed = level.getSeed();
        EnumMap<ResourceSystem, Map<String, Target>> weights = new EnumMap<>(ResourceSystem.class);
        EnumMap<ResourceSystem, Target> wildcard = new EnumMap<>(ResourceSystem.class);
        EnumMap<ResourceSystem, List<AssignRule>> assign = new EnumMap<>(ResourceSystem.class);
        EnumMap<ResourceSystem, Long2ObjectOpenHashMap<List<Pick>>> paint = new EnumMap<>(ResourceSystem.class);
        EnumMap<ResourceSystem, Integer> grids = defaultGrids();
        Set<String> decidedFeatures = new HashSet<>();
        List<DepositNode> nodes = new ArrayList<>();
        List<DepositRegion> regions = new ArrayList<>();
        Compiler compiler = new Compiler(seed, level.getChunkSource().randomState(),
                level.registryAccess().registryOrThrow(Registries.DENSITY_FUNCTION));
        int sources = 0;
        Map<String, Field> allZones = new HashMap<>();
        List<Field[]> allRules = new ArrayList<>();

        for (var entry : registry.entrySet()) {
            if (entry.getValue().dimension() == level.dimension()) {
                entry.getValue().grids().forEach((id, size) -> grids.put(ResourceSystem.byId(id).orElseThrow(
                        () -> new IllegalStateException(entry.getKey().location() + ": grids names unknown system " +
                                id)), size));
            }
        }
        for (var entry : registry.entrySet()) {
            ResourceAllocation allocation = entry.getValue();
            if (allocation.dimension() != level.dimension()) {
                continue;
            }
            sources++;
            String source = entry.getKey().location().toString();
            Map<String, Field> compiled = new HashMap<>();
            for (String name : allocation.zones().keySet()) {
                compiler.zone(source, name, allocation.zones(), compiled, new HashSet<>(), false);
            }
            for (int i = 0; i < allocation.rules().size(); i++) {
                ResourceAllocation.Rule rule = allocation.rules().get(i);
                Field density = chain(source, rule, rule.base(), compiled, false);
                Field richness = chain(source, rule, rule.richness(), compiled, true);
                allRules.add(new Field[] { density, richness });
                if (rule.assign()) {
                    for (String target : rule.targets()) {
                        if (target.isEmpty() && rule.system().exclusive()) {
                            continue;
                        }
                        checkTarget(level, source, rule.system(), target);
                        if (rule.system() == ResourceSystem.PLACED_FEATURE) {
                            decidedFeatures.add(target);
                        }
                    }
                    Field select = rule.select().map(z -> compiled(source, compiled, z)).orElse(null);
                    assign.computeIfAbsent(rule.system(), s -> new ArrayList<>()).add(new AssignRule(i,
                            rule.targets(), cumulative(rule), density, richness, select));
                    continue;
                }
                for (String target : rule.targets()) {
                    Target t;
                    if (target.equals("*")) {
                        t = wildcard.computeIfAbsent(rule.system(), s -> new Target());
                    } else {
                        checkTarget(level, source, rule.system(), target);
                        t = weights.computeIfAbsent(rule.system(), s -> new HashMap<>())
                                .computeIfAbsent(target, k -> new Target());
                    }
                    t.density.add(density);
                    t.richness.add(richness);
                }
            }
            allZones.putAll(compiled);
            for (var spec : allocation.paint()) {
                if (!spec.target().isEmpty()) {
                    checkTarget(level, source, spec.system(), spec.target());
                    if (spec.system() == ResourceSystem.PLACED_FEATURE) {
                        decidedFeatures.add(spec.target());
                    }
                }
                Pick pick = new Pick(spec.target(), spec.richness(), true, -1);
                var cells = paint.computeIfAbsent(spec.system(), s -> new Long2ObjectOpenHashMap<>());
                int g = grids.getOrDefault(spec.system(), 1);
                for (List<Integer> chunk : spec.chunks()) {
                    long key = ChunkPos.asLong(Math.floorDiv(chunk.get(0), g), Math.floorDiv(chunk.get(1), g));
                    if (spec.system().exclusive()) {
                        cells.put(key, List.of(pick));
                    } else {
                        List<Pick> list = new ArrayList<>(cells.getOrDefault(key, List.of()));
                        list.removeIf(q -> q.target().equals(spec.target()));
                        list.add(pick);
                        cells.put(key, List.copyOf(list));
                    }
                }
            }
            String dimension = level.dimension().location().toString();
            for (var spec : allocation.depositNodes()) {
                checkTarget(level, source, ResourceSystem.DEPOSIT, spec.type().toString());
                DepositNode.Builder node = DepositNode.builder(spec.type(), spec.x(), spec.z()).dimension(dimension);
                spec.size().ifPresent(node::size);
                spec.yield().ifPresent(node::yield);
                nodes.add(node.build());
            }
            for (var spec : allocation.depositRegions()) {
                checkTarget(level, source, ResourceSystem.DEPOSIT, spec.type().toString());
                DepositRegion.Builder region = DepositRegion.builder(spec.type()).dimension(dimension)
                        .from(spec.minX(), spec.minZ()).to(spec.maxX(), spec.maxZ()).count(spec.count());
                if (spec.minSize().isPresent() || spec.maxSize().isPresent()) {
                    region.size(spec.minSize().orElse(spec.maxSize().orElseThrow()),
                            spec.maxSize().orElse(spec.minSize().orElseThrow()));
                }
                spec.yield().ifPresent(region::yield);
                regions.add(region.build());
            }
        }
        if (sources == 0) {
            return EMPTY;
        }
        checkGrids(grids, assign.keySet(), paint.keySet());

        // wildcard fields fold into every named target; unnamed targets read the wildcard directly
        wildcard.forEach((system, any) -> weights.getOrDefault(system, Map.of())
                .replaceAll((target, t) -> t.with(any)));
        IdentityHashMap<PlacedFeature, Target> features = new IdentityHashMap<>();
        IdentityHashMap<PlacedFeature, String> featureIds = new IdentityHashMap<>();
        Registry<PlacedFeature> placed = level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        weights.getOrDefault(ResourceSystem.PLACED_FEATURE, Map.of()).forEach((target, t) ->
                features.put(placed.get(ResourceLocation.parse(target)), t));
        Target anyFeature = wildcard.get(ResourceSystem.PLACED_FEATURE);
        if (anyFeature != null) {
            placed.forEach(f -> features.putIfAbsent(f, anyFeature));
        }
        decidedFeatures.forEach(id -> featureIds.put(placed.get(ResourceLocation.parse(id)), id));
        AllocationIndex index = new AllocationIndex(seed, weights, wildcard, assign, paint, grids, features,
                featureIds, List.copyOf(nodes), List.copyOf(regions), sources);
        index.zoneFields = allZones;
        index.ruleFields = allRules;
        return index;
    }

    private static EnumMap<ResourceSystem, Integer> defaultGrids() {
        EnumMap<ResourceSystem, Integer> g = new EnumMap<>(ResourceSystem.class);
        g.put(ResourceSystem.ORE_VEIN, 3);
        g.put(ResourceSystem.BEDROCK_FLUID, 8);
        g.put(ResourceSystem.BEDROCK_ORE, 16);
        return g;
    }

    /** Cell geometry the pack was authored against must be the live GT geometry, or every cell lands elsewhere. */
    private static void checkGrids(EnumMap<ResourceSystem, Integer> grids, Set<ResourceSystem> assigned,
            Set<ResourceSystem> painted) {
        Map<ResourceSystem, Integer> live = Map.of(
                ResourceSystem.ORE_VEIN, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize,
                ResourceSystem.BEDROCK_FLUID, BedrockFluidVeinSavedData.VEIN_CHUNK_SIZE,
                ResourceSystem.BEDROCK_ORE, ConfigHolder.INSTANCE.worldgen.oreVeins.bedrockOreDistance);
        live.forEach((system, size) -> {
            if ((assigned.contains(system) || painted.contains(system)) && !grids.get(system).equals(size)) {
                throw new IllegalStateException("resource allocation assumes " + system.id + " cells of " +
                        grids.get(system) + " chunks; GregTech uses " + size);
            }
        });
    }

    private static double[] cumulative(ResourceAllocation.Rule rule) {
        int n = rule.targets().size();
        List<Double> shares = rule.shares().orElse(null);
        if (shares != null && shares.size() != n) {
            throw new IllegalStateException("rule " + rule.targets() + ": " + shares.size() + " shares");
        }
        double total = 0.0;
        for (int k = 0; k < n; k++) {
            total += shares != null ? shares.get(k) : 1.0;
        }
        double[] out = new double[n];
        double acc = 0.0;
        for (int k = 0; k < n; k++) {
            acc += shares != null ? shares.get(k) : 1.0;
            out[k] = acc / total;
        }
        return out;
    }

    private static Field compiled(String source, Map<String, Field> zones, String name) {
        Field f = zones.get(name);
        if (f == null) {
            throw new IllegalStateException(source + ": unknown zone " + name);
        }
        return f;
    }

    /** m = base; per layer m = lerp(m, value, zone); m >= 0. Richness chain skips layers without one. */
    private static Field chain(String source, ResourceAllocation.Rule rule, double base, Map<String, Field> zones,
            boolean richness) {
        List<Field> fields = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        for (var layer : rule.layers()) {
            Field f = compiled(source, zones, layer.zone());
            if (richness) {
                if (layer.richness().isEmpty()) {
                    continue;
                }
                values.add(layer.richness().get());
            } else {
                values.add(layer.weight());
            }
            fields.add(f);
        }
        int n = fields.size();
        Field[] fs = fields.toArray(Field[]::new);
        double[] vs = values.stream().mapToDouble(Double::doubleValue).toArray();
        return (x, z) -> {
            double m = base;
            for (int i = 0; i < n; i++) {
                double t = fs[i].at(x, z);
                m += (vs[i] - m) * t;
            }
            return Math.max(0.0, m);
        };
    }

    private record Compiler(long seed, RandomState randomState, Registry<DensityFunction> functions) {

        Field zone(String source, String name, Map<String, Zone> zones, Map<String, Field> compiled,
                Set<String> visiting, boolean warped) {
            Field done = compiled.get(name);
            if (done != null && !warped) {
                return done;
            }
            Zone zone = zones.get(name);
            if (zone == null) {
                throw new IllegalStateException(source + ": unknown zone " + name);
            }
            if (!visiting.add(name)) {
                throw new IllegalStateException(source + ": zone cycle through " + name);
            }
            Field field = build(source, name, zone, zones, compiled, visiting, warped);
            visiting.remove(name);
            if (!warped) {
                compiled.put(name, field);
            }
            return field;
        }

        private Field[] parts(String source, List<String> names, Map<String, Zone> zones,
                Map<String, Field> compiled, Set<String> visiting, boolean warped) {
            Field[] parts = new Field[names.size()];
            for (int i = 0; i < parts.length; i++) {
                parts[i] = zone(source, names.get(i), zones, compiled, visiting, warped);
            }
            return parts;
        }

        private Field build(String source, String name, Zone zone, Map<String, Zone> zones,
                Map<String, Field> compiled, Set<String> visiting, boolean warped) {
            return switch (zone) {
                case Zone.Circle c -> (x, z) -> Zone.ramp(AllocationHash.dist(x - c.x(), z - c.z()) - c.radius(),
                        c.falloff());
                case Zone.Rect r -> (x, z) -> {
                    double dx = Math.max(r.minX() - x, x - r.maxX());
                    double dz = Math.max(r.minZ() - z, z - r.maxZ());
                    double outside = dx > 0 && dz > 0 ? AllocationHash.dist(dx, dz) : Math.max(dx, dz);
                    return Zone.ramp(outside, r.falloff());
                };
                case Zone.Density d -> {
                    if (warped) {
                        throw new IllegalStateException(source + ": zone " + name + " reads density under a warp");
                    }
                    yield density(source, d);
                }
                case Zone.Noise n -> {
                    NormalNoise noise = noise(source, n.noise(), n.seed().orElse(name));
                    double s = n.xzScale();
                    boolean thresholded = n.min().isPresent() || n.max().isPresent();
                    Band band = new Band(n.min(), n.max(), n.falloff());
                    yield (x, z) -> {
                        double v = noise.getValue(x * s, 0.0, z * s);
                        return thresholded ? band.at(v) : Math.min(Math.max((v + 1.0) * 0.5, 0.0), 1.0);
                    };
                }
                case Zone.Cells c -> {
                    if (c.size() <= 0) {
                        throw new IllegalStateException(source + ": zone " + name + " cells size must be > 0");
                    }
                    long salt = AllocationHash.salt(c.seed().orElse(name));
                    boolean thresholded = c.min().isPresent() || c.max().isPresent();
                    double lo = c.min().orElse(0.0);
                    double hi = c.max().orElse(1.0);
                    yield (x, z) -> {
                        double[] cell = AllocationHash.cell(seed, salt, c.size(), c.jitter(), x, z);
                        if (!thresholded) {
                            return cell[0];
                        }
                        if (cell[0] < lo || cell[0] >= hi) {
                            return 0.0;
                        }
                        return Zone.ramp(c.falloff() - cell[1], c.falloff());
                    };
                }
                case Zone.Combine cb -> {
                    Field[] parts = parts(source, cb.zones(), zones, compiled, visiting, warped);
                    List<Double> ws = cb.weights().orElse(null);
                    if (ws != null && ws.size() != parts.length) {
                        throw new IllegalStateException(source + ": zone " + name + " has " + ws.size() +
                                " weights for " + parts.length + " zones");
                    }
                    double[] w = new double[parts.length];
                    for (int k = 0; k < w.length; k++) {
                        w[k] = ws != null ? ws.get(k) : 1.0;
                    }
                    yield switch (cb.type()) {
                        case "intersect" -> (x, z) -> {
                            double m = 1.0;
                            for (Field p : parts) {
                                m = Math.min(m, p.at(x, z));
                            }
                            return m;
                        };
                        case "union" -> (x, z) -> {
                            double m = 0.0;
                            for (Field p : parts) {
                                m = Math.max(m, p.at(x, z));
                            }
                            return m;
                        };
                        case "multiply" -> (x, z) -> {
                            double m = 1.0;
                            for (Field p : parts) {
                                m *= p.at(x, z);
                            }
                            return m;
                        };
                        case "add" -> (x, z) -> {
                            double m = 0.0;
                            for (Field p : parts) {
                                m += p.at(x, z);
                            }
                            return Math.min(m, 1.0);
                        };
                        default -> (x, z) -> {
                            double m = 0.0;
                            double sum = 0.0;
                            for (int k = 0; k < parts.length; k++) {
                                m += w[k] * parts[k].at(x, z);
                                sum += w[k];
                            }
                            return sum <= 0.0 ? 0.0 : m / sum;
                        };
                    };
                }
                case Zone.Subtract sb -> {
                    Field inner = zone(source, sb.zone(), zones, compiled, visiting, warped);
                    Field[] minus = parts(source, sb.subtract(), zones, compiled, visiting, warped);
                    yield (x, z) -> {
                        double m = inner.at(x, z);
                        for (Field p : minus) {
                            m -= p.at(x, z);
                        }
                        return Math.max(m, 0.0);
                    };
                }
                case Zone.Invert inv -> {
                    Field inner = zone(source, inv.zone(), zones, compiled, visiting, warped);
                    yield (x, z) -> 1.0 - inner.at(x, z);
                }
                case Zone.Remap rm -> {
                    if (rm.low() == rm.high()) {
                        throw new IllegalStateException(source + ": zone " + name + " remap low == high");
                    }
                    Field inner = zone(source, rm.zone(), zones, compiled, visiting, warped);
                    yield (x, z) -> Zone.smooth(Math.min(Math.max((inner.at(x, z) - rm.low()) /
                            (rm.high() - rm.low()), 0.0), 1.0));
                }
                case Zone.Warp wp -> {
                    Field inner = zone(source, wp.zone(), zones, compiled, visiting, true);
                    String s = wp.seed().orElse(name);
                    NormalNoise nx = noise(source, wp.noise(), s + "/x");
                    NormalNoise nz = noise(source, wp.noise(), s + "/z");
                    double scale = wp.xzScale();
                    double amp = wp.amplitude();
                    yield (x, z) -> {
                        double sx = x * scale;
                        double sz = z * scale;
                        return inner.at(x + amp * nx.getValue(sx, 0.0, sz), z + amp * nz.getValue(sx, 0.0, sz));
                    };
                }
            };
        }

        private NormalNoise noise(String source, Either<ResourceLocation, Zone.InlineNoise> spec, String seed) {
            return spec.map(
                    id -> randomState.getOrCreateNoise(ResourceKey.create(Registries.NOISE, id)),
                    inline -> NormalNoise.create(randomState.getOrCreateRandomFactory(WFCore.id("resource_zone"))
                            .fromHashOf(seed), new NormalNoise.NoiseParameters(inline.firstOctave(),
                                    inline.amplitudes())));
        }

        private Field density(String source, Zone.Density d) {
            Holder<DensityFunction> holder = functions
                    .getHolder(ResourceKey.create(Registries.DENSITY_FUNCTION, d.function()))
                    .orElseThrow(() -> new IllegalStateException(source + ": unknown density function " +
                            d.function()));
            DensityFunction function = holder.value().mapAll(new Wiring(randomState));
            Band band = new Band(d.min(), d.max(), d.falloff());
            int y = d.y();
            return (x, z) -> band.at(function.compute(new DensityFunction.SinglePointContext((int) Math.floor(x), y,
                    (int) Math.floor(z))));
        }
    }

    /** Ramps in from min and max; absent side unbounded. */
    private record Band(Optional<Double> min, Optional<Double> max, double falloff) {

        double at(double v) {
            double m = 1.0;
            if (min.isPresent()) {
                m *= Zone.ramp(min.get() - v, falloff);
            }
            if (max.isPresent()) {
                m *= Zone.ramp(v - max.get(), falloff);
            }
            return m;
        }
    }

    private static void checkTarget(ServerLevel level, String source, ResourceSystem system, String target) {
        ResourceLocation id = ResourceLocation.tryParse(target);
        boolean known = switch (system) {
            case DEPOSIT -> id != null && WFDeposits.get(id) != null;
            case ORE_VEIN -> id != null && level.registryAccess().registryOrThrow(GTRegistries.Keys.ORE_VEIN)
                    .containsKey(id);
            case BEDROCK_FLUID -> id != null && level.registryAccess()
                    .registryOrThrow(GTRegistries.Keys.BEDROCK_FLUID).containsKey(id);
            case BEDROCK_ORE -> id != null && level.registryAccess().registryOrThrow(GTRegistries.Keys.BEDROCK_ORE)
                    .containsKey(id);
            case PLACED_FEATURE -> id != null && level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE)
                    .containsKey(id);
            // Optional mod: absent => rule inert.
            case WARFORGE_VEIN -> !WarforgeIntegration.isLoaded() || WarforgeVeinAllocation.knows(target);
        };
        if (!known) {
            throw new IllegalStateException(source + ": unknown " + system.id + " target " + target);
        }
    }

    /** {@link RandomState}'s private noise wiring, reproduced for functions outside the noise router. */
    public record Wiring(RandomState randomState) implements DensityFunction.Visitor {

        @Override
        public DensityFunction apply(DensityFunction function) {
            if (function instanceof DensityFunctions.HolderHolder holder) {
                return holder.function().value();
            }
            if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                return marker.wrapped();
            }
            if (function instanceof BlendedNoise) {
                // TODO: seed like RandomState if a zone ever needs it. end_islands likewise, unchecked (protected).
                throw new IllegalStateException("resource zones cannot sample " + function.getClass().getSimpleName());
            }
            return function;
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            var key = noise.noiseData().unwrapKey()
                    .orElseThrow(() -> new IllegalStateException("resource zones need registered noises"));
            return new DensityFunction.NoiseHolder(noise.noiseData(), randomState.getOrCreateNoise(key));
        }
    }
}
