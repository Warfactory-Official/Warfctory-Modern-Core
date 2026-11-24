package com.norwood.wfcore.tessellate.kubejs;

import com.norwood.wfcore.tessellate.asm.ClassEdits;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** {@code patches/kubejs/0001} on stock 2101.7.2-build.381; validate-then-mutate, mismatch => throws. */
public final class KubeJsTransformer {

    private static final String K = "dev.latvian.mods.kubejs.";
    public static final String EVENT_HANDLER = K + "event.EventHandler";
    public static final String PLUGIN = K + "plugin.builtin.BuiltinKubeJSPlugin";
    public static final String CONTEXT_FACTORY = K + "script.KubeJSContextFactory";
    public static final String SCRIPT_MANAGER = K + "script.ScriptManager";
    public static final String CONSOLE = K + "script.ConsoleJS";
    public static final String LISTENERS = K + "plugin.builtin.wrapper.NativeEventWrapper$Listeners";

    private static final String REGIONS = "com/norwood/wfcore/tessellate/kubejs/KubeJsRegions";
    private static final String OBJ = "Ljava/lang/Object;";

    private KubeJsTransformer() {
    }

    public static boolean handles(String className) {
        return switch (className) {
            case EVENT_HANDLER, PLUGIN, CONTEXT_FACTORY, SCRIPT_MANAGER, CONSOLE, LISTENERS -> true;
            default -> false;
        };
    }

    public static void apply(String className, ClassNode node) {
        for (MethodNode m : node.methods) {
            if (m.name.startsWith("tessellate$") || calls(m, Opcodes.INVOKESTATIC, REGIONS, "post").size()
                    + calls(m, Opcodes.INVOKESTATIC, REGIONS, "install").size()
                    + calls(m, Opcodes.INVOKESTATIC, REGIONS, "loadContext").size()
                    + calls(m, Opcodes.INVOKESTATIC, REGIONS, "unlessRegionThread").size()
                    + calls(m, Opcodes.INVOKESTATIC, REGIONS, "nativeEvent").size() > 0) {
                throw new IllegalStateException("already patched: " + node.name);
            }
        }
        switch (className) {
            case EVENT_HANDLER -> eventHandler(node);
            case PLUGIN -> ClassEdits.swapNewFeedingField(node, "<clinit>", "GLOBAL", "java/util/HashMap",
                    "com/norwood/wfcore/tessellate/kubejs/SynchronizedHashMap", 1);
            case CONTEXT_FACTORY -> contextFactory(node);
            case SCRIPT_MANAGER -> scriptManager(node);
            case CONSOLE -> console(node);
            case LISTENERS -> listeners(node);
            default -> throw new IllegalArgumentException(className);
        }
    }

    private static List<MethodInsnNode> calls(MethodNode m, int opcode, String owner, String name) {
        List<MethodInsnNode> found = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode c && c.getOpcode() == opcode && c.owner.equals(owner) && c.name.equals(name)) {
                found.add(c);
            }
        }
        return found;
    }

    private static void eventHandler(ClassNode node) {
        String desc = "(Ldev/latvian/mods/kubejs/script/ScriptTypeHolder;Ljava/lang/Object;Ldev/latvian/mods/kubejs/event/KubeEvent;)"
                + "Ldev/latvian/mods/kubejs/event/EventResult;";
        MethodNode post = ClassEdits.method(node, "postInternal", desc);
        FieldInsnNode resultRead = new FieldInsnNode(Opcodes.GETFIELD, node.name, "result", "Ldev/latvian/mods/rhino/type/TypeInfo;");
        ClassEdits.field(node, "result", resultRead.desc);
        if ((post.access & Opcodes.ACC_PROTECTED) == 0) {
            throw new IllegalStateException(node.name + ".postInternal is not protected");
        }
        post.name = "tessellate$dispatch";
        post.access = (post.access & ~Opcodes.ACC_PROTECTED) | Opcodes.ACC_PUBLIC;

        MethodNode bridge = new MethodNode(Opcodes.ACC_PROTECTED, "postInternal", desc, null, null);
        LabelNode fast = new LabelNode();
        InsnList b = bridge.instructions;
        b.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "onRegionThread", "()Z", false));
        b.add(new JumpInsnNode(Opcodes.IFEQ, fast));
        for (int v = 0; v < 4; v++) {
            b.add(new VarInsnNode(Opcodes.ALOAD, v));
        }
        b.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "post", "(" + OBJ + OBJ + OBJ + OBJ + ")" + OBJ, false));
        b.add(new TypeInsnNode(Opcodes.CHECKCAST, "dev/latvian/mods/kubejs/event/EventResult"));
        b.add(new InsnNode(Opcodes.ARETURN));
        b.add(fast);
        for (int v = 0; v < 4; v++) {
            b.add(new VarInsnNode(Opcodes.ALOAD, v));
        }
        b.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "tessellate$dispatch", desc, false));
        b.add(new InsnNode(Opcodes.ARETURN));
        node.methods.add(bridge);

        MethodNode hasResult = new MethodNode(Opcodes.ACC_PUBLIC, "tessellate$hasResult", "()Z", null, null);
        LabelNode none = new LabelNode();
        InsnList h = hasResult.instructions;
        h.add(new VarInsnNode(Opcodes.ALOAD, 0));
        h.add(resultRead);
        h.add(new JumpInsnNode(Opcodes.IFNULL, none));
        h.add(new InsnNode(Opcodes.ICONST_1));
        h.add(new InsnNode(Opcodes.IRETURN));
        h.add(none);
        h.add(new InsnNode(Opcodes.ICONST_0));
        h.add(new InsnNode(Opcodes.IRETURN));
        node.methods.add(hasResult);
    }

    private static void contextFactory(ClassNode node) {
        MethodNode ctor = ClassEdits.method(node, "<init>", "(Ldev/latvian/mods/kubejs/script/ScriptManager;)V");
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode i : ctor.instructions) {
            if (i.getOpcode() == Opcodes.RETURN) {
                returns.add(i);
            }
        }
        if (returns.size() != 1) {
            throw new IllegalStateException("KubeJSContextFactory.<init>: expected 1 return, found " + returns.size());
        }
        ctor.instructions.insertBefore(returns.get(0), new VarInsnNode(Opcodes.ALOAD, 0));
        ctor.instructions.insertBefore(returns.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "install", "(" + OBJ + ")V", false));
    }

    private static void scriptManager(ClassNode node) {
        MethodNode load = ClassEdits.method(node, "load", "(J)V");
        List<MethodInsnNode> enter = calls(load, Opcodes.INVOKEVIRTUAL, "dev/latvian/mods/kubejs/script/KubeJSContextFactory", "enter");
        if (enter.size() != 1) {
            throw new IllegalStateException("ScriptManager.load: expected 1 factory.enter(), found " + enter.size());
        }
        load.instructions.insertBefore(enter.get(0), new InsnNode(Opcodes.DUP));
        load.instructions.insert(enter.get(0), new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "loadContext", "(" + OBJ + OBJ + ")" + OBJ, false));
    }

    private static void console(ClassNode node) {
        List<MethodNode> owners = new ArrayList<>();
        List<MethodInsnNode> sites = new ArrayList<>();
        for (MethodNode m : node.methods) {
            List<MethodInsnNode> get = calls(m, Opcodes.INVOKEVIRTUAL, "java/lang/ref/WeakReference", "get");
            if (get.size() > 1) {
                throw new IllegalStateException("ConsoleJS." + m.name + ": expected 1 WeakReference.get, found " + get.size());
            }
            if (get.size() == 1) {
                owners.add(m);
                sites.add(get.get(0));
            }
        }
        if (owners.size() != 2 || owners.stream().noneMatch(m -> m.name.equals("line"))
                || owners.stream().noneMatch(m -> m.name.equals("getScriptLine"))) {
            throw new IllegalStateException("ConsoleJS: expected WeakReference.get in line + getScriptLine, found " + owners.size());
        }
        for (int i = 0; i < sites.size(); i++) {
            owners.get(i).instructions.insert(sites.get(i), new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "unlessRegionThread",
                    "(" + OBJ + ")" + OBJ, false));
        }
    }

    private static void listeners(ClassNode node) {
        MethodNode accept = ClassEdits.method(node, "accept", "(Lnet/neoforged/bus/api/Event;)V");
        InsnList head = new InsnList();
        LabelNode go = new LabelNode();
        head.add(new VarInsnNode(Opcodes.ALOAD, 0));
        head.add(new VarInsnNode(Opcodes.ALOAD, 1));
        head.add(new MethodInsnNode(Opcodes.INVOKESTATIC, REGIONS, "nativeEvent", "(" + OBJ + OBJ + ")Z", false));
        head.add(new JumpInsnNode(Opcodes.IFEQ, go));
        head.add(new InsnNode(Opcodes.RETURN));
        head.add(go);
        accept.instructions.insert(head);
    }
}
