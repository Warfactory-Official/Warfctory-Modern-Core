package com.norwood.wfcore.client.render;

import com.gregtechceu.gtceu.api.machine.MetaMachine;
import com.gregtechceu.gtceu.common.data.GTSoundEntries;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.client.render.mask.RenderMaskManager;
import com.norwood.wfcore.common.deposit.DepositType;
import com.norwood.wfcore.common.machine.DrillRigMachine;
import org.joml.Vector3f;

import java.util.List;

/**
 * Purely client-side "drilling" VFX + SFX: while a Drilling Rig is active, kicks up ore-coloured dust and rock
 * crumbs on top of the ore blocks under it, and loops GregTech's miner sound ({@link GTSoundEntries#MINER}) at the
 * head. Nothing is server-synced - it reads only state the client already has ({@code isActive()} from the
 * recipe-logic status, and the {@code @DescSynced} display deposit id for the ore tint) and spawns the particles
 * locally with {@link ClientLevel#addParticle}. Drills are found via {@link RenderMaskManager} (the same
 * "currently rendering its GLTF model" set the animation uses), so effects only start for on-screen rigs.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class DrillParticleHandler {

    /** Neutral deep-rock crumbs; the ore colour comes from the tinted dust, since block particles can't be tinted. */
    private static final BlockState CRUMB_BLOCK = Blocks.DEEPSLATE.defaultBlockState();
    /** Don't bother spawning for rigs further than this (blocks) from the camera - the player can't see them. */
    private static final double MAX_DIST_SQR = 48 * 48;
    /** Controllers (packed pos) that currently have a looping miner sound, so we start exactly one per rig. */
    private static final LongOpenHashSet SOUNDING = new LongOpenHashSet();

    private DrillParticleHandler() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            SOUNDING.clear(); // left the world - drop stale sound bookkeeping (the sounds are stopped by MC)
            return;
        }
        if (mc.player == null || mc.isPaused()) {
            return;
        }
        for (BlockPos controllerPos : RenderMaskManager.getMaskedControllers()) {
            if (!(MetaMachine.getMachine(level, controllerPos) instanceof DrillRigMachine drill)) {
                continue;
            }
            if (!drill.isFormed() || !drill.isActive()) {
                continue;
            }
            BlockPos head = drill.getDrillHeadWorldPos();
            if (head == null || mc.player.distanceToSqr(head.getX() + 0.5, head.getY() + 0.5, head.getZ() + 0.5)
                    > MAX_DIST_SQR) {
                continue;
            }
            startSound(controllerPos.immutable(), head);
            spawn(level, drill);
        }
    }

    /** Start one looping miner sound at the head; it auto-releases when {@link #shouldStopSound} goes true. */
    private static void startSound(BlockPos controllerPos, BlockPos head) {
        if (SOUNDING.add(controllerPos.asLong())) {
            GTSoundEntries.MINER.playAutoReleasedSound(() -> shouldStopSound(controllerPos), head, true, 0,
                    1.0F, 1.0F);
        }
    }

    /** True once the rig should no longer be heard (gone / unformed / idle); also frees it to re-sound later. */
    private static boolean shouldStopSound(BlockPos controllerPos) {
        ClientLevel level = Minecraft.getInstance().level;
        boolean stop = level == null
                || !(MetaMachine.getMachine(level, controllerPos) instanceof DrillRigMachine drill)
                || !drill.isFormed() || !drill.isActive();
        if (stop) {
            SOUNDING.remove(controllerPos.asLong());
        }
        return stop;
    }

    private static void spawn(ClientLevel level, DrillRigMachine drill) {
        // Spatter the effect on TOP of the ore blocks in the 3x3 under the drill head (not at the head itself).
        List<BlockPos> ores = drill.getDepositBlocksNearHead(level, 1);
        if (ores.isEmpty()) {
            return;
        }
        DepositType type = drill.getDisplayDepositType();
        int rgb = type != null ? type.effectiveColor() : 0xFFFFFF;
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255f, ((rgb >> 8) & 0xFF) / 255f, (rgb & 0xFF) / 255f);
        RandomSource rnd = level.random;

        for (BlockPos ore : ores) {
            double px = ore.getX() + rnd.nextDouble();
            double pz = ore.getZ() + rnd.nextDouble();
            double top = ore.getY() + 1.0; // top face of the ore block

            // Ore-coloured dust rising off the ore.
            level.addParticle(new DustParticleOptions(color, 3.0F), px, top + rnd.nextDouble() * 0.2, pz,
                    (rnd.nextDouble() - 0.5) * 0.02, 0.02 + rnd.nextDouble() * 0.03, (rnd.nextDouble() - 0.5) * 0.02);

            // Rock crumbs (block-breaking) bursting off the top, on ~a quarter of the blocks each tick.
            if (rnd.nextInt(4) == 0) {
                level.addParticle(new BlockParticleOption(ParticleTypes.BLOCK, CRUMB_BLOCK), px, top, pz,
                        (rnd.nextDouble() - 0.5) * 0.1, 0.06 + rnd.nextDouble() * 0.06, (rnd.nextDouble() - 0.5) * 0.1);
            }
        }
    }
}
