package com.norwood.wfcore.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import com.norwood.wfcore.integration.warforge.WarforgeIntegration;


public final class NamePlateVisibility {

    private NamePlateVisibility() {}

    private static final double AIM_REACH = 64.0D;
    private static final double AIM_SLACK = 0.05D;
    private static final int COVER_SCAN = 3;


    public static Font.DisplayMode displayMode(Entity entity) {
        // WarForge teammates always show through walls; everyone else needs the aim/cover/line-of-sight gate.
        // The FactionTeammateCache reference stays lazy behind isLoaded() so its WarForge imports never load
        // when the mod is absent.
        if (WarforgeIntegration.isLoaded() && FactionTeammateCache.isTeammate(entity)) {
            return Font.DisplayMode.SEE_THROUGH;
        }
        return shouldPierce(entity) ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL;
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
