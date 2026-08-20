package com.norwood.wfcore.diagnostics.client;

import com.norwood.wfcore.WFCore;
import com.norwood.wfcore.diagnostics.ModInventory;

import java.io.File;
import java.lang.module.ModuleReader;
import java.lang.module.ResolvedModule;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.Vector;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;


public final class ClassEnumerator {

    /** Backstop so a pathological classpath can't make us allocate without bound. */
    private static final int MAX_CLASSES = 1_000_000;

    public record Result(List<String> classNames, int moduleCount, int modFileCount, int classpathEntries,
                         int failedSources, int filtered, boolean loadedProbe, int loadedProbeCount,
                         boolean includedPlatform, boolean includedDefaultPackage) {}

    private ClassEnumerator() {}

    public static Result enumerate(boolean includePlatform, boolean includeDefaultPackage) {
        TreeSet<String> classes = new TreeSet<>();
        int[] failed = {0};
        int[] filtered = {0};

        int modFileCount = collectModFiles(classes, includePlatform, failed, filtered);
        int moduleCount = collectModules(classes, includePlatform, failed, filtered);
        int cpEntries = collectClasspath(classes, includePlatform, failed, filtered);

        int probeCount = 0;
        boolean probe = false;
        try {
            int before = classes.size();
            probe = collectInitialised(classes, filtered);
            probeCount = classes.size() - before;
        } catch (Throwable ignored) {
        }


        if (!includeDefaultPackage) {
            int before = classes.size();
            classes.removeIf(cn -> cn.indexOf('.') < 0);
            filtered[0] += before - classes.size();
        }

        return new Result(new ArrayList<>(classes), moduleCount, modFileCount, cpEntries, failed[0], filtered[0],
                probe, probeCount, includePlatform, includeDefaultPackage);
    }

    private static int collectModFiles(Set<String> out, boolean includePlatform, int[] failed, int[] filtered) {
        int count = 0;
        for (ModInventory.LoadedFile file : ModInventory.loadedFiles()) {
            Path path = file.path();
            if (path == null || !Files.isRegularFile(path)) {
                continue; // jar-in-jar / union paths aren't real files; their parent jar is scanned instead
            }
            count++;
            try {
                scanJar(path, out, includePlatform, filtered);
            } catch (Throwable t) {
                failed[0]++;
            }
            if (out.size() >= MAX_CLASSES) {
                return count;
            }
        }
        return count;
    }

    private static int collectModules(Set<String> out, boolean includePlatform, int[] failed, int[] filtered) {
        Set<String> seenModules = new HashSet<>();
        int count = 0;
        for (ModuleLayer layer : layers()) {
            for (ResolvedModule module : layer.configuration().modules()) {
                String name = module.name();
                if (!seenModules.add(name)) {
                    continue;
                }
                if (!includePlatform && isPlatformModule(name)) {
                    continue;
                }
                count++;
                try (ModuleReader reader = module.reference().open(); Stream<String> resources = reader.list()) {
                    resources.forEach(res -> addIfClass(out, res, filtered));
                } catch (Throwable t) {
                    failed[0]++;
                }
                if (out.size() >= MAX_CLASSES) {
                    return count;
                }
            }
        }
        return count;
    }

    private static List<ModuleLayer> layers() {
        List<ModuleLayer> result = new ArrayList<>();
        Set<ModuleLayer> seen = newIdentitySet();
        Deque<ModuleLayer> queue = new ArrayDeque<>();
        ModuleLayer own = ClassEnumerator.class.getModule().getLayer();
        if (own != null) {
            queue.add(own);
        }
        queue.add(ModuleLayer.boot());
        while (!queue.isEmpty()) {
            ModuleLayer layer = queue.poll();
            if (layer == null || !seen.add(layer)) {
                continue;
            }
            result.add(layer);
            queue.addAll(layer.parents());
        }
        return result;
    }

    private static Set<ModuleLayer> newIdentitySet() {
        return java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    }

    private static int collectClasspath(Set<String> out, boolean includePlatform, int[] failed, int[] filtered) {
        String cp = System.getProperty("java.class.path", "");
        if (cp.isEmpty()) {
            return 0;
        }
        int entries = 0;
        for (String raw : cp.split(File.pathSeparator)) {
            if (raw.isBlank()) {
                continue;
            }
            entries++;
            Path path = Path.of(raw);
            try {
                if (Files.isDirectory(path)) {
                    scanDir(path, out, includePlatform, filtered);
                } else if (Files.isRegularFile(path) && (raw.endsWith(".jar") || raw.endsWith(".zip"))) {
                    scanJar(path, out, includePlatform, filtered);
                }
            } catch (Throwable t) {
                failed[0]++;
            }
            if (out.size() >= MAX_CLASSES) {
                return entries;
            }
        }
        return entries;
    }

    private static void scanJar(Path jar, Set<String> out, boolean includePlatform, int[] filtered) throws Exception {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            var it = zip.entries();
            while (it.hasMoreElements()) {
                ZipEntry entry = it.nextElement();
                if (!entry.isDirectory()) {
                    addClasspathClass(out, entry.getName(), includePlatform, filtered);
                }
                if (out.size() >= MAX_CLASSES) {
                    return;
                }
            }
        }
    }

    private static void scanDir(Path root, Set<String> out, boolean includePlatform, int[] filtered) throws Exception {
        try (Stream<Path> walk = Files.walk(root)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = root.relativize(p).toString().replace(File.separatorChar, '/');
                addClasspathClass(out, rel, includePlatform, filtered);
            });
        }
    }

    private static void addClasspathClass(Set<String> out, String resource, boolean includePlatform, int[] filtered) {
        String cn = classNameOf(resource);
        if (cn == null || (!includePlatform && isPlatformClass(cn))) {
            return;
        }
        keepOrCount(out, cn, filtered);
    }

      private static boolean collectInitialised(Set<String> out, int[] filtered) throws Exception {
        Field classesField = ClassLoader.class.getDeclaredField("classes");
        if (!classesField.trySetAccessible()) {
            return false;
        }
        Set<ClassLoader> seen = new HashSet<>();
        ClassLoader loader = ClassEnumerator.class.getClassLoader();
        ClassLoader ctx = Thread.currentThread().getContextClassLoader();
        for (ClassLoader start : new ClassLoader[]{loader, ctx}) {
            for (ClassLoader cl = start; cl != null; cl = cl.getParent()) {
                if (!seen.add(cl)) {
                    continue;
                }
                Object value = classesField.get(cl);
                if (value instanceof Vector<?> vec) {
                    for (Object o : vec.toArray()) {
                        if (o instanceof Class<?> c) {
                            // Synthetic/hidden/lambda/proxy and array classes are the usual "garbage" in a
                            // loaded-class list; drop them so the dump stays real class names.
                            if (c.isArray() || c.isHidden() || c.isSynthetic()) {
                                filtered[0]++;
                            } else {
                                keepOrCount(out, c.getName(), filtered);
                            }
                        }
                    }
                }
            }
        }
        return true;
    }



    private static void addIfClass(Set<String> out, String resource, int[] filtered) {
        String cn = classNameOf(resource);
        if (cn != null) {
            keepOrCount(out, cn, filtered);
        }
    }

    /** Keeps a candidate only if it is a well-formed class name, otherwise bumps the filtered count. */
    private static void keepOrCount(Set<String> out, String cn, int[] filtered) {
        if (isValidBinaryClassName(cn)) {
            out.add(cn);
        } else {
            filtered[0]++;
        }
    }


    private static boolean isValidBinaryClassName(String name) {
        if (name == null || name.isEmpty() || name.indexOf('/') >= 0 || name.contains("$$")) {
            return false;
        }
        int segStart = 0;
        for (int i = 0; i <= name.length(); i++) {
            if (i == name.length() || name.charAt(i) == '.') {
                if (i == segStart) {
                    return false; // empty segment: leading/trailing or doubled dot
                }
                if (!Character.isJavaIdentifierStart(name.charAt(segStart))) {
                    return false;
                }
                for (int j = segStart + 1; j < i; j++) {
                    if (!Character.isJavaIdentifierPart(name.charAt(j))) {
                        return false;
                    }
                }
                segStart = i + 1;
            }
        }
        return true;
    }

    /** {@code a/b/C.class} -> {@code a.b.C}; skips module-info, package-info and multi-release/META-INF shells. */
    private static String classNameOf(String resource) {
        if (resource == null || !resource.endsWith(".class")) {
            return null;
        }
        if (resource.endsWith("module-info.class") || resource.endsWith("package-info.class")) {
            return null;
        }
        // Normalise Java 9+ multi-release paths (META-INF/versions/<n>/a/b/C.class) down to a/b/C.class.
        String path = resource;
        if (path.startsWith("META-INF/versions/")) {
            int slash = path.indexOf('/', "META-INF/versions/".length());
            if (slash < 0) {
                return null;
            }
            path = path.substring(slash + 1);
        }
        if (path.startsWith("META-INF/")) {
            return null;
        }
        return path.substring(0, path.length() - ".class".length()).replace('/', '.');
    }

    private static boolean isPlatformModule(String name) {
        return name.startsWith("java.") || name.startsWith("jdk.") || name.startsWith("javafx.")
                || name.equals("java.base");
    }

    private static boolean isPlatformClass(String cn) {
        return cn.startsWith("java.") || cn.startsWith("jdk.") || cn.startsWith("javax.")
                || cn.startsWith("sun.") || cn.startsWith("com.sun.") || cn.startsWith("jakarta.");
    }

    /** Renders the dump body: header comment lines followed by one fully-qualified class name per line, sorted. */
    public static String render(Result result, String playerName) {
        StringBuilder sb = new StringBuilder(result.classNames().size() * 40 + 256);
        sb.append("# WFCore class dump\n");
        sb.append("# player: ").append(playerName).append('\n');
        sb.append("# classes: ").append(result.classNames().size()).append('\n');
        sb.append("# sources: modFiles=").append(result.modFileCount())
                .append(", modules=").append(result.moduleCount())
                .append(", classpathEntries=").append(result.classpathEntries())
                .append(", failedSources=").append(result.failedSources())
                .append(", filtered=").append(result.filtered())
                .append(", initialisedProbe=").append(result.loadedProbe() ? ("yes(" + result.loadedProbeCount() + ")") : "no")
                .append(", platformModules=").append(result.includedPlatform() ? "included" : "excluded")
                .append(", defaultPackage=").append(result.includedDefaultPackage() ? "included" : "excluded (obf Minecraft)")
                .append('\n');
        sb.append("# note: client-reported and client-trusting - a patched client can omit or fake entries.\n");
        sb.append('\n');
        for (String cn : result.classNames()) {
            sb.append(cn).append('\n');
        }
        return sb.toString();
    }

    public static void logSummary(Result result) {
        WFCore.LOGGER.debug("[wfcore-classdump] enumerated {} classes ({} modFiles, {} modules, {} cp entries, {} failed, {} filtered, probe={})",
                result.classNames().size(), result.modFileCount(), result.moduleCount(), result.classpathEntries(),
                result.failedSources(), result.filtered(), result.loadedProbe());
    }
}
