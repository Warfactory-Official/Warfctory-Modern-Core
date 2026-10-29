package com.norwood.wfcore.common.data;

import com.norwood.wfcore.WFCore;
import com.wf.wflib.MissileEntity;
import com.wf.wflib.damage.MissileDamageRegistry;
import com.wf.wflib.damage.MissileDamageResponse;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;

/**
 * WFCore's default missile damage response, registered into WFLib' {@link MissileDamageRegistry} and
 * applied to every wfcore missile (see {@code WFMissiles.missile}). A missile shrugs off fire-typed damage while
 * still taking explosion damage. Interceptor/CIWS hits go through WF-B's separate {@code damageMissile} path and
 * are unaffected.
 */
public final class WFDamageResponses {

    public static final ResourceLocation HARDENED_ID = WFCore.id("hardened");
    public static final ResourceLocation HARDENED_ICBM_ID = WFCore.id("hardened_icbm");

    // Fraction of non-immune damage the reinforced ICBM airframe actually takes (the rest is shrugged off).
    private static final float ICBM_DAMAGE_MULT = 0.25f;

    /**
     * Immune to fire; takes everything else (notably explosions) as dealt — until it's been
     * shot down. Once {@link MissileEntity#isDowned() downed} it shrugs off all further damage, so sustained
     * area fire (e.g. an auto CIWS whose explosive rounds keep landing while it falls) can't cook off the warhead
     * after it's already been neutralised and is coasting to its crash/fizzle. Interceptor/CIWS kills go through
     * WF-B's separate {@code damageMissile} path and are unaffected by this.
     */
    public static final MissileDamageResponse HARDENED = (missile, source, amount) -> {
        if (missile.damage().isDowned()) {
            return 0.0f;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
            return 0.0f;
        }
        return amount;
    };

    /**
     * The ICBM class's tougher response: immune to fire like {@link #HARDENED}, and also takes only
     * {@link #ICBM_DAMAGE_MULT} of everything else (explosions, blast-adjacent CIWS rounds), so the reinforced
     * airframe shrugs off attacks that would rattle a normal missile. Still immune once downed.
     */
    public static final MissileDamageResponse HARDENED_ICBM = (missile, source, amount) -> {
        if (missile.damage().isDowned()) {
            return 0.0f;
        }
        if (source.is(DamageTypeTags.IS_FIRE)) {
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
