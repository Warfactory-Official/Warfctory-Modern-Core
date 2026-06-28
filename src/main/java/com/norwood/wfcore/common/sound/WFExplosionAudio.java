package com.norwood.wfcore.common.sound;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Plays an explosion as a directional, distance-aware event. Every nearby player gets the blast as a positional
 * sound (the client pans/attenuates it by direction and distance), delayed by the time sound would take to reach
 * them. Close listeners hear the sharp {@code mining_charge_blast}; anyone past {@link #NEAR_RANGE} hears the
 * recorded distant variant instead.
 */
public final class WFExplosionAudio {

    /** ~340 m/s at 20 tps ≈ 17 blocks per tick. */
    private static final double BLOCKS_PER_TICK = 17.0;
    /** Within this, you hear the close blast; beyond it, the distant rumble. */
    private static final double NEAR_RANGE = 24.0;
    private static final float NEAR_VOLUME = 4.0F;
    private static final float DISTANT_VOLUME = 8.0F;
    /** Variable-range audibility of the loud distant variant (~16 * volume). */
    private static final double MAX_RANGE = 16.0 * DISTANT_VOLUME;

    private WFExplosionAudio() {}

    public static void playBlast(ServerLevel level, Vec3 pos, float power) {
        float powerScale = Math.min(1.0F, power / 4.0F);
        long seed = level.random.nextLong(); // shared so everyone hears the same variant of a given event
        for (ServerPlayer player : level.players()) {
            double dist = Math.sqrt(player.distanceToSqr(pos));
            if (dist > MAX_RANGE) {
                continue;
            }
            boolean near = dist <= NEAR_RANGE;
            SoundEvent sound = near ? WFSounds.MINING_CHARGE_BLAST.get() : WFSounds.MINING_CHARGE_BLAST_DISTANT.get();
            float volume = (near ? NEAR_VOLUME : DISTANT_VOLUME) * powerScale;
            int delay = (int) (dist / BLOCKS_PER_TICK);
            WFDelayedSounds.schedule(level, delay, player, pos.x, pos.y, pos.z,
                    sound, SoundSource.BLOCKS, volume, 1.0F, seed);
        }
    }
}
