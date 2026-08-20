package com.norwood.wfcore.common.sound;

import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.norwood.wfcore.WFCore;

public final class WFSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,
            WFCore.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST = register("mining_charge_blast");
    public static final DeferredHolder<SoundEvent, SoundEvent> MINING_CHARGE_BLAST_DISTANT = register(
            "mining_charge_blast_distant");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_ARM = register("detonator_arm");
    public static final DeferredHolder<SoundEvent, SoundEvent> DETONATOR_DETONATE = register("detonator_detonate");
    /** The NTM CE boltgun report, played when the bolt gun bolts a casing. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOLT_TOOL_FIRE = register("bolt_tool_fire");
    /** Looping flight sound for the Gas Drone (Shahed-Jarty airframe). */
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_DRONE = register("missile_gas_drone");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_JARTY = register("missile_jartydrone");
    public static final DeferredHolder<SoundEvent, SoundEvent> MISSILE_EMP = register("missile_emp");

    private WFSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(WFCore.id(name)));
    }
}
