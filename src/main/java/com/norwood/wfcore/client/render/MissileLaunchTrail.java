package com.norwood.wfcore.client.render;

import com.gregtechceu.gtceu.api.machine.MetaMachine;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.common.machine.MissileLauncherMachine;
import com.wf.wfballistics.ModEntities;
import com.wf.wfballistics.client.flywheel.FlywheelEffectManager;
import com.wf.wfballistics.client.flywheel.InstancedTrailEffect;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Drives WF-Ballistics' real Flywheel exhaust trail for the launch-rise animation. On fire, the display
 * missile isn't a real entity, so we attach the trail to a lightweight stand-in {@link Entity} (a bare
 * missile entity that is never added to the world — it only carries a position + heading for the effect to
 * read) and steer it up the silo each client tick to track the rising model. When the missile leaves the
 * silo the stand-in is discarded, so the trail stops emitting and fades on its own (its puffs live ~2s).
 *
 * <p>Tick-driven rather than render-driven, so the trail keeps tracking and cleans up even if the player
 * looks away mid-launch; the BER only kicks off {@link #ensure} on the frame it first sees the rise.
 */
@Mod.EventBusSubscriber(modid = WFCore.MOD_ID, value = Dist.CLIENT)
public final class MissileLaunchTrail {

    /** Silo controller pos -> the stand-in entity carrying its trail. */
    private static final Map<BlockPos, Entity> ACTIVE = new HashMap<>();

    private MissileLaunchTrail() {}

    /**
     * Starts a trail for this silo's launch if one isn't already running (called from the BER while the
     * model is rising). No-op when the Flywheel backend is off — the BER falls back to vanilla particles.
     */
    public static void ensure(MissileLauncherMachine machine) {
        BlockPos pos = machine.getPos();
        if (ACTIVE.containsKey(pos)) {
            return;
        }
        Level level = machine.getLevel();
        Vec3 src = machine.trailSourceWorld();
        if (level == null || src == null || !FlywheelEffectManager.isAvailable(level)) {
            return;
        }
        Entity source = ModEntities.STEALTH_MISSILE.get().create(level);
        if (source == null) {
            return;
        }
        source.setPos(src.x, src.y, src.z);
        source.setDeltaMovement(0.0, MissileLauncherMachine.RISE_SPEED, 0.0);
        FlywheelEffectManager.spawn(new InstancedTrailEffect(source));
        ACTIVE.put(pos.immutable(), source);
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ACTIVE.isEmpty()) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        Iterator<Map.Entry<BlockPos, Entity>> it = ACTIVE.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Entity> entry = it.next();
            Entity source = entry.getValue();
            Vec3 src = level != null
                    && MetaMachine.getMachine(level, entry.getKey()) instanceof MissileLauncherMachine m
                            ? m.trailSourceWorld() : null;
            if (src == null) {
                source.discard(); // effect sees the source gone and expires once its puffs fade
                it.remove();
            } else {
                source.setPos(src.x, src.y, src.z);
                source.setDeltaMovement(0.0, MissileLauncherMachine.RISE_SPEED, 0.0);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        LevelAccessor unloaded = event.getLevel();
        ACTIVE.entrySet().removeIf(e -> {
            if (e.getValue().level() == unloaded) {
                e.getValue().discard();
                return true;
            }
            return false;
        });
    }
}
