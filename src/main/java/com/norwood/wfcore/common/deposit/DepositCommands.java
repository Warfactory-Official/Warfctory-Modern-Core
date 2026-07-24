package com.norwood.wfcore.common.deposit;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.norwood.wfcore.common.machine.DepositBlockEntity;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

public final class DepositCommands {

    public static final DepositCommands INSTANCE = new DepositCommands();

    /** Chunk radius cap for the outward search (~16k blocks); large enough to find even sparse naquadah. */
    private static final int MAX_CHUNK_RADIUS = 1024;

    private DepositCommands() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_deposit")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("locate")
                        .executes(ctx -> locate(ctx, null))
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(typeIds(), b))
                                .executes(ctx -> locate(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("tp")
                        .then(Commands.argument("type", StringArgumentType.string())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(typeIds(), b))
                                .executes(ctx -> teleport(ctx, StringArgumentType.getString(ctx, "type")))))
                .then(Commands.literal("count")
                        .executes(ctx -> count(ctx, 8))
                        .then(Commands.argument("chunkRadius", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> count(ctx, IntegerArgumentType.getInteger(ctx, "chunkRadius"))))));
    }

    private static List<String> typeIds() {
        List<String> ids = new ArrayList<>();
        for (DepositType t : WFDeposits.all()) {
            ids.add(t.id().toString());
        }
        return ids;
    }

    /** Resolve a bare or namespaced id against the registry; {@code null} if unknown. */
    @Nullable
    private static ResourceLocation resolveType(String raw) {
        ResourceLocation direct = ResourceLocation.tryParse(raw.indexOf(':') >= 0 ? raw : "wfcore:" + raw);
        if (direct != null && WFDeposits.get(direct) != null) {
            return direct;
        }
        for (DepositType t : WFDeposits.all()) {
            if (t.id().getPath().equals(raw) || t.id().toString().equals(raw)) {
                return t.id();
            }
        }
        return null;
    }

    private int locate(CommandContext<CommandSourceStack> ctx, @Nullable String typeArg) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());

        List<ResourceLocation> types = new ArrayList<>();
        if (typeArg != null) {
            ResourceLocation t = resolveType(typeArg);
            if (t == null) {
                src.sendFailure(Component.literal("Unknown deposit type: " + typeArg));
                return 0;
            }
            types.add(t);
        } else {
            for (DepositType t : WFDeposits.all()) {
                types.add(t.id());
            }
        }
        if (types.isEmpty()) {
            src.sendFailure(Component.literal("No deposit types registered."));
            return 0;
        }

        src.sendSuccess(() -> Component.literal("§7Nearest deposits to §f" + at.getX() + ", " + at.getZ()
                + "§7 in §f" + level.dimension().location() + "§7:"), false);
        int found = 0;
        for (ResourceLocation type : types) {
            int[] pos = nearest(level, type, at.getX(), at.getZ());
            if (pos == null) {
                src.sendSuccess(() -> Component.literal("  §8" + type + ": none within "
                        + (MAX_CHUNK_RADIUS * 16) + " blocks"), false);
                continue;
            }
            found++;
            long dist = Math.round(Math.sqrt(dist2(pos[0], pos[1], at.getX(), at.getZ())));
            src.sendSuccess(() -> Component.literal("  §a" + type.getPath() + "§7 at §f" + pos[0] + ", " + pos[1]
                    + "§7 (§f" + dist + "§7 blocks) — §e/tp @s " + pos[0] + " ~ " + pos[1]), false);
        }
        return found;
    }

    private int teleport(CommandContext<CommandSourceStack> ctx, String typeArg) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("Only a player can teleport."));
            return 0;
        }
        ResourceLocation type = resolveType(typeArg);
        if (type == null) {
            src.sendFailure(Component.literal("Unknown deposit type: " + typeArg));
            return 0;
        }
        BlockPos at = player.blockPosition();
        int[] pos = nearest(level, type, at.getX(), at.getZ());
        if (pos == null) {
            src.sendFailure(Component.literal("No " + type.getPath() + " deposit within "
                    + (MAX_CHUNK_RADIUS * 16) + " blocks."));
            return 0;
        }
        int surfaceY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos[0], pos[1]);
        player.teleportTo(level, pos[0] + 0.5, surfaceY, pos[1] + 0.5, player.getYRot(), player.getXRot());
        src.sendSuccess(() -> Component.literal("§aTeleported above the nearest " + type.getPath()
                + " deposit at §f" + pos[0] + ", " + pos[1] + "§a. It sits on bedrock (~y-59) below — dig down, "
                + "use spectator, or open the Ore Prospector here."), false);
        return 1;
    }

    private int count(CommandContext<CommandSourceStack> ctx, int chunkRadius) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        BlockPos at = BlockPos.containing(src.getPosition());
        int ccx = at.getX() >> 4;
        int ccz = at.getZ() >> 4;

        java.util.Map<ResourceLocation, Integer> counts = new java.util.LinkedHashMap<>();
        int scannedChunks = 0;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(ccx + dx, ccz + dz);
                if (chunk == null) {
                    continue;
                }
                scannedChunks++;
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof DepositBlockEntity deposit && deposit.getDepositTypeId() != null) {
                        counts.merge(deposit.getDepositTypeId(), 1, Integer::sum);
                    }
                }
            }
        }

        int total = counts.values().stream().mapToInt(Integer::intValue).sum();
        int loaded = scannedChunks;
        src.sendSuccess(() -> Component.literal("§7Deposit blocks in §f" + loaded + "§7 loaded chunks (radius "
                + chunkRadius + "): §f" + total), false);
        if (counts.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  §8none here — teleport onto a deposit first "
                    + "(/wfcore_deposit tp <type>), then recount"), false);
        } else {
            counts.forEach((type, n) -> src.sendSuccess(() -> Component.literal("  §a" + type.getPath()
                    + "§7: §f" + n + "§7 blocks"), false));
        }
        return total;
    }

    /** Nearest predicted deposit origin (block x/z) of {@code type} to (cx, cz), or {@code null} if none in range. */
    @Nullable
    private static int[] nearest(ServerLevel level, ResourceLocation type, int cx, int cz) {
        long seed = level.getSeed();
        ResourceLocation dim = level.dimension().location();
        List<DepositNode> nodes = new ArrayList<>();
        for (DepositNode n : WFDeposits.nodesIn(dim)) {
            if (n.type().equals(type)) {
                nodes.add(n);
            }
        }
        List<DepositRegion> regions = new ArrayList<>();
        for (DepositRegion r : WFDeposits.regionsIn(dim)) {
            if (r.type().equals(type)) {
                regions.add(r);
            }
        }
        if (nodes.isEmpty() && regions.isEmpty()) {
            return null;
        }

        int ccx = cx >> 4;
        int ccz = cz >> 4;
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int r = 0; r <= MAX_CHUNK_RADIUS; r++) {
            if (best != null) {
                long minPossible = (long) Math.max(0, r - 1) * 16;
                if (minPossible * minPossible > bestD) {
                    break;
                }
            }
            long[] acc = {bestD};
            int[] hit = scanRing(nodes, regions, seed, ccx, ccz, r, cx, cz, acc);
            if (hit != null) {
                best = hit;
                bestD = acc[0];
            }
        }
        return best;
    }

    /** Scan the perimeter of chunk-ring {@code r}; return the best origin found on it (updating {@code acc[0]}). */
    @Nullable
    private static int[] scanRing(List<DepositNode> nodes, List<DepositRegion> regions, long seed,
                                  int ccx, int ccz, int r, int cx, int cz, long[] acc) {
        int[] best = null;
        if (r == 0) {
            return scanChunk(nodes, regions, seed, ccx, ccz, cx, cz, acc);
        }
        for (int i = -r; i <= r; i++) {
            int[] a = scanChunk(nodes, regions, seed, ccx + i, ccz - r, cx, cz, acc);
            if (a != null) best = a;
            int[] b = scanChunk(nodes, regions, seed, ccx + i, ccz + r, cx, cz, acc);
            if (b != null) best = b;
        }
        for (int j = -r + 1; j <= r - 1; j++) {
            int[] a = scanChunk(nodes, regions, seed, ccx - r, ccz + j, cx, cz, acc);
            if (a != null) best = a;
            int[] b = scanChunk(nodes, regions, seed, ccx + r, ccz + j, cx, cz, acc);
            if (b != null) best = b;
        }
        return best;
    }

    @Nullable
    private static int[] scanChunk(List<DepositNode> nodes, List<DepositRegion> regions, long seed,
                                   int chunkX, int chunkZ, int cx, int cz, long[] acc) {
        int[] best = null;
        for (DepositNode n : nodes) {
            if (n.inChunk(chunkX, chunkZ)) {
                long d = dist2(n.x(), n.z(), cx, cz);
                if (d < acc[0]) {
                    acc[0] = d;
                    best = new int[]{n.x(), n.z()};
                }
            }
        }
        for (DepositRegion reg : regions) {
            BlockPos o = reg.chosenOrigin(chunkX, chunkZ, seed);
            if (o != null) {
                long d = dist2(o.getX(), o.getZ(), cx, cz);
                if (d < acc[0]) {
                    acc[0] = d;
                    best = new int[]{o.getX(), o.getZ()};
                }
            }
        }
        return best;
    }

    private static long dist2(int x, int z, int cx, int cz) {
        long dx = x - cx;
        long dz = z - cz;
        return dx * dx + dz * dz;
    }
}
