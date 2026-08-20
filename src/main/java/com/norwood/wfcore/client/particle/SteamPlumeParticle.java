package com.norwood.wfcore.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Steam wisp rising off the Strandcaster's casting bed. It behaves like vanilla campfire "cosy" smoke — a
 * slow, buoyant column running the generic smoke sprite animation — but is tinted near-white and translucent
 * so it reads as steam rather than smoke.
 */
public class SteamPlumeParticle extends TextureSheetParticle {

    private final SpriteSet sprites;

    protected SteamPlumeParticle(ClientLevel level, double x, double y, double z,
                                 double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.lifetime = 55 + this.random.nextInt(35); // ~3-4.5s, campfire-cosy-like
        this.gravity = 0.0F;                           // rises on its own buoyancy, doesn't fall
        this.friction = 0.96F;
        this.hasPhysics = false;
        this.quadSize *= 1.0F + this.random.nextFloat() * 1.3F;
        this.xd = dx * 0.1;
        this.zd = dz * 0.1;
        this.yd = dy > 0 ? dy : 0.02D + this.random.nextFloat() * 0.02D;
        float white = 0.9F + this.random.nextFloat() * 0.1F; // near-white steam
        this.setColor(white, white, white);
        this.alpha = 0.75F;
        this.setSpriteFromAge(sprites);
    }

    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    @Override
    public void tick() {
        super.tick();
        this.yd += 0.0008D; // gentle buoyancy so it keeps drifting upward as it ages
        this.setSpriteFromAge(this.sprites);
        this.alpha = 0.75F * Math.max(0.0F, 1.0F - (float) this.age / (float) this.lifetime);
    }

    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double dx, double dy, double dz) {
            return new SteamPlumeParticle(level, x, y, z, dx, dy, dz, this.sprites);
        }
    }
}
