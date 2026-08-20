package com.norwood.wfcore.radar;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.component.WrittenBookContent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import com.norwood.wfcore.radar.math.ClusterData;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Builds a written book from a completed radar scan. Bases are ranked by richness, then by player
 * count found inside the scan box; each entry reports the estimated center coordinates.
 */
public final class RadarBook {

    private static final int ENTRIES_PER_PAGE = 3;

    private RadarBook() {}

    public static ItemStack createReport(List<ClusterData> clusters) {
        List<ClusterData> ranked = new ArrayList<>(clusters);
        ranked.sort(Comparator
                .comparingInt((ClusterData c) -> c.clusterValue)
                .thenComparingInt(c -> c.playerPopulation)
                .reversed());

        ItemStack book = new ItemStack(Items.WRITTEN_BOOK);
        List<Filterable<Component>> pages = new ArrayList<>();
        pages.add(page("§l§9Radar Scan Report§r\n\n" + ranked.size() + " base(s) detected.\n\n" +
                "Ranked by richness and player presence inside the scan box."));

        StringBuilder current = new StringBuilder();
        int rank = 1;
        int onPage = 0;
        for (ClusterData cluster : ranked) {
            current.append("§l#").append(rank).append(" Base§r\n")
                    .append("Center: ").append(cluster.centerPoint.getX()).append(", ")
                    .append(cluster.centerPoint.getZ()).append('\n')
                    .append("Richness: ").append(cluster.clusterValue).append('\n')
                    .append("Players: ").append(cluster.playerPopulation).append("\n\n");
            rank++;
            if (++onPage >= ENTRIES_PER_PAGE) {
                pages.add(page(current.toString()));
                current.setLength(0);
                onPage = 0;
            }
        }
        if (current.length() > 0) {
            pages.add(page(current.toString()));
        }

        // Written books are a single WRITTEN_BOOK_CONTENT component in 1.21 rather than loose NBT keys,
        // and pages are Components wrapped in Filterable (the chat-filter's raw/filtered pair) instead of
        // pre-serialized JSON strings.
        book.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(
                Filterable.passThrough("Radar Scan Report"), "WFCore Radar", 0, List.copyOf(pages), true));
        return book;
    }

    private static Filterable<Component> page(String text) {
        return Filterable.passThrough(Component.literal(text));
    }
}
