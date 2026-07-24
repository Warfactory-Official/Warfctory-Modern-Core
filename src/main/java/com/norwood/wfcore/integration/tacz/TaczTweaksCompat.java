package com.norwood.wfcore.integration.tacz;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.ModList;

import com.norwood.wfcore.WFCore;
import com.tacz.guns.entity.EntityKineticBullet;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;


public final class TaczTweaksCompat {

    public static final String MOD_ID = "tacztweaks";

    // Field names are explicit prefixes in tacztweaks' mixin, so they survive verbatim on the target class.
    private static final String F_GUN_STACK = "tacztweaks$gunStack";
    private static final String F_BLOCK_PIERCE = "tacztweaks$blockPierce";
    private static final String F_ENTITY_PIERCE = "tacztweaks$entityPierce";
    private static final String F_BURST_INDEX = "tacztweaks$burstIndex";
    private static final String F_PELLET_INDEX = "tacztweaks$pelletIndex";
    private static final String F_DAMAGE_MODIFIERS = "tacztweaks$damageModifiers";
    private static final String DAMAGE_MODIFIER_CLASS =
            "me.muksc.tacztweaks.mixininterface.features.EntityKineticBulletExtension$DamageModifier";

    private static final String KEY_GUN = "Gun";
    private static final String KEY_BLOCK_PIERCE = "BlockPierce";
    private static final String KEY_ENTITY_PIERCE = "EntityPierce";
    private static final String KEY_BURST_INDEX = "BurstIndex";
    private static final String KEY_PELLET_INDEX = "PelletIndex";
    private static final String KEY_DAMAGE_MODS = "DamageMods";
    private static final String KEY_MOD_FLAT = "f";
    private static final String KEY_MOD_MULT = "m";


    private static Field gunStackF;
    private static Field blockPierceF;
    private static Field entityPierceF;
    private static Field burstIndexF;
    private static Field pelletIndexF;
    private static Field damageModifiersF;
    private static Constructor<?> damageModifierCtor;
    private static Method damageModifierFlat;
    private static Method damageModifierMult;

    private static boolean available;

    private TaczTweaksCompat() {}

    public static void init() {
        if (available) {
            return;
        }
        if (ModList.get() == null || !ModList.get().isLoaded(MOD_ID)) {
            WFCore.LOGGER.debug("Ballistics: TaCZ Tweaks not loaded, bullet-state carry disabled");
            return;
        }
        try {
            gunStackF = field(F_GUN_STACK);
        } catch (NoSuchFieldException e) {
            WFCore.LOGGER.warn("Ballistics: TaCZ Tweaks loaded but '{}' is missing on EntityKineticBullet; "
                    + "re-spawned bullets may crash its block-interaction feature", F_GUN_STACK, e);
            return;
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to hook TaCZ Tweaks gun-stack field", t);
            return;
        }

        blockPierceF = fieldOrNull(F_BLOCK_PIERCE);
        entityPierceF = fieldOrNull(F_ENTITY_PIERCE);
        burstIndexF = fieldOrNull(F_BURST_INDEX);
        pelletIndexF = fieldOrNull(F_PELLET_INDEX);
        damageModifiersF = fieldOrNull(F_DAMAGE_MODIFIERS);
        try {
            Class<?> dm = Class.forName(DAMAGE_MODIFIER_CLASS);
            damageModifierCtor = dm.getConstructor(float.class, float.class);
            damageModifierFlat = dm.getMethod("flat");
            damageModifierMult = dm.getMethod("multiplier");
        } catch (Throwable t) {
            WFCore.LOGGER.debug("Ballistics: TaCZ Tweaks DamageModifier not resolvable, skipping that field", t);
        }

        available = true;
        WFCore.LOGGER.info("Ballistics: TaCZ Tweaks detected, cached per-bullet state for virtual-bullet carry");
    }

    public static boolean isAvailable() {
        return available;
    }

    public static CompoundTag saveState(EntityKineticBullet bullet) {
        if (!available) {
            return null;
        }
        CompoundTag tag = new CompoundTag();
        try {
            ItemStack gun = (ItemStack) gunStackF.get(bullet);
            if (gun != null && !gun.isEmpty()) {
                tag.put(KEY_GUN, gun.save(new CompoundTag()));
            }
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to read tacztweaks gun stack", t);
        }
        saveInt(tag, KEY_BLOCK_PIERCE, blockPierceF, bullet);
        saveInt(tag, KEY_ENTITY_PIERCE, entityPierceF, bullet);
        saveInt(tag, KEY_BURST_INDEX, burstIndexF, bullet);
        saveInt(tag, KEY_PELLET_INDEX, pelletIndexF, bullet);

        if (damageModifiersF != null && damageModifierFlat != null) {
            try {
                List<?> mods = (List<?>) damageModifiersF.get(bullet);
                if (mods != null && !mods.isEmpty()) {
                    ListTag list = new ListTag();
                    for (Object mod : mods) {
                        CompoundTag e = new CompoundTag();
                        e.putFloat(KEY_MOD_FLAT, (float) damageModifierFlat.invoke(mod));
                        e.putFloat(KEY_MOD_MULT, (float) damageModifierMult.invoke(mod));
                        list.add(e);
                    }
                    tag.put(KEY_DAMAGE_MODS, list);
                }
            } catch (Throwable t) {
                WFCore.LOGGER.warn("Ballistics: failed to read tacztweaks damage modifiers", t);
            }
        }
        return tag;
    }

    /**
     * Restore the {@link #saveState} snapshot onto a re-spawned bullet before it ticks. {@code tag} may be
     * {@code null} (e.g. bullets virtualised before this carry existed): gunStack still gets set to
     * {@link ItemStack#EMPTY} so tacztweaks' non-null read never NPEs.
     */
    public static void restoreState(EntityKineticBullet bullet, CompoundTag tag) {
        if (!available) {
            return;
        }
        try {
            ItemStack gun = (tag != null && tag.contains(KEY_GUN))
                    ? ItemStack.of(tag.getCompound(KEY_GUN))
                    : ItemStack.EMPTY;
            gunStackF.set(bullet, gun);
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to restore tacztweaks gun stack", t);
        }
        if (tag == null) {
            return;
        }
        restoreInt(tag, KEY_BLOCK_PIERCE, blockPierceF, bullet);
        restoreInt(tag, KEY_ENTITY_PIERCE, entityPierceF, bullet);
        restoreInt(tag, KEY_BURST_INDEX, burstIndexF, bullet);
        restoreInt(tag, KEY_PELLET_INDEX, pelletIndexF, bullet);

        if (damageModifiersF != null && damageModifierCtor != null && tag.contains(KEY_DAMAGE_MODS)) {
            try {
                @SuppressWarnings("unchecked")
                List<Object> mods = (List<Object>) damageModifiersF.get(bullet);
                if (mods != null) {
                    mods.clear();
                    ListTag list = tag.getList(KEY_DAMAGE_MODS, Tag.TAG_COMPOUND);
                    for (int i = 0; i < list.size(); i++) {
                        CompoundTag e = list.getCompound(i);
                        mods.add(damageModifierCtor.newInstance(e.getFloat(KEY_MOD_FLAT), e.getFloat(KEY_MOD_MULT)));
                    }
                }
            } catch (Throwable t) {
                WFCore.LOGGER.warn("Ballistics: failed to restore tacztweaks damage modifiers", t);
            }
        }
    }

    private static void saveInt(CompoundTag tag, String key, Field f, EntityKineticBullet bullet) {
        if (f == null) {
            return;
        }
        try {
            tag.putInt(key, f.getInt(bullet));
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to read tacztweaks field {}", key, t);
        }
    }

    private static void restoreInt(CompoundTag tag, String key, Field f, EntityKineticBullet bullet) {
        if (f == null || !tag.contains(key)) {
            return;
        }
        try {
            f.setInt(bullet, tag.getInt(key));
        } catch (Throwable t) {
            WFCore.LOGGER.warn("Ballistics: failed to restore tacztweaks field {}", key, t);
        }
    }

    private static Field field(String name) throws NoSuchFieldException {
        Field f = EntityKineticBullet.class.getDeclaredField(name);
        f.setAccessible(true);
        return f;
    }

    private static Field fieldOrNull(String name) {
        try {
            return field(name);
        } catch (Throwable t) {
            WFCore.LOGGER.debug("Ballistics: tacztweaks field '{}' not found, skipping carry", name);
            return null;
        }
    }
}
