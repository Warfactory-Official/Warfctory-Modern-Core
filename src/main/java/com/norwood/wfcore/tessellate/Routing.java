package com.norwood.wfcore.tessellate;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * Foreign-region delivery for vanilla command mixins. Region thread + target not owned => owner thread
 * ({@link RegionThreads#onRegion}) or server thread (volumes); any other thread => inline.
 */
public final class Routing {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Score read-modify-write across threads; held around whole command bodies, never nested under a Scoreboard monitor. */
    public static final Object SCORE_LOCK = new Object();

    private Routing() {
    }

    public static boolean foreign(ServerLevel level, BlockPos pos) {
        return RegionThreads.isRegionThread() && !RegionThreads.owns(level, pos);
    }

    /** Server-thread write into a block entity/entity: waits for the owning region to be idle (as main-thread {@code Level.setBlock} does). */
    public static void leaseOnMain(ServerLevel level, BlockPos pos) {
        if (level.getServer().isSameThread()) {
            RegionThreads.lease(level, ChunkPos.asLong(pos));
        }
    }

    public static boolean foreign(Entity entity) {
        return entity.level() instanceof ServerLevel level && foreign(level, entity.blockPosition());
    }

    /** True when the caller is not on a region thread or owns every chunk column of {@code box}. */
    public static boolean ownsAll(ServerLevel level, BoundingBox box) {
        if (!RegionThreads.isRegionThread()) {
            return true;
        }
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++) {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++) {
                if (!RegionThreads.owns(level, new BlockPos(cx << 4, 0, cz << 4))) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Foreign {@code pos}: {@code op} runs on its owner, returns true. Owned/off-region: false, caller runs inline. */
    public static boolean toOwner(String what, ServerLevel level, BlockPos pos, Runnable op) {
        if (!foreign(level, pos)) {
            return false;
        }
        LOGGER.debug("{} -> owner of {} in {}", what, pos, level.dimension().location());
        RegionThreads.onRegion(what, level, pos, op);
        return true;
    }

    public static boolean toOwner(String what, Entity entity, Runnable op) {
        return entity.level() instanceof ServerLevel level && toOwner(what, level, entity.blockPosition(), op);
    }

    /** Command body that may throw: failure on replay goes to the source, as vanilla would have. */
    public static Runnable reporting(CommandSourceStack source, ThrowingRun body) {
        return () -> {
            try {
                body.run();
            } catch (CommandSyntaxException e) {
                source.sendFailure(ComponentUtils.fromMessage(e.getRawMessage()));
            }
        };
    }

    @FunctionalInterface
    public interface ThrowingRun {
        void run() throws CommandSyntaxException;
    }
}
