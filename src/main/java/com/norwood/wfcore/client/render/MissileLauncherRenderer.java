package com.norwood.wfcore.client.render;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import com.norwood.wfcore.common.machine.MissileLauncherBlockEntity;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import com.wf.wfballistics.client.flywheel.FlywheelEffectManager;
import com.wf.wfballistics.client.render.MissileItemRenderer;
import com.wf.wfballistics.item.MissileItem;

/**
 * Renders the loaded missile as a 3D model on the silo's launch pad (the 3x3 steel frame at the base),
 * including the launch sequence: on fire, the model streaks up from the pad to the spawn height over
 * {@link MissileLauncherMachine#ANIM_TICKS} ticks, then hides while the real entity flies and the silo
 * reloads. Reuses WF-Ballistics' {@link MissileItemRenderer}, which fits the airframe's baked model into a
 * 1-block, nose-up unit pose (long axis local +Y), so we only place/scale it. Drawn only while formed.
 */
public class MissileLauncherRenderer implements BlockEntityRenderer<MissileLauncherBlockEntity> {

    /** On-screen height of the standing missile, in blocks (the item renderer fits the model to 1 block). */
    private static final float MISSILE_HEIGHT = 6.0f;

    public MissileLauncherRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(MissileLauncherBlockEntity be, float partialTick, PoseStack pose,
                       MultiBufferSource buffer, int light, int overlay) {
        if (!(be.getMetaMachine() instanceof MissileLauncherMachine machine) || !machine.isFormed()) {
            return;
        }

        // Choose what (and whether) to draw from the launch state:
        //  - cooldown 0        -> at rest: the loaded slot missile sitting on the pad.
        //  - first ANIM_TICKS  -> launching: the fired missile streaking up from pad to spawn height.
        //  - rest of cooldown  -> in flight / reloading: nothing.
        BlockPos pad = machine.launchPadPos();
        int cd = machine.getCooldown();
        ItemStack stack;
        float lift; // blocks above the pad the model's base sits this frame
        if (cd == 0) {
            // At rest the pad shows the missile the operator has selected from the linked factories.
            stack = machine.getDisplayStack();
            lift = MissileLauncherMachine.BASE_LIFT;
        } else {
            float elapsed = (MissileLauncherMachine.LAUNCH_COOLDOWN - cd) + partialTick; // ticks since fire
            if (elapsed > MissileLauncherMachine.ANIM_TICKS) {
                return; // missile has left the silo; pad is empty until reload finishes
            }
            stack = machine.getLaunchedStack();
            float frac = Mth.clamp(elapsed / MissileLauncherMachine.ANIM_TICKS, 0f, 1f);
            lift = MissileLauncherMachine.BASE_LIFT
                    + frac * (MissileLauncherMachine.MUZZLE_HEIGHT - MissileLauncherMachine.BASE_LIFT);
            // Rocket exhaust from the tail as it climbs: WF-Ballistics' real Flywheel trail when the backend
            // is on (kicked off here, then tick-driven by MissileLaunchTrail), else vanilla particles.
            Level level = be.getLevel();
            if (level != null) {
                if (FlywheelEffectManager.isAvailable(level)) {
                    MissileLaunchTrail.ensure(machine);
                } else {
                    emitExhaust(level, pad.getX() + 0.5, pad.getY() + lift, pad.getZ() + 0.5);
                }
            }
        }
        if (!(stack.getItem() instanceof MissileItem)) {
            return;
        }

        // Pad centre relative to the controller block (the BER pose starts at the controller's origin).
        BlockPos ctrl = be.getBlockPos();
        double dx = (pad.getX() - ctrl.getX()) + 0.5;
        double dy = (pad.getY() - ctrl.getY()) + lift;
        double dz = (pad.getZ() - ctrl.getZ()) + 0.5;

        // The passed `light` is sampled at the controller block, which is buried inside the casing (≈0), so
        // it would render the missile black. Sample light near the silo mouth (where sky light is strongest)
        // so the model reads clearly rather than picking up the dark interior floor.
        int packedLight = light;
        if (be.getLevel() != null) {
            packedLight = LevelRenderer.getLightColor(be.getLevel(), pad.above((int) MISSILE_HEIGHT + 1));
        }

        pose.pushPose();
        pose.translate(dx, dy, dz);
        pose.scale(MISSILE_HEIGHT, MISSILE_HEIGHT, MISSILE_HEIGHT);
        // MissileItemRenderer re-centres the model at (0.5, 0, 0.5) of its unit cube; cancel that so the
        // airframe stands centred over the pad instead of offset by half a (scaled) block.
        pose.translate(-0.5, 0.0, -0.5);
        MissileItemRenderer.instance().renderByItem(stack, ItemDisplayContext.FIXED, pose, buffer,
                packedLight, overlay);
        pose.popPose();
    }

    /**
     * Emits rocket exhaust — a hot flame core + smoke plume — streaming down from the tail (the model's
     * base). Uses vanilla FLAME/LARGE_SMOKE rather than WF-Ballistics' {@code rocket_flame}: that trail
     * particle lives ~300 ticks (fine for a fast missile that leaves it far behind, but it piles up and
     * lingers on a near-stationary silo launch). Called per render frame during the rise, so keep the
     * per-call count low — it still reads as a continuous plume at any framerate.
     */
    private static void emitExhaust(Level level, double x, double y, double z) {
        var rng = level.random;
        for (int i = 0; i < 2; i++) {
            double sx = x + (rng.nextDouble() - 0.5) * 0.5;
            double sz = z + (rng.nextDouble() - 0.5) * 0.5;
            // Downward + slight outward spray from the nozzle.
            level.addParticle(ParticleTypes.FLAME, sx, y - 0.2, sz,
                    (rng.nextDouble() - 0.5) * 0.03, -0.25 - rng.nextDouble() * 0.15, (rng.nextDouble() - 0.5) * 0.03);
        }
        level.addParticle(ParticleTypes.LARGE_SMOKE, x + (rng.nextDouble() - 0.5) * 0.6, y - 0.6,
                z + (rng.nextDouble() - 0.5) * 0.6, 0.0, -0.1, 0.0);
    }

    /** Keep rendering even when the controller block itself is off-screen (the missile towers above it). */
    @Override
    public boolean shouldRenderOffScreen(MissileLauncherBlockEntity be) {
        return true;
    }
}
