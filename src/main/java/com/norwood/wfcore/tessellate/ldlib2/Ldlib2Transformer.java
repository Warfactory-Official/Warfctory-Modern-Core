package com.norwood.wfcore.tessellate.ldlib2;

import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Delivers {@code patches/ldlib2/0001} and {@code 0004} ({@code synchronized}/{@code volatile} flags are not
 * mixin-expressible). Applied from {@code TessellateMixinPlugin.postApply}; validates before mutating, mismatch => throws.
 */
public final class Ldlib2Transformer {

    public static final String ASYNC_DATA = "com.lowdragmc.lowdraglib2.async.AsyncThreadData";
    public static final String LISTENERS = "com.lowdragmc.lowdraglib2.CommonListeners";
    public static final String HOLDER = "com.lowdragmc.lowdraglib2.syncdata.holder.ISyncMangedHolder";
    public static final String FIELD_STORAGE = "com.lowdragmc.lowdraglib2.syncdata.storage.FieldManagedStorage";

    private static final String DATA = "com/lowdragmc/lowdraglib2/async/AsyncThreadData";
    private static final String LEVEL = "net/minecraft/server/level/ServerLevel";
    private static final String LEVEL_DESC = "L" + LEVEL + ";";
    private static final String DATA_DESC = "L" + DATA + ";";
    private static final String MAP = "java/util/Map";
    private static final String INSTANCES = "INSTANCES";
    private static final String GET_OR_CREATE = "(" + LEVEL_DESC + ")" + DATA_DESC;
    private static final String RELEASE = "releaseExecutorService";

    private static final List<String> SYNCHRONIZED = List.of(
            "createExecutorService", "addAsyncLogic", "removeAsyncLogic", RELEASE);

    private Ldlib2Transformer() {
    }

    public static boolean handles(String className) {
        return switch (className) {
            case ASYNC_DATA, LISTENERS, HOLDER, FIELD_STORAGE -> true;
            default -> false;
        };
    }

    public static void apply(String className, ClassNode node) {
        switch (className) {
            case ASYNC_DATA -> asyncThreadData(node);
            case LISTENERS -> commonListeners(node);
            case HOLDER -> syncHolder(node);
            case FIELD_STORAGE -> fieldStorage(node);
            default -> throw new IllegalArgumentException(className);
        }
    }

    private static void fieldStorage(ClassNode node) {
        field(node, "initialized", "Z").access |= Opcodes.ACC_VOLATILE;
    }

    private static void asyncThreadData(ClassNode node) {
        FieldNode periodId = field(node, "periodID", "J");
        MethodNode getOrCreate = method(node, "getOrCreate", GET_OR_CREATE);
        MethodNode clinit = method(node, "<clinit>", "()V");
        List<MethodNode> sync = new ArrayList<>();
        for (String name : SYNCHRONIZED) {
            sync.add(method(node, name, name.equals("addAsyncLogic") || name.equals("removeAsyncLogic")
                    ? "(Lcom/lowdragmc/lowdraglib2/async/IAsyncLogic;)V" : "()V"));
        }
        MethodNode legacyCtor = method(node, "<init>", "(" + LEVEL_DESC + "Lnet/minecraft/nbt/CompoundTag;)V");
        method(node, "<init>", "(" + LEVEL_DESC + ")V");
        for (String added : List.of("get", "remove", INSTANCES)) {
            if (node.methods.stream().anyMatch(m -> m.name.equals(added))
                    || node.fields.stream().anyMatch(f -> f.name.equals(added))) {
                throw new IllegalStateException("already patched: " + added);
            }
        }

        periodId.access |= Opcodes.ACC_VOLATILE;
        sync.forEach(m -> m.access |= Opcodes.ACC_SYNCHRONIZED);

        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC | Opcodes.ACC_FINAL,
                INSTANCES, "L" + MAP + ";", null, null));
        InsnList init = new InsnList();
        init.add(new TypeInsnNode(Opcodes.NEW, "java/util/concurrent/ConcurrentHashMap"));
        init.add(new InsnNode(Opcodes.DUP));
        init.add(new MethodInsnNode(Opcodes.INVOKESPECIAL, "java/util/concurrent/ConcurrentHashMap", "<init>", "()V", false));
        init.add(new FieldInsnNode(Opcodes.PUTSTATIC, DATA, INSTANCES, "L" + MAP + ";"));
        clinit.instructions.insert(init);

        InsnList body = new InsnList();
        body.add(new FieldInsnNode(Opcodes.GETSTATIC, DATA, INSTANCES, "L" + MAP + ";"));
        body.add(new VarInsnNode(Opcodes.ALOAD, 0));
        body.add(new InvokeDynamicInsnNode("apply", "()Ljava/util/function/Function;",
                new Handle(Opcodes.H_INVOKESTATIC, "java/lang/invoke/LambdaMetafactory", "metafactory",
                        "(Ljava/lang/invoke/MethodHandles$Lookup;Ljava/lang/String;Ljava/lang/invoke/MethodType;"
                                + "Ljava/lang/invoke/MethodType;Ljava/lang/invoke/MethodHandle;Ljava/lang/invoke/MethodType;)"
                                + "Ljava/lang/invoke/CallSite;", false),
                Type.getMethodType("(Ljava/lang/Object;)Ljava/lang/Object;"),
                new Handle(Opcodes.H_NEWINVOKESPECIAL, DATA, "<init>", "(" + LEVEL_DESC + ")V", false),
                Type.getMethodType(GET_OR_CREATE)));
        body.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MAP, "computeIfAbsent",
                "(Ljava/lang/Object;Ljava/util/function/Function;)Ljava/lang/Object;", true));
        body.add(new TypeInsnNode(Opcodes.CHECKCAST, DATA));
        body.add(new InsnNode(Opcodes.ARETURN));
        replaceBody(getOrCreate, body);

        node.methods.remove(legacyCtor);
        node.methods.removeIf(m -> m.name.startsWith("lambda$getOrCreate$"));

        MethodNode get = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "get", GET_OR_CREATE, null, null);
        get.instructions.add(new FieldInsnNode(Opcodes.GETSTATIC, DATA, INSTANCES, "L" + MAP + ";"));
        get.instructions.add(new VarInsnNode(Opcodes.ALOAD, 0));
        get.instructions.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MAP, "get", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        get.instructions.add(new TypeInsnNode(Opcodes.CHECKCAST, DATA));
        get.instructions.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(get);

        MethodNode remove = new MethodNode(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "remove", "(" + LEVEL_DESC + ")V", null, null);
        LabelNode end = new LabelNode();
        InsnList r = remove.instructions;
        r.add(new FieldInsnNode(Opcodes.GETSTATIC, DATA, INSTANCES, "L" + MAP + ";"));
        r.add(new VarInsnNode(Opcodes.ALOAD, 0));
        r.add(new MethodInsnNode(Opcodes.INVOKEINTERFACE, MAP, "remove", "(Ljava/lang/Object;)Ljava/lang/Object;", true));
        r.add(new TypeInsnNode(Opcodes.CHECKCAST, DATA));
        r.add(new VarInsnNode(Opcodes.ASTORE, 1));
        r.add(new VarInsnNode(Opcodes.ALOAD, 1));
        r.add(new JumpInsnNode(Opcodes.IFNULL, end));
        r.add(new VarInsnNode(Opcodes.ALOAD, 1));
        r.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, DATA, RELEASE, "()V", false));
        r.add(end);
        r.add(new FrameNode(Opcodes.F_SAME, 0, null, 0, null));
        r.add(new InsnNode(Opcodes.RETURN));
        node.methods.add(remove);
    }

    private static void commonListeners(ClassNode node) {
        int[] replaced = {0};
        for (MethodNode m : node.methods) {
            for (AbstractInsnNode i = m.instructions.getFirst(); i != null; i = i.getNext()) {
                if (isGetOrCreate(i) && i.getNext() instanceof MethodInsnNode release
                        && release.owner.equals(DATA) && release.name.equals(RELEASE)) {
                    m.instructions.remove(release);
                    MethodInsnNode call = (MethodInsnNode) i;
                    call.name = "remove";
                    call.desc = "(" + LEVEL_DESC + ")V";
                    replaced[0]++;
                }
            }
        }
        if (replaced[0] != 2) {
            throw new IllegalStateException("CommonListeners: expected 2 getOrCreate().releaseExecutorService(), found " + replaced[0]);
        }
    }

    private static void syncHolder(ClassNode node) {
        MethodNode detach = method(node, "detachAsyncLogic", "()V");
        MethodInsnNode create = null;
        for (AbstractInsnNode i : detach.instructions) {
            if (isGetOrCreate(i)) {
                if (create != null) {
                    throw new IllegalStateException("detachAsyncLogic: multiple getOrCreate");
                }
                create = (MethodInsnNode) i;
            }
        }
        if (create == null || !(create.getNext() instanceof VarInsnNode aload) || aload.var != 0
                || !(aload.getNext() instanceof MethodInsnNode removeLogic) || !removeLogic.name.equals("removeAsyncLogic")) {
            throw new IllegalStateException("detachAsyncLogic: unexpected shape");
        }
        LabelNode end = null;
        for (AbstractInsnNode i = removeLogic.getNext(); i != null; i = i.getNext()) {
            if (i instanceof LabelNode l) {
                end = l;
                break;
            }
        }
        if (end == null) {
            throw new IllegalStateException("detachAsyncLogic: no end label");
        }
        create.name = "get";
        InsnList after = new InsnList();
        after.add(new VarInsnNode(Opcodes.ASTORE, 1));
        after.add(new VarInsnNode(Opcodes.ALOAD, 1));
        after.add(new JumpInsnNode(Opcodes.IFNULL, end));
        after.add(new VarInsnNode(Opcodes.ALOAD, 1));
        detach.instructions.insert(create, after);
    }

    private static boolean isGetOrCreate(AbstractInsnNode i) {
        return i instanceof MethodInsnNode m && m.getOpcode() == Opcodes.INVOKESTATIC && m.owner.equals(DATA)
                && m.name.equals("getOrCreate") && m.desc.equals(GET_OR_CREATE);
    }

    private static void replaceBody(MethodNode m, InsnList body) {
        m.instructions.clear();
        m.instructions.add(body);
        m.tryCatchBlocks = new ArrayList<>();
        m.localVariables = null;
        m.visibleLocalVariableAnnotations = null;
        m.invisibleLocalVariableAnnotations = null;
    }

    private static FieldNode field(ClassNode node, String name, String desc) {
        return node.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + name + desc + " missing"));
    }

    private static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + name + desc + " missing"));
    }
}
