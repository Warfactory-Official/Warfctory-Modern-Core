package com.norwood.wfcore.radar;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.norwood.wfcore.radar.RadarClustering.DataPoint;
import com.norwood.wfcore.radar.RadarClustering.TargetType;
import com.norwood.wfcore.radar.math.ClusterData;
import com.norwood.wfcore.radar.math.IntCoord2;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


public final class RadarScanExporter {

    private RadarScanExporter() {}

    public static JsonObject toJson(String dimensionId, long gameTime, long generatedAtEpochMs,
                                    Map<IntCoord2, DataPoint> targets, List<ClusterData> clusters,
                                    int eps, int minPts) {
        Set<IntCoord2> clustered = Collections.newSetFromMap(new IdentityHashMap<>());
        for (ClusterData cluster : clusters) {
            clustered.addAll(cluster.coordinates);
        }

        JsonObject root = new JsonObject();
        root.addProperty("dimension", dimensionId);
        root.addProperty("gameTime", gameTime);
        root.addProperty("generatedAtEpochMs", generatedAtEpochMs);
        root.addProperty("eps", eps);
        root.addProperty("minPts", minPts);
        root.addProperty("targetCount", targets.size());
        root.addProperty("clusterCount", clusters.size());

        JsonArray points = new JsonArray();
        for (Map.Entry<IntCoord2, DataPoint> entry : targets.entrySet()) {
            points.add(pointJson(entry.getKey(), entry.getValue(), clustered.contains(entry.getKey())));
        }
        root.add("points", points);

        JsonArray clusterArr = new JsonArray();
        for (int i = 0; i < clusters.size(); i++) {
            ClusterData cluster = clusters.get(i);
            JsonObject obj = new JsonObject();
            obj.addProperty("index", i);
            obj.add("center", coordJson(cluster.centerPoint));

            JsonObject bounds = new JsonObject();
            bounds.add("min", coordJson(cluster.boundingBox.getMin()));
            bounds.add("max", coordJson(cluster.boundingBox.getMax()));
            obj.add("bounds", bounds);

            obj.addProperty("clusterValue", cluster.clusterValue);
            obj.addProperty("playerPopulation", cluster.playerPopulation);
            obj.addProperty("pointCount", cluster.coordinates.size());

            JsonArray clusterPoints = new JsonArray();
            JsonArray clusterPlayers = new JsonArray();
            for (IntCoord2 coord : cluster.coordinates) {
                DataPoint dp = targets.get(coord);
                clusterPoints.add(pointJson(coord, dp, true));
                if (dp != null && dp.type() == TargetType.PLAYER) {
                    clusterPlayers.add(coordJson(coord));
                }
            }
            obj.add("points", clusterPoints);
            obj.add("players", clusterPlayers);
            clusterArr.add(obj);
        }
        root.add("clusters", clusterArr);
        return root;
    }

    public static String toPrettyString(JsonObject obj) {
        return new GsonBuilder().setPrettyPrinting().create().toJson(obj);
    }

    private static JsonObject coordJson(IntCoord2 coord) {
        JsonObject obj = new JsonObject();
        obj.addProperty("x", coord.getX());
        obj.addProperty("z", coord.getZ());
        return obj;
    }

    private static JsonObject pointJson(IntCoord2 coord, DataPoint dp, boolean clustered) {
        JsonObject obj = coordJson(coord);
        obj.addProperty("type", dp != null ? dp.type().name() : TargetType.STRUCTURE.name());
        obj.addProperty("value", dp != null ? dp.value() : 0);
        obj.addProperty("clustered", clustered);
        return obj;
    }
}
