package com.norwood.wfcore.common.particle;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import com.mojang.serialization.Codec;
import com.norwood.wfcore.WFCore;

public final class WFParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(ForgeRegistries.PARTICLE_TYPES, WFCore.MOD_ID);

    public static final RegistryObject<ParticleType<BlockParticleOption>> BLOCK_DEBRIS = PARTICLE_TYPES.register(
            "block_debris",
            () -> new ParticleType<BlockParticleOption>(false, BlockParticleOption.DESERIALIZER) {

                @Override
                public Codec<BlockParticleOption> codec() {
                    return BlockParticleOption.codec(this);
                }
            });

    public static final RegistryObject<SimpleParticleType> SMOKE_PLUME = PARTICLE_TYPES.register("smoke_plume",
            () -> new SimpleParticleType(false) {});

    private WFParticles() {}
}
