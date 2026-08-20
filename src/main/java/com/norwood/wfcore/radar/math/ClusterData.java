package com.norwood.wfcore.radar.math;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.List;

/**
 * A detected base: the points that make it up, its center, bounding box, combined richness
 * ({@link #clusterValue}) and the number of players found inside ({@link #playerPopulation}).
 *
 * <p>
 * The 1.12.2 version implemented the computation/{@code IData} capability; the modern radar is
 * EU-only, so that is intentionally dropped.
 */
public class ClusterData {

    public final List<IntCoord2> coordinates;
    public final IntCoord2 centerPoint;
    public final BoundingBox boundingBox;
    public final int clusterValue;
    public final int playerPopulation;

    public ClusterData(List<IntCoord2> coordinates, IntCoord2 centerPoint, BoundingBox boundingBox,
                       int clusterValue, int playerPopulation) {
        this.coordinates = coordinates;
        this.centerPoint = centerPoint;
        this.boundingBox = boundingBox;
        this.clusterValue = clusterValue;
        this.playerPopulation = playerPopulation;
    }

    public CompoundTag toNBT() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("pop", playerPopulation);
        tag.putInt("value", clusterValue);
        tag.put("center", centerPoint.toNBT());
        tag.put("bounds", boundingBox.toNBT());

        ListTag coords = new ListTag();
        for (IntCoord2 coord : coordinates) {
            coords.add(coord.toNBT());
        }
        tag.put("coords", coords);
        return tag;
    }

    public static ClusterData fromNBT(CompoundTag tag) {
        ListTag coordList = tag.getList("coords", Tag.TAG_COMPOUND);
        List<IntCoord2> coords = new ArrayList<>(coordList.size());
        for (int i = 0; i < coordList.size(); i++) {
            coords.add(IntCoord2.fromNBT(coordList.getCompound(i)));
        }

        return new ClusterData(
                coords,
                IntCoord2.fromNBT(tag.getCompound("center")),
                BoundingBox.fromNBT(tag.getCompound("bounds")),
                tag.getInt("value"),
                tag.getInt("pop"));
    }

    @Override
    public String toString() {
        return "Cluster centered on " + centerPoint + " with combined value " + clusterValue + " and " +
                playerPopulation + " player(s) inside " + boundingBox;
    }
}
