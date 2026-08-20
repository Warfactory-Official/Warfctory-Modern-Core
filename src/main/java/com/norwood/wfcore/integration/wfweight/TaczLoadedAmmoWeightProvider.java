package com.norwood.wfcore.integration.wfweight;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.warfactory.ultimateweight.api.IWeightCompatProvider;
import com.warfactory.ultimateweight.v1211.WeightViews1211;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weighs a TACZ gun as itself plus the rounds currently chambered in it, so a full magazine costs what its
 * rounds cost and firing the gun lightens it. The TACZ counterpart of the Superb Warfare loaded-launcher
 * provider the 1.20.1 branch carried.
 *
 * <p>
 * Both halves prefer a {@link TaczWeights} override and fall back to Ultimate Weight's <em>configured</em>
 * weights, never to {@code weightOf}: the resolver consults compat providers before falling back to config,
 * so asking it for the gun's weight here would re-enter this provider forever.
 */
public final class TaczLoadedAmmoWeightProvider implements IWeightCompatProvider {

    /** gun id -> the ammo it feeds on. Datapack-fixed, so safe to cache; the weights are not. */
    private static final Map<ResourceLocation, Optional<ResourceLocation>> AMMO_IDS = new ConcurrentHashMap<>();
    /** ammo id -> Ultimate Weight's configured per-round weight, which costs a stack build to read. */
    private static final Map<ResourceLocation, Double> CONFIGURED_ROUND = new ConcurrentHashMap<>();

    @Override
    public OptionalDouble getUnitWeight(Object rawStack) {
        if (!(rawStack instanceof ItemStack stack) || stack.isEmpty()) {
            return OptionalDouble.empty();
        }
        IGun gun = IGun.getIGunOrNull(stack);
        if (gun == null) {
            return OptionalDouble.empty();
        }

        ResourceLocation gunId = gun.getGunId(stack);
        double configured = WeightViews1211.configuredWeightOf(stack, 0);
        double base = TaczWeights.gunOr(gunId, configured);

        int rounds = gun.useDummyAmmo(stack) ? gun.getDummyAmmoAmount(stack) : gun.getCurrentAmmoCount(stack);
        double loaded = rounds > 0 ? rounds * roundWeight(gunId) : 0.0D;

        if (loaded <= 0.0D && base == configured) {
            // Nothing to add and no per-gun override: let the plain configured weight stand.
            return OptionalDouble.empty();
        }
        return OptionalDouble.of(base + loaded);
    }

    /** The weight of one round of whatever {@code gunId} feeds on, or 0 if it has no ammo. */
    private static double roundWeight(ResourceLocation gunId) {
        if (gunId == null) {
            return 0.0D;
        }
        ResourceLocation ammoId = AMMO_IDS
                .computeIfAbsent(gunId, id -> TimelessAPI.getCommonGunIndex(id).map(i -> i.getGunData().getAmmoId()))
                .orElse(null);
        if (ammoId == null) {
            return 0.0D;
        }
        return TaczWeights.ammoOr(ammoId, CONFIGURED_ROUND.computeIfAbsent(ammoId, id -> {
            ItemStack ammo = AmmoItemBuilder.create().setId(id).setCount(1).build();
            return ammo.isEmpty() ? 0.0D : WeightViews1211.configuredWeightOf(ammo, 0);
        }));
    }

    /** Ahead of the generic providers, matching the launcher provider it replaces. */
    @Override
    public int getPriority() {
        return 200;
    }
}
