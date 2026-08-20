package com.norwood.wfcore.common.particle;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import com.mojang.serialization.MapCodec;
import com.norwood.wfcore.WFCore;

public final class WFParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister
            .create(Registries.PARTICLE_TYPE, WFCore.MOD_ID);

    // 1.21 replaced ParticleType's ParticleOptions.Deserializer ctor arg with a codec() +
    // streamCodec() pair; BlockParticleOption supplies both factories.
    public static final DeferredHolder<ParticleType<?>, ParticleType<BlockParticleOption>> BLOCK_DEBRIS =
            PARTICLE_TYPES.register("block_debris",
                    () -> new ParticleType<BlockParticleOption>(false) {

                        @Override
                        public MapCodec<BlockParticleOption> codec() {
                            return BlockParticleOption.codec(this);
                        }

                        @Override
                        public StreamCodec<? super RegistryFriendlyByteBuf, BlockParticleOption> streamCodec() {
                            return BlockParticleOption.streamCodec(this);
                        }
                    });

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SMOKE_PLUME = PARTICLE_TYPES
            .register("smoke_plume", () -> new SimpleParticleType(false) {});

    /** White, campfire-style rising smoke used as "steam" off the Strandcaster's casting bed while it works. */
    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> STRANDCASTER_STEAM = PARTICLE_TYPES
            .register("strandcaster_steam", () -> new SimpleParticleType(false) {});

    private WFParticles() {}
}
