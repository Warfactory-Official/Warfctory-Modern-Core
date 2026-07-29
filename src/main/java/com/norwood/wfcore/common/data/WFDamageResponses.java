package com.norwood.wfcore.common.data;

import com.norwood.wfcore.WFCore;
import com.wf.wfballistics.MissileEntity;
import com.wf.wfballistics.damage.MissileDamageRegistry;
import com.wf.wfballistics.damage.MissileDamageResponse;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;

/**
 * WFCore's default missile damage response, registered into WF-Ballistics' {@link MissileDamageRegistry} and
 * applied to every wfcore missile (see {@code WFMissiles.missile}). A missile shrugs off small-arms and flames —
 * TaCZ gun fire (the {@code tacz:bullets} damage-type tag) and anything fire-typed — while still taking explosion
 * damage. Interceptor/CIWS hits go through WF-B's separate {@code damageMissile} path and are unaffected.
 *
 * <p>The TaCZ tag is referenced by id only (no class dependency), so this is safe whether or not TaCZ is loaded —
 * with TaCZ absent the tag simply matches nothing and only the fire clause applies.
 */
public final class WFDamageResponses {

    public static final ResourceLocation HARDENED_ID = WFCore.id("hardened");
    public static final ResourceLocation HARDENED_ICBM_ID = WFCore.id("hardened_icbm");

    // Fraction of non-immune damage the reinforced ICBM airframe actually takes (the rest is shrugged off).
    private static final float ICBM_DAMAGE_MULT = 0.25f;

    // All four TaCZ bullet damage types (tacz:bullet[_ignore_armor|_void|_void_ignore_armor]) collect here.
    private static final TagKey<DamageType> TACZ_BULLETS =
            TagKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("tacz", "bullets"));

    /**
     * Immune to TaCZ gunfire and fire; takes everything else (notably explosions) as dealt — until it's been
     * shot down. Once {@link MissileEntity#isDowned() downed} it shrugs off all further damage, so sustained
     * area fire (e.g. an auto CIWS whose explosive rounds keep landing while it falls) can't cook off the warhead
     * after it's already been neutralised and is coasting to its crash/fizzle. Interceptor/CIWS kills go through
     * WF-B's separate {@code damageMissile} path and are unaffected by this.
     */
    public static final MissileDamageResponse HARDENED = (missile, source, amount) -> {
        if (missile.isDowned()) {
            return 0.0f;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(TACZ_BULLETS)) {
            return 0.0f;
        }
        return amount;
    };

    /**
     * The ICBM class's tougher response: immune to fire + gunfire like {@link #HARDENED}, and also takes only
     * {@link #ICBM_DAMAGE_MULT} of everything else (explosions, blast-adjacent CIWS rounds), so the reinforced
     * airframe shrugs off attacks that would rattle a normal missile. Still immune once downed.
     */
    public static final MissileDamageResponse HARDENED_ICBM = (missile, source, amount) -> {
        if (missile.isDowned()) {
            return 0.0f;
        }
        if (source.is(DamageTypeTags.IS_FIRE) || source.is(TACZ_BULLETS)) {
            return 0.0f;
        }
        return amount * ICBM_DAMAGE_MULT;
    };

    private WFDamageResponses() {}

    public static void register() {
        MissileDamageRegistry.register(HARDENED_ID, HARDENED);
        MissileDamageRegistry.register(HARDENED_ICBM_ID, HARDENED_ICBM);
    }
}
