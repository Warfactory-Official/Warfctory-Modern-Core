package com.norwood.wfcore.tessellate;

import com.norwood.wfcore.tessellate.ir.IrTransformer;
import com.norwood.wfcore.tessellate.kubejs.KubeJsTransformer;
import com.norwood.wfcore.tessellate.umc.UmcTransformer;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Transforms stock UMC 1.3.1 / IR 1.11.0 classes like Mixin's writer (COMPUTE_FRAMES) and links them: VerifyError => fail. */
class StockJarTransformerTest {

    private static final List<String> UMC = List.of(UmcTransformer.WORLD, UmcTransformer.ENTITY_SYNC, UmcTransformer.FLUID,
            UmcTransformer.MODDED_ENTITY);
    private static final List<String> IR = List.of(IrTransformer.MOVEABLE, IrTransformer.COUPLEABLE, IrTransformer.ROLLING,
            IrTransformer.CONTROL_MAPPER, IrTransformer.RIDABLE, IrTransformer.SIMULATION, IrTransformer.CHRONO,
            IrTransformer.RAIL_BASE, IrTransformer.RAIL, IrTransformer.DEFINITIONS);

    private static Path jar(String property, String fallback) {
        Path jar = Path.of(System.getProperty(property, fallback));
        assumeTrue(Files.exists(jar), "stock jar absent: " + jar);
        return jar;
    }

    private static byte[] read(ZipFile zip, String name) throws IOException {
        try (InputStream in = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
            return in.readAllBytes();
        }
    }

    private static byte[] transform(byte[] stock, String name, BiConsumer<String, ClassNode> edit, ClassLoader hierarchy) {
        ClassNode node = new ClassNode();
        new ClassReader(stock).accept(node, ClassReader.EXPAND_FRAMES);
        edit.accept(name, node);
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String a, String b) {
                try {
                    Class<?> x = Class.forName(a.replace('/', '.'), false, hierarchy);
                    Class<?> y = Class.forName(b.replace('/', '.'), false, hierarchy);
                    if (x.isAssignableFrom(y)) {
                        return a;
                    }
                    if (y.isAssignableFrom(x)) {
                        return b;
                    }
                    if (x.isInterface() || y.isInterface()) {
                        return "java/lang/Object";
                    }
                    do {
                        x = x.getSuperclass();
                    } while (!x.isAssignableFrom(y));
                    return x.getName().replace('.', '/');
                } catch (ClassNotFoundException | LinkageError unresolvable) {
                    return "java/lang/Object";
                }
            }
        };
        node.accept(writer);
        return writer.toByteArray();
    }

    private void run(Path jar, Path dependency, List<String> names, BiConsumer<String, ClassNode> edit, String prefix) throws Exception {
        Map<String, byte[]> patched = new HashMap<>();
        try (URLClassLoader stockLoader = new URLClassLoader(new URL[]{jar.toUri().toURL(), dependency.toUri().toURL()}, getClass().getClassLoader());
             ZipFile zip = new ZipFile(jar.toFile())) {
            for (String name : names) {
                byte[] stock = read(zip, name);
                patched.put(name, transform(stock, name, edit, stockLoader));
                ClassNode again = new ClassNode();
                new ClassReader(patched.get(name)).accept(again, 0);
                assertThrows(RuntimeException.class, () -> edit.accept(name, again), name + " second application");
            }
            // child-first definition of the patched classes
            ClassLoader child = new ClassLoader(stockLoader) {
                @Override
                protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                    byte[] bytes = patched.get(name);
                    if (bytes == null) {
                        return super.loadClass(name, resolve);
                    }
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> c = findLoadedClass(name);
                        return c != null ? c : defineClass(name, bytes, 0, bytes.length);
                    }
                }
            };
            for (String name : names) {
                try {
                    Class<?> c = Class.forName(name, false, child);
                    c.getDeclaredMethods();
                    Class.forName(name, true, child);
                } catch (VerifyError | ClassFormatError e) {
                    throw new AssertionError(name, e);
                } catch (LinkageError unresolvedRuntimeDependency) {
                    System.err.println("link " + prefix + " " + name + ": " + unresolvedRuntimeDependency);
                }
            }
        }
    }

    @Test
    void umcClassesTransformAndVerify() throws Exception {
        Path umc = jar("umc.stock.jar", "../threading-eval/srv-final/mods/UniversalModCore-1.21.1-neoforge-1.3.1.jar");
        run(umc, umc, UMC,
                UmcTransformer::apply, "umc");
    }

    @Test
    void irClassesTransformAndVerify() throws Exception {
        run(jar("ir.stock.jar", "../threading-eval/srv-final/mods/ImmersiveRailroading-1.21.1-neoforge-1.11.0.jar"),
                jar("umc.stock.jar", "../threading-eval/srv-final/mods/UniversalModCore-1.21.1-neoforge-1.3.1.jar"), IR,
                IrTransformer::apply, "ir");
    }

    @Test
    void kubejsClassesTransformAndVerify() throws Exception {
        Path kubejs = jar("kubejs.stock.jar", "../threading-eval/srv-final/mods/kubejs-neoforge-2101.7.2-build.381.jar");
        run(kubejs, jar("rhino.stock.jar", "../threading-eval/srv-final/mods/rhino-2101.2.8-build.91.jar"),
                List.of(KubeJsTransformer.EVENT_HANDLER, KubeJsTransformer.PLUGIN, KubeJsTransformer.CONTEXT_FACTORY,
                        KubeJsTransformer.SCRIPT_MANAGER, KubeJsTransformer.CONSOLE, KubeJsTransformer.LISTENERS),
                KubeJsTransformer::apply, "kubejs");
    }
}
