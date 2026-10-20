package com.norwood.wfcore.common.worldgen.rock;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.registries.DataPackRegistryEvent;

import com.google.common.collect.MapMaker;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.worldgen.resource.AllocationIndex;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Surface-relative deeprock / deepslate, {@code data/<ns>/wfcore/rock_layers/<id>.json} from
 * {@code worldgen-workspace}. Layer top = {@code max(floor, floor(surface) - depth)}, dither over
 * {@code [top - blend, top)}. Formulas duplicated in {@code wfworld internal/world/rocklayers.go}: change both.
 */
public record RockLayers(ResourceKey<Level> dimension, DensityFunction surface, int blend, Layer deeprock,
        Layer deepslate, GtFrame gtFrame) {

    public static final ResourceKey<Registry<RockLayers>> REGISTRY =
            ResourceKey.createRegistryKey(WFCore.id("rock_layers"));

    public record Layer(int depth, int floor) {

        static final Codec<Layer> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("depth").forGetter(Layer::depth),
                Codec.INT.fieldOf("floor").forGetter(Layer::floor)).apply(i, Layer::new));

        public int top(double surface) {
            return Math.max(floor, Mth.floor(surface) - depth);
        }
    }

    /** GT heightRange y of the deeprock top, deepslate top and surface the vein heights were authored against. */
    public record GtFrame(int deeprock, int deepslate, int surface) {

        static final Codec<GtFrame> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("deeprock").forGetter(GtFrame::deeprock),
                Codec.INT.fieldOf("deepslate").forGetter(GtFrame::deepslate),
                Codec.INT.fieldOf("surface").forGetter(GtFrame::surface)).apply(i, GtFrame::new));
    }

    public static final Codec<RockLayers> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceKey.codec(Registries.DIMENSION).fieldOf("dimension").forGetter(RockLayers::dimension),
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("surface").forGetter(RockLayers::surface),
            Codec.INT.fieldOf("blend").forGetter(RockLayers::blend),
            Layer.CODEC.fieldOf("deeprock").forGetter(RockLayers::deeprock),
            Layer.CODEC.fieldOf("deepslate").forGetter(RockLayers::deepslate),
            GtFrame.CODEC.fieldOf("gt_frame").forGetter(RockLayers::gtFrame)).apply(i, RockLayers::new));

    public static void register(DataPackRegistryEvent.NewRegistry event) {
        event.dataPackRegistry(REGISTRY, CODEC);
    }

    // surface wired per RandomState (noises seeded by the level); memo per thread => shared across generator threads
    private static final Map<RandomState, Map<RockLayers, DensityFunction>> WIRED = new MapMaker().weakKeys()
            .makeMap();

    public static Optional<RockLayers> of(ServerLevel level) {
        return level.registryAccess().registryOrThrow(REGISTRY).stream()
                .filter(l -> l.dimension.equals(level.dimension())).findFirst();
    }

    public DensityFunction surface(RandomState randomState) {
        return WIRED.computeIfAbsent(randomState, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(this, l -> l.surface.mapAll(new MemoWiring(new AllocationIndex.Wiring(randomState),
                        new HashMap<>())));
    }

    /** Wiring + one shared per-point memo per referenced function: inlined copies otherwise recompute (~6x). */
    private record MemoWiring(AllocationIndex.Wiring wiring, Map<DensityFunction, DensityFunction> memo)
            implements DensityFunction.Visitor {

        @Override
        public DensityFunction apply(DensityFunction function) {
            DensityFunction wired = wiring.apply(function);
            return function instanceof DensityFunctions.HolderHolder ? memo.computeIfAbsent(wired, Memo::new) : wired;
        }

        @Override
        public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise) {
            return wiring.visitNoise(noise);
        }
    }

    /** Last point per thread; identity equality (one instance per distinct function). */
    private static final class Memo implements DensityFunction {

        private final DensityFunction wrapped;
        private final ThreadLocal<double[]> last = ThreadLocal.withInitial(() -> new double[] { Double.NaN, 0, 0, 0 });

        Memo(DensityFunction wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public double compute(FunctionContext ctx) {
            double[] l = last.get();
            if (l[1] != ctx.blockX() || l[2] != ctx.blockY() || l[3] != ctx.blockZ() || Double.isNaN(l[0])) {
                l[1] = ctx.blockX();
                l[2] = ctx.blockY();
                l[3] = ctx.blockZ();
                l[0] = wrapped.compute(ctx);
            }
            return l[0];
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider) {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor) {
            return visitor.apply(new Memo(wrapped.mapAll(visitor)));
        }

        @Override
        public double minValue() {
            return wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return wrapped.maxValue();
        }

        @Override
        public net.minecraft.util.KeyDispatchDataCodec<? extends DensityFunction> codec() {
            return wrapped.codec();
        }
    }

    public Layer layer(String name) {
        return switch (name) {
            case "deeprock" -> deeprock;
            case "deepslate" -> deepslate;
            default -> throw new IllegalArgumentException("rock layer " + name);
        };
    }

    public static double corner(DensityFunction surface, int x, int z) {
        return surface.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /** Surface at block column (x, z): bilinear over the 4-block quart grid. */
    public static double column(DensityFunction surface, int x, int z) {
        int x0 = x & ~3, z0 = z & ~3;
        return Mth.lerp2((x & 3) / 4.0, (z & 3) / 4.0, corner(surface, x0, z0), corner(surface, x0 + 4, z0),
                corner(surface, x0, z0 + 4), corner(surface, x0 + 4, z0 + 4));
    }

    /**
     * GT vein y -> world y at column surface s: stone frame [gt deepslate, gt surface] onto [deepslate top, surface],
     * deepslate frame [gt deeprock, gt deepslate] onto [deeprock top, deepslate top], extended below.
     */
    public int gtY(int y, double s) {
        int sf = Mth.floor(s);
        int t = deeprock.top(s), u = deepslate.top(s);
        double k = y >= gtFrame.deepslate ? Math.max(0, sf - u) / (double) (gtFrame.surface - gtFrame.deepslate)
                : (u - t) / (double) (gtFrame.deepslate - gtFrame.deeprock);
        return Mth.floor(u + (y - gtFrame.deepslate) * k);
    }
}
