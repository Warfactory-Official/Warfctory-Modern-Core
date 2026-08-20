package com.norwood.wfcore.api.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Automatic layered placement for a category's research nodes. Computes a {@code (column, row)} grid coord for
 * every node:
 * <ul>
 * <li><b>column</b> = the longest prerequisite chain within the category (roots at column 0), i.e. the node's
 * depth, so unlock flow reads left to right;</li>
 * <li><b>row</b> = packed near the average row of its prerequisites, skipping rows already taken (so siblings
 * fan out and several independent roots stack into parallel trees).</li>
 * </ul>
 * Nodes pinned with {@link Research.Builder#pos(int, int)} keep their exact cell, and those cells are reserved
 * first so auto-placed nodes never land on top of them — a tree can freely mix manual and automatic nodes.
 * Purely a function of the (stable) registry, so the layout is deterministic across GUI opens.
 */
public final class ResearchLayout {

    private ResearchLayout() {}

    /** @return id -&gt; {column, row} for every node in {@code nodes} (already filtered to one category). */
    public static Map<String, int[]> compute(List<Research> nodes) {
        Map<String, Research> byId = new HashMap<>();
        for (Research r : nodes) byId.put(r.getId(), r);

        Map<String, Integer> column = new HashMap<>();
        for (Research r : nodes) resolveColumn(r, byId, column, new HashSet<>());

        // Group by column (ascending) so a node's prerequisites already have rows when we place it.
        TreeMap<Integer, List<Research>> byColumn = new TreeMap<>();
        for (Research r : nodes) byColumn.computeIfAbsent(column.get(r.getId()), k -> new ArrayList<>()).add(r);

        Map<String, Integer> row = new HashMap<>();
        for (List<Research> columnNodes : byColumn.values()) {
            Set<Integer> used = new HashSet<>();
            List<Research> autos = new ArrayList<>();
            for (Research r : columnNodes) {
                if (r.hasManualPos()) {
                    row.put(r.getId(), r.getGridY());
                    used.add(r.getGridY());
                } else {
                    autos.add(r);
                }
            }
            autos.sort(Comparator.comparingInt(r -> desiredRow(r, byId, row)));
            for (Research r : autos) {
                int placed = nearestFreeRow(desiredRow(r, byId, row), used);
                row.put(r.getId(), placed);
                used.add(placed);
            }
        }

        Map<String, int[]> result = new HashMap<>();
        for (Research r : nodes) {
            int c = r.hasManualPos() ? r.getGridX() : column.get(r.getId());
            result.put(r.getId(), new int[] { c, row.get(r.getId()) });
        }
        return result;
    }

    private static int resolveColumn(Research r, Map<String, Research> byId, Map<String, Integer> column,
                                     Set<String> stack) {
        String id = r.getId();
        Integer cached = column.get(id);
        if (cached != null) return cached;
        if (r.hasManualPos()) {
            column.put(id, r.getGridX());
            return r.getGridX();
        }
        if (!stack.add(id)) return 0; // cycle guard
        int c = 0;
        for (String prereq : r.getPrerequisites()) {
            Research p = byId.get(prereq); // only in-category prerequisites affect depth
            if (p != null) c = Math.max(c, resolveColumn(p, byId, column, stack) + 1);
        }
        stack.remove(id);
        column.put(id, c);
        return c;
    }

    private static int desiredRow(Research r, Map<String, Research> byId, Map<String, Integer> row) {
        int sum = 0;
        int n = 0;
        for (String prereq : r.getPrerequisites()) {
            Research p = byId.get(prereq);
            Integer pr = p == null ? null : row.get(p.getId());
            if (pr != null) {
                sum += pr;
                n++;
            }
        }
        return n == 0 ? 0 : Math.floorDiv(sum, n);
    }

    private static int nearestFreeRow(int desired, Set<Integer> used) {
        if (!used.contains(desired)) return desired;
        for (int d = 1; d < 4096; d++) {
            if (!used.contains(desired + d)) return desired + d;
            if (!used.contains(desired - d)) return desired - d;
        }
        return desired;
    }
}
