package com.norwood.wfcore.common.sound;

import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.norwood.wfcore.WFCore;

public final class WFSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(ForgeRegistries.SOUND_EVENTS,
            WFCore.MOD_ID);

    public static final RegistryObject<SoundEvent> MINING_CHARGE_BLAST = register("mining_charge_blast");
    public static final RegistryObject<SoundEvent> MINING_CHARGE_BLAST_DISTANT = register(
            "mining_charge_blast_distant");
    public static final RegistryObject<SoundEvent> DETONATOR_ARM = register("detonator_arm");
    public static final RegistryObject<SoundEvent> DETONATOR_DETONATE = register("detonator_detonate");
    /** The NTM CE boltgun report, played when the bolt gun bolts a casing. */
    public static final RegistryObject<SoundEvent> BOLT_TOOL_FIRE = register("bolt_tool_fire");
    /** Looping flight sound for the Gas Drone (Shahed-Jarty airframe). */
    public static final RegistryObject<SoundEvent> MISSILE_DRONE = register("missile_gas_drone");
    public static final RegistryObject<SoundEvent> MISSILE_JARTY = register("missile_jartydrone");

    private WFSounds() {}

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(WFCore.id(name)));
    }
}
