package com.norwood.wfcore.common.ballistics;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public final class VirtualProjectile {

    public final UUID id;
    public Vec3 pos;
    public Vec3 vel;
    public final double gravity;
    public final double drag;
    public final int launchTick;
    public int age;

    public long impactTick = -1L;

    public final ResourceLocation adapterId;

    public final CompoundTag typeToken;

    public final UUID shooter;

    public Vec3 impactPos;
    public Direction impactFace;

    public VirtualProjectile(UUID id, Vec3 pos, Vec3 vel, double gravity, double drag, int launchTick,
                             ResourceLocation adapterId, CompoundTag typeToken, UUID shooter) {
        this.id = id;
        this.pos = pos;
        this.vel = vel;
        this.gravity = gravity;
        this.drag = drag;
        this.launchTick = launchTick;
        this.adapterId = adapterId;
        this.typeToken = typeToken;
        this.shooter = shooter;
    }

    private static ListTag vec3ToList(Vec3 v) {
        ListTag list = new ListTag();
        list.add(DoubleTag.valueOf(v.x));
        list.add(DoubleTag.valueOf(v.y));
        list.add(DoubleTag.valueOf(v.z));
        return list;
    }

    private static Vec3 listToVec3(ListTag list) {
        return new Vec3(list.getDouble(0), list.getDouble(1), list.getDouble(2));
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", id);
        tag.put("Pos", vec3ToList(pos));
        tag.put("Vel", vec3ToList(vel));
        tag.putDouble("Gravity", gravity);
        tag.putDouble("Drag", drag);
        tag.putInt("LaunchTick", launchTick);
        tag.putInt("Age", age);
        tag.putLong("ImpactTick", impactTick);
        tag.putString("AdapterId", adapterId.toString());
        tag.put("TypeToken", typeToken);
        if (shooter != null) {
            tag.putUUID("Shooter", shooter);
        }
        if (impactPos != null) {
            tag.put("ImpactPos", vec3ToList(impactPos));
        }
        if (impactFace != null) {
            tag.putInt("ImpactFace", impactFace.get3DDataValue());
        }
        return tag;
    }

    public static VirtualProjectile load(CompoundTag tag) {
        UUID id = tag.getUUID("Id");
        Vec3 pos = listToVec3(tag.getList("Pos", Tag.TAG_DOUBLE));
        Vec3 vel = listToVec3(tag.getList("Vel", Tag.TAG_DOUBLE));
        double gravity = tag.getDouble("Gravity");
        double drag = tag.getDouble("Drag");
        int launchTick = tag.getInt("LaunchTick");
        ResourceLocation adapterId = ResourceLocation.tryParse(tag.getString("AdapterId"));
        CompoundTag typeToken = tag.getCompound("TypeToken");
        UUID shooter = tag.hasUUID("Shooter") ? tag.getUUID("Shooter") : null;

        VirtualProjectile v = new VirtualProjectile(id, pos, vel, gravity, drag, launchTick,
                adapterId, typeToken, shooter);
        v.age = tag.getInt("Age");
        v.impactTick = tag.getLong("ImpactTick");
        if (tag.contains("ImpactPos", Tag.TAG_LIST)) {
            v.impactPos = listToVec3(tag.getList("ImpactPos", Tag.TAG_DOUBLE));
        }
        if (tag.contains("ImpactFace", Tag.TAG_INT)) {
            v.impactFace = Direction.from3DDataValue(tag.getInt("ImpactFace"));
        }
        return v;
    }
}
