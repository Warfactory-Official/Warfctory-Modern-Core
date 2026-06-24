package com.norwood.wfcore.api.research;

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Registry of {@link ResearchCategory} tabs. Populated during mod init (Java or KubeJS) via
 * {@link ResearchCategory.Builder#register()}. Insertion order is preserved so tabs render in a stable order.
 * Categories referenced by a research but never explicitly registered are filled in on demand with a default.
 */
public final class ResearchCategoryRegistry {

    private static final Map<String, ResearchCategory> CATEGORIES = new LinkedHashMap<>();

    private ResearchCategoryRegistry() {}

    public static void register(ResearchCategory category) {
        if (category != null) CATEGORIES.put(category.getId(), category);
    }

    public static void unregister(String id) {
        if (id != null) CATEGORIES.remove(id);
    }

    @Nullable
    public static ResearchCategory get(String id) {
        return id == null ? null : CATEGORIES.get(id);
    }

    public static boolean exists(String id) {
        return CATEGORIES.containsKey(id);
    }

    /** The registered category for this id, or a default one (registered now) for ad-hoc string categories. */
    public static ResearchCategory getOrCreate(String id) {
        ResearchCategory category = CATEGORIES.get(id);
        if (category == null) {
            category = ResearchCategory.createDefault(id);
            CATEGORIES.put(id, category);
        }
        return category;
    }

    public static Collection<ResearchCategory> all() {
        return Collections.unmodifiableCollection(CATEGORIES.values());
    }
}
