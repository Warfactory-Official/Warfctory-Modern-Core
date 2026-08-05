package com.norwood.wfcore.common.worldgen.transplant;

import com.gregtechceu.gtceu.config.ConfigHolder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;

/**
 * Operator surface for the nether -> overworld vein migration. Permission level 4: the backfill
 * rewrites the world and is meant to run offline on a backed-up save.
 *
 * <pre>
 *   /wfcore_veins transplant status          list the overworld copies registered this reload
 *   /wfcore_veins backfill start [perTick]    begin backfilling the CURRENT dimension (offline)
 *   /wfcore_veins backfill stop               pause; resumes from the progress file on next start
 *   /wfcore_veins backfill status             progress / ETA
 *   /wfcore_veins backfill here               run a single origin at your location (spot test)
 * </pre>
 */
public final class VeinBackfillCommand {

    public static final VeinBackfillCommand INSTANCE = new VeinBackfillCommand();

    private VeinBackfillCommand() {}

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("wfcore_veins")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("transplant")
                        .then(Commands.literal("status").executes(this::transplantStatus)))
                .then(Commands.literal("backfill")
                        .then(Commands.literal("start")
                                .executes(ctx -> start(ctx, 4))
                                .then(Commands.argument("perTick", IntegerArgumentType.integer(1, 256))
                                        .executes(ctx -> start(ctx, IntegerArgumentType.getInteger(ctx, "perTick")))))
                        .then(Commands.literal("stop").executes(this::stop))
                        .then(Commands.literal("status").executes(this::backfillStatus))
                        .then(Commands.literal("here").executes(this::here))));
    }

    private int transplantStatus(CommandContext<CommandSourceStack> ctx) {
        var ids = VeinTransplant.transplantIds();
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Registered " + ids.size() + " overworld transplant vein(s) on layer '"
                        + VeinTransplant.LAYER_NAME + "'."), false);
        for (ResourceLocation id : ids) {
            ctx.getSource().sendSuccess(() -> Component.literal("  " + id), false);
        }
        return ids.size();
    }

    private int start(CommandContext<CommandSourceStack> ctx, int perTick) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();

        if (VeinBackfillRunner.INSTANCE.isBusy()) {
            src.sendFailure(Component.literal("Backfill already running: "
                    + VeinBackfillRunner.INSTANCE.status()));
            return 0;
        }
        if (src.getServer().getPlayerList().getPlayerCount() > 0) {
            src.sendFailure(Component.literal(
                    "Refusing to start: this is an OFFLINE migration - NO players may be online, "
                            + "including you. Run it from the server console with everyone disconnected, "
                            + "and back up the world first. (Currently loaded base/chunk-loader chunks are "
                            + "skipped automatically.)"));
            return 0;
        }
        if (VeinTransplant.transplantIds().isEmpty()) {
            src.sendFailure(Component.literal(
                    "No transplant veins are registered - nothing to backfill. "
                            + "Check that nether veins were transplanted (see console on world load)."));
            return 0;
        }

        boolean started = VeinBackfillRunner.INSTANCE.start(level, perTick);
        if (started) {
            src.sendSuccess(() -> Component.literal(
                    "Started vein backfill of " + level.dimension().location() + " at " + perTick
                            + " origins/tick. Watch console; use '/wfcore_veins backfill status'."), true);
            return 1;
        }
        src.sendFailure(Component.literal("Could not start backfill."));
        return 0;
    }

    private int stop(CommandContext<CommandSourceStack> ctx) {
        VeinBackfillRunner.INSTANCE.stop();
        ctx.getSource().sendSuccess(() -> Component.literal("Backfill stopped: "
                + VeinBackfillRunner.INSTANCE.status()), true);
        return 1;
    }

    private int backfillStatus(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(VeinBackfillRunner.INSTANCE.status()), false);
        return 1;
    }

    /** Runs a single origin at the caller's location - useful for validating on a world copy. */
    private int here(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        ServerLevel level = src.getLevel();
        int gridSize = Math.max(1, ConfigHolder.INSTANCE.worldgen.oreVeins.oreVeinGridSize);
        ChunkPos here = new ChunkPos(net.minecraft.core.BlockPos.containing(src.getPosition()));
        ChunkPos origin = new ChunkPos(
                Math.floorDiv(here.x, gridSize) * gridSize,
                Math.floorDiv(here.z, gridSize) * gridSize);

        VeinBackfill.Result r = VeinBackfill.placeAt(level, origin, cp -> true);
        src.sendSuccess(() -> Component.literal(
                "Origin " + origin + ": placed " + r.veinsPlaced() + " transplant vein(s) into "
                        + r.chunksTouched() + " chunk(s)."), true);
        return r.veinsPlaced();
    }
}
