package com.norwood.wfcore.tessellate.ae2things;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the transformer on the stock AE2-Things 1.4.2-beta classes and links the result (JVM verifier). */
class Ae2ThingsTransformerTest {

    private static final List<String> TARGETS = List.of(
            Ae2ThingsTransformer.MOD, Ae2ThingsTransformer.MANAGER, Ae2ThingsTransformer.DATA);

    private static final Map<String, byte[]> STOCK = new HashMap<>();
    private static final Map<String, byte[]> PATCHED = new HashMap<>();
    private static ZipFile jarFile;

    @BeforeAll
    static void load() throws IOException {
        Path jar = Path.of(System.getProperty("ae2things.stock.jar",
                "../threading-eval/srv-final/mods/AE2-Things-1.4.2-beta.jar"));
        assumeTrue(Files.exists(jar), "stock AE2-Things jar absent: " + jar);
        jarFile = new ZipFile(jar.toFile());
        for (String name : TARGETS) {
            try (InputStream in = jarFile.getInputStream(jarFile.getEntry(name.replace('.', '/') + ".class"))) {
                byte[] bytes = in.readAllBytes();
                STOCK.put(name, bytes);
                ClassNode node = new ClassNode();
                new ClassReader(bytes).accept(node, 0);
                Ae2ThingsTransformer.apply(name, node);
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                PATCHED.put(name, writer.toByteArray());
            }
        }
    }

    private static ClassNode node(Map<String, byte[]> from, String name) {
        ClassNode node = new ClassNode();
        new ClassReader(from.get(name)).accept(node, 0);
        return node;
    }

    @Test
    void flagsApplied() {
        ClassNode mod = node(PATCHED, Ae2ThingsTransformer.MOD);
        for (String f : List.of("storageManager", "storageManagerServer")) {
            assertTrue(mod.fields.stream().anyMatch(x -> x.name.equals(f) && (x.access & Opcodes.ACC_VOLATILE) != 0), f);
        }
        ClassNode manager = node(PATCHED, Ae2ThingsTransformer.MANAGER);
        assertTrue(manager.fields.stream().anyMatch(x -> x.name.equals("registries") && (x.access & Opcodes.ACC_VOLATILE) != 0));
        for (String m : List.of("save", "updateDisk", "removeDisk", "hasUUID", "getOrCreateDisk", "modifyDisk")) {
            assertTrue(manager.methods.stream().anyMatch(x -> x.name.equals(m) && (x.access & Opcodes.ACC_SYNCHRONIZED) != 0), m);
        }
        ClassNode data = node(PATCHED, Ae2ThingsTransformer.DATA);
        for (String f : List.of("stackKeys", "stackAmounts", "itemCount")) {
            assertTrue(data.fields.stream().anyMatch(x -> x.name.equals(f) && (x.access & Opcodes.ACC_FINAL) != 0), f);
        }
    }

    @Test
    void stockHasNoneOfThem() {
        ClassNode manager = node(STOCK, Ae2ThingsTransformer.MANAGER);
        assertTrue(manager.methods.stream().noneMatch(x -> (x.access & Opcodes.ACC_SYNCHRONIZED) != 0));
        assertTrue(manager.fields.stream().noneMatch(x -> (x.access & Opcodes.ACC_VOLATILE) != 0));
    }

    @Test
    void rejectsSecondApplication() {
        for (String name : TARGETS) {
            ClassNode patched = node(PATCHED, name);
            assertThrows(IllegalStateException.class, () -> Ae2ThingsTransformer.apply(name, patched), name);
        }
    }

    @Test
    void rejectsFinalFieldWrittenOutsideConstructor() {
        ClassNode data = node(STOCK, Ae2ThingsTransformer.DATA);
        var writer = new org.objectweb.asm.tree.MethodNode(Opcodes.ACC_PUBLIC, "poke", "()V", null, null);
        writer.instructions.add(new org.objectweb.asm.tree.VarInsnNode(Opcodes.ALOAD, 0));
        writer.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.LCONST_0));
        writer.instructions.add(new org.objectweb.asm.tree.FieldInsnNode(Opcodes.PUTFIELD, data.name, "itemCount", "J"));
        writer.instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.RETURN));
        data.methods.add(writer);
        assertThrows(IllegalStateException.class, () -> Ae2ThingsTransformer.apply(Ae2ThingsTransformer.DATA, data));
    }

    @Test
    void patchedClassesVerify() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = PATCHED.get(name);
                if (bytes == null && name.startsWith("io.github.projectet.ae2things.")) {
                    var entry = jarFile.getEntry(name.replace('.', '/') + ".class");
                    if (entry != null) {
                        try (InputStream in = jarFile.getInputStream(entry)) {
                            bytes = in.readAllBytes();
                        } catch (IOException e) {
                            throw new ClassNotFoundException(name, e);
                        }
                    }
                }
                if (bytes == null) {
                    return super.loadClass(name, resolve);
                }
                synchronized (getClassLoadingLock(name)) {
                    Class<?> c = findLoadedClass(name);
                    return c != null ? c : defineClass(name, bytes, 0, bytes.length);
                }
            }
        };
        for (String name : TARGETS) {
            Class<?> c = Class.forName(name, false, loader);
            try {
                c.getDeclaredMethods();
                Class.forName(name, true, loader);
            } catch (VerifyError | ClassFormatError e) {
                throw new AssertionError(name, e);
            } catch (LinkageError unresolvedRuntimeDependency) {
                System.err.println("link " + name + ": " + unresolvedRuntimeDependency);
            }
        }
    }
}
