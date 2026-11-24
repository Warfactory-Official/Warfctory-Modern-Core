package com.norwood.wfcore.tessellate.ldlib2;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Runs the transformer on the stock LDLib2 2.2.42 classes and links the result (JVM verifier). */
class Ldlib2TransformerTest {

    private static final List<String> TARGETS = List.of(
            Ldlib2Transformer.ASYNC_DATA, Ldlib2Transformer.LISTENERS, Ldlib2Transformer.HOLDER, Ldlib2Transformer.FIELD_STORAGE);

    private static final Map<String, byte[]> STOCK = new HashMap<>();
    private static final Map<String, byte[]> PATCHED = new HashMap<>();

    private static final String LEVEL_STUB = "net.minecraft.server.level.ServerLevel";

    private static ZipFile jarFile;

    @BeforeAll
    static void load() throws IOException {
        Path jar = Path.of(System.getProperty("ldlib2.stock.jar",
                "../threading-eval/srv-final/mods/ldlib2-neoforge-1.21.1-2.2.42-all.jar"));
        assumeTrue(Files.exists(jar), "stock LDLib2 2.2.42 jar absent: " + jar);
        jarFile = new ZipFile(jar.toFile());
        {
            ZipFile zip = jarFile;
            for (String name : TARGETS) {
                try (InputStream in = zip.getInputStream(zip.getEntry(name.replace('.', '/') + ".class"))) {
                    byte[] bytes = in.readAllBytes();
                    STOCK.put(name, bytes);
                    ClassNode node = new ClassNode();
                    new ClassReader(bytes).accept(node, 0);
                    Ldlib2Transformer.apply(name, node);
                    ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                    node.accept(writer);
                    PATCHED.put(name, writer.toByteArray());
                    if (System.getProperty("ldlib2.dump") != null) {
                        Path out = Path.of(System.getProperty("ldlib2.dump"), name.replace('.', '/') + ".class");
                        Files.createDirectories(out.getParent());
                        Files.write(out, PATCHED.get(name));
                    }
                }
            }
        }
    }

    private static ClassNode node(String name) {
        ClassNode node = new ClassNode();
        new ClassReader(PATCHED.get(name)).accept(node, 0);
        return node;
    }

    @Test
    void flagsApplied() {
        ClassNode data = node(Ldlib2Transformer.ASYNC_DATA);
        for (String m : List.of("createExecutorService", "addAsyncLogic", "removeAsyncLogic", "releaseExecutorService")) {
            assertTrue(data.methods.stream().anyMatch(x -> x.name.equals(m) && (x.access & Opcodes.ACC_SYNCHRONIZED) != 0), m);
        }
        assertTrue(data.fields.stream().anyMatch(f -> f.name.equals("periodID") && (f.access & Opcodes.ACC_VOLATILE) != 0));
        assertTrue(node(Ldlib2Transformer.FIELD_STORAGE).fields.stream()
                .anyMatch(f -> f.name.equals("initialized") && (f.access & Opcodes.ACC_VOLATILE) != 0));
    }

    @Test
    void stockHasNoneOfThem() {
        ClassNode stock = new ClassNode();
        new ClassReader(STOCK.get(Ldlib2Transformer.FIELD_STORAGE)).accept(stock, 0);
        assertTrue(stock.fields.stream().noneMatch(f -> f.name.equals("initialized") && (f.access & Opcodes.ACC_VOLATILE) != 0));
    }

    @Test
    void rejectsSecondApplication() {
        ClassNode data = node(Ldlib2Transformer.ASYNC_DATA);
        try {
            Ldlib2Transformer.apply(Ldlib2Transformer.ASYNC_DATA, data);
        } catch (IllegalStateException expected) {
            return;
        }
        throw new AssertionError("double application accepted");
    }

    /** Defines the patched classes (+ a bare ServerLevel stub) in a child-first loader; init=true forces verification (VerifyError => fail). */
    @Test
    void patchedClassesVerifyAndBehave() throws Exception {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                byte[] bytes = PATCHED.get(name);
                if (name.equals(LEVEL_STUB)) {
                    ClassWriter stub = new ClassWriter(0);
                    stub.visit(Opcodes.V21, Opcodes.ACC_PUBLIC, LEVEL_STUB.replace('.', '/'), null, "java/lang/Object", null);
                    var init = stub.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                    init.visitCode();
                    init.visitVarInsn(Opcodes.ALOAD, 0);
                    init.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                    init.visitInsn(Opcodes.RETURN);
                    init.visitMaxs(1, 1);
                    init.visitEnd();
                    stub.visitEnd();
                    bytes = stub.toByteArray();
                }
                if (bytes == null && name.startsWith("com.lowdragmc.lowdraglib2.")) {
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
            c.getDeclaredMethods();
            c.getDeclaredFields();
            try {
                Class.forName(name, true, loader);
            } catch (VerifyError | ClassFormatError e) {
                throw new AssertionError(name, e);
            } catch (LinkageError unresolvedRuntimeDependency) {
                // verification precedes <clinit>; missing game runtime in the test JVM is not a failure
                System.err.println("link " + name + ": " + unresolvedRuntimeDependency);
            }
        }
        Class<?> data = Class.forName(Ldlib2Transformer.ASYNC_DATA, false, loader);
        Method get = data.getMethod("get", Class.forName(LEVEL_STUB, false, loader));
        Method remove = data.getMethod("remove", get.getParameterTypes()[0]);
        assertTrue(Modifier.isStatic(get.getModifiers()) && Modifier.isStatic(remove.getModifiers()));
        Object level = get.getParameterTypes()[0].getDeclaredConstructor().newInstance();
        Method getOrCreate = data.getMethod("getOrCreate", get.getParameterTypes()[0]);
        assertNull(get.invoke(null, level));
        Object created = getOrCreate.invoke(null, level);
        assertNotNull(created);
        assertSame(created, getOrCreate.invoke(null, level));
        assertSame(created, get.invoke(null, level));
        remove.invoke(null, level);
        assertNull(get.invoke(null, level));
        remove.invoke(null, level);
        assertSame(data, get.getReturnType());
    }
}
