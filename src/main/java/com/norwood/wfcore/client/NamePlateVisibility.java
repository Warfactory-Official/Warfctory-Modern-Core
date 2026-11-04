package com.norwood.wfcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.flansmod.warforge.api.client.NameplatePolicy;
import com.flansmod.warforge.api.client.WarforgeClientApi;
import com.norwood.wfcore.integration.warforge.WarforgeIntegration;


public final class NamePlateVisibility {

    private NamePlateVisibility() {}

    private static final double AIM_REACH = 64.0D;
    private static final double AIM_SLACK = 0.05D;
    private static final int COVER_SCAN = 3;

    /** Beyond this range a player nameplate is only visible to their own WarForge faction. */
    private static final double VISIBLE_RANGE = 20.0D;
    private static final double VISIBLE_RANGE_SQ = VISIBLE_RANGE * VISIBLE_RANGE;



    public static boolean isHidden(Entity entity) {

        if (!WarforgeIntegration.isLoaded() || !(entity instanceof Player player)) {
            return false;
        }
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || camera == entity) {
            return false;
        }
        if (Minecraft.getInstance().getEntityRenderDispatcher().distanceToSqr(entity) <= VISIBLE_RANGE_SQ) {
            return false;
        }
        return !WarforgeClientApi.isTeammate(player);
    }

    public static Font.DisplayMode displayMode(Entity entity) {
        if (WarforgeIntegration.isLoaded() && entity instanceof Player player && WarforgeClientApi.isTeammate(player)) {
            return Font.DisplayMode.SEE_THROUGH;
        }
        return shouldPierce(entity) ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
    }

    /** Behind {@link WarforgeIntegration#isLoaded()}; client only. */
    public static void installWarforgePolicy() {
        WarforgeClientApi.setNameplatePolicy(entity -> {
            if (isHidden(entity)) {
                return NameplatePolicy.Mode.HIDDEN;
            }
            return displayMode(entity) == Font.DisplayMode.SEE_THROUGH
                    ? NameplatePolicy.Mode.SEE_THROUGH : NameplatePolicy.Mode.NORMAL;
        });
    }

    public static boolean shouldPierce(Entity entity) {
        return isCrosshairOver(entity) && isCoveredFromAbove(entity) && hasLineOfSight(entity);
    }

    private static boolean isCrosshairOver(Entity entity) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null || camera == entity) {
            return false;
        }
        Vec3 eye = camera.getEyePosition();
        Vec3 end = eye.add(camera.getViewVector(1.0F).scale(AIM_REACH));
        return entity.getBoundingBox().inflate(AIM_SLACK).clip(eye, end).isPresent();
    }

    private static boolean isCoveredFromAbove(Entity entity) {
        Level level = entity.level();
        int x = Mth.floor(entity.getX());
        int z = Mth.floor(entity.getZ());
        int headTop = Mth.ceil(entity.getBoundingBox().maxY);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < COVER_SCAN; dy++) {
            pos.set(x, headTop + dy, z);
            if (!level.getBlockState(pos).isAir()) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasLineOfSight(Entity entity) {
        Entity camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) {
            return false;
        }
        Vec3 eye = camera.getEyePosition();
        Vec3 target = entity.getBoundingBox().getCenter();
        BlockHitResult hit = entity.level().clip(
                new ClipContext(eye, target, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, camera));
        return hit.getType() == HitResult.Type.MISS;
    }
}
