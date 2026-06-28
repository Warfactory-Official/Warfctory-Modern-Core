package com.norwood.wfcore.common.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;

import com.norwood.wfcore.common.data.WFTags;
import com.norwood.wfcore.common.particle.WFParticles;
import com.norwood.wfcore.common.sound.WFExplosionAudio;
import com.norwood.wfcore.integration.warforge.WarforgeChunkUtil;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The blast a mining charge produces: an instant cube around the centre that breaks only natural blocks
 * (see {@link WFTags#NATURAL_BLAST_BREAKABLE}), keeps their drops (fortune-mined for ores), then spawns those
 * drops as a handful of consolidated item entities so a big blast does not litter the world with hundreds of
 * entities. Deals only light, distance-scaled damage.
 */
public final class MiningExplosion {

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final float MAX_DAMAGE = 6.0F;
    /** Cap on distinct block types we sample for the debris particle burst. */
    private static final int DEBRIS_TYPES = 8;

    private MiningExplosion() {}

    public static void explode(ServerLevel level, BlockPos center, int radius, int fortune, int tier,
                               @Nullable Player player) {
        ItemStack plainTool = new ItemStack(Items.NETHERITE_PICKAXE);
        ItemStack oreTool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (fortune > 0) {
            oreTool.enchant(Enchantments.BLOCK_FORTUNE, fortune);
        }

        List<ItemStack> drops = new ArrayList<>();
        List<BlockState> debris = new ArrayList<>();
        Set<Block> debrisSeen = new HashSet<>();
        List<BlockState> nearby = new ArrayList<>();
        Set<Block> nearbySeen = new HashSet<>();
        List<BlockPos> chain = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int broken = 0;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    pos.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir()) {
                        continue;
                    }
                    // Another explosive caught in the blast: chain it instead of treating it as terrain.
                    if (IDetonatable.isExplosive(state)) {
                        chain.add(pos.immutable());
                        continue;
                    }
                    // Sample every solid block in the volume for the block-break particle burst, even ones
                    // the charge can't break — those particles don't depend on anything actually breaking.
                    if (nearby.size() < DEBRIS_TYPES && nearbySeen.add(state.getBlock())) {
                        nearby.add(state);
                    }
                    if (!isBreakable(state, tier)) {
                        continue;
                    }
                    if (state.getDestroySpeed(level, pos) < 0) {
                        continue; // indestructible (bedrock, deposits)
                    }
                    if (WarforgeIntegration.isLoaded() && !WarforgeChunkUtil.canDestroyIn(player, level, pos))
                        continue; // Warforge integration

                    boolean ore = state.is(Tags.Blocks.ORES);
                    BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
                    LootParams.Builder params = new LootParams.Builder(level)
                            .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                            .withParameter(LootContextParams.TOOL, ore ? oreTool : plainTool)
                            .withOptionalParameter(LootContextParams.BLOCK_ENTITY, be);
                    drops.addAll(state.getDrops(params));

                    if (debris.size() < DEBRIS_TYPES && debrisSeen.add(state.getBlock())) {
                        debris.add(state);
                    }

                    level.setBlock(pos, AIR, Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
                    broken++;
                }
            }
        }

        spawnConsolidatedDrops(level, center, consolidate(drops));
        // Smoke and the block-break burst always play; only the flying debris needs blocks to have broken.
        spawnSmoke(level, center, radius);
        spawnCrackParticles(level, center, radius, nearby);
        if (broken > 0 && !debris.isEmpty()) {
            spawnDebris(level, center, radius, debris);
        }
        playBoom(level, center);
        damageEntities(level, center, radius, player);

        // Chain-detonate other explosives caught in the blast. Each removes itself before exploding, so it
        // fires once; for very large fields, swap this for level.scheduleTick to spread the cascade over ticks.
        for (BlockPos p : chain) {
            IDetonatable.tryDetonate(level, p, player);
        }
    }

    private static boolean isBreakable(BlockState state, int tier) {
        // Deep matrix and deepslate ores need tier 2 — checked first so it beats the broad forge:ores below.
        if (state.is(WFTags.DEEP_BLAST_BREAKABLE) || state.is(WFTags.DEEP_ORES)) {
            return tier >= 2;
        }
        return state.is(WFTags.NATURAL_BLAST_BREAKABLE) || state.is(Tags.Blocks.ORES);
    }

    /** Merge same-item drops into full stacks so we spawn as few item entities as possible. */
    private static List<ItemStack> consolidate(List<ItemStack> drops) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack drop : drops) {
            if (drop.isEmpty()) {
                continue;
            }
            int remaining = drop.getCount();
            for (ItemStack existing : out) {
                if (remaining <= 0) {
                    break;
                }
                if (ItemStack.isSameItemSameTags(existing, drop)) {
                    int space = existing.getMaxStackSize() - existing.getCount();
                    if (space > 0) {
                        int moved = Math.min(space, remaining);
                        existing.grow(moved);
                        remaining -= moved;
                    }
                }
            }
            while (remaining > 0) {
                ItemStack copy = drop.copy();
                int take = Math.min(drop.getMaxStackSize(), remaining);
                copy.setCount(take);
                out.add(copy);
                remaining -= take;
            }
        }
        return out;
    }

    /** Spawn the merged stacks in a tight, deterministic spiral above the centre — never one-per-block. */
    private static void spawnConsolidatedDrops(ServerLevel level, BlockPos center, List<ItemStack> stacks) {
        double baseX = center.getX() + 0.5;
        double baseY = center.getY() + 0.5;
        double baseZ = center.getZ() + 0.5;
        for (int i = 0; i < stacks.size(); i++) {
            double angle = i * 2.3999632; // golden angle keeps the cluster even
            double r = Math.min(0.6, 0.15 + 0.05 * i);
            double ox = Math.cos(angle) * r;
            double oz = Math.sin(angle) * r;
            ItemEntity entity = new ItemEntity(level, baseX + ox, baseY + 0.4, baseZ + oz, stacks.get(i));
            entity.setDeltaMovement(ox * 0.05, 0.12, oz * 0.05);
            entity.setDefaultPickUpDelay();
            level.addFreshEntity(entity);
        }
    }

    /** Smoke that emanates from the centre — each puff is sent with a velocity pointing away from the blast. */
    private static void spawnSmoke(ServerLevel level, BlockPos center, int radius) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        RandomSource rng = level.random;
        int count = 30 + radius * 12;
        for (int i = 0; i < count; i++) {
            // Full 3D gaussian normalized => a uniformly random direction over the whole sphere (all directions).
            double dx = rng.nextGaussian();
            double dy = rng.nextGaussian();
            double dz = rng.nextGaussian();
            double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (len < 1.0e-4) {
                dx = 0.0;
                dy = 1.0;
                dz = 0.0;
                len = 1.0;
            }
            double ux = dx / len;
            double uy = dy / len;
            double uz = dz / len;
            double dist = rng.nextDouble() * radius * 0.6;
            // A hard initial yank outward; the particle's friction bleeds it off so it slows after the burst.
            double speed = 0.45 + rng.nextDouble() * 0.4;
            // Puffs flung downward get their Y velocity heavily damped, so they slow on the Y axis almost at once.
            double vy = uy < 0.0 ? uy * 0.2 : uy;
            level.sendParticles(WFParticles.SMOKE_PLUME.get(),
                    cx + ux * dist, cy + uy * dist, cz + uz * dist, 0, ux, vy, uz, speed);
        }
    }

    /** A heavy burst of vanilla block-break crack particles, fanning out from the centre. Always plays. */
    private static void spawnCrackParticles(ServerLevel level, BlockPos center, int radius, List<BlockState> nearby) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        List<BlockState> states = nearby.isEmpty() ? List.of(Blocks.STONE.defaultBlockState()) : nearby;
        int per = Math.max(8, (90 + radius * 40) / states.size());
        float spread = radius * 0.5F;
        for (BlockState state : states) {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state),
                    cx, cy, cz, per, spread, spread, spread, 0.35D);
        }
    }

    /** The flying block chunks — only emitted when blocks actually broke. */
    private static void spawnDebris(ServerLevel level, BlockPos center, int radius, List<BlockState> debris) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        float spread = radius * 0.55F;
        int perType = Math.max(14, 110 / debris.size());
        for (BlockState state : debris) {
            level.sendParticles(new BlockParticleOption(WFParticles.BLOCK_DEBRIS.get(), state),
                    cx, cy + 0.6D, cz, perType, spread, spread * 0.5F, spread, 0.45D);
        }
    }

    private static void playBoom(ServerLevel level, BlockPos center) {
        WFExplosionAudio.playBlast(level, Vec3.atCenterOf(center), 4.0F);
    }

    private static void damageEntities(ServerLevel level, BlockPos center, int radius, @Nullable Player player) {
        double cx = center.getX() + 0.5;
        double cy = center.getY() + 0.5;
        double cz = center.getZ() + 0.5;
        double range = radius + 1.0;
        DamageSource source = level.damageSources().explosion(null, player);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(center).inflate(range))) {
            double dist = Math.sqrt(entity.distanceToSqr(cx, cy, cz));
            if (dist > range) {
                continue;
            }
            float damage = (float) (MAX_DAMAGE * (1.0 - dist / range));
            if (damage > 0) {
                entity.hurt(source, damage);
            }
        }
    }
}
