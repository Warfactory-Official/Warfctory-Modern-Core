package com.norwood.wfcore.tessellate.asm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Validate-then-mutate helpers for {@code postApply} edits: every lookup throws {@link IllegalStateException} on shape
 * mismatch, and nothing is mutated until every expected site has been found. Frames: Mixin writes COMPUTE_FRAMES.
 */
public final class ClassEdits {

    private ClassEdits() {
    }

    public static FieldNode field(ClassNode node, String name, String desc) {
        return node.fields.stream().filter(f -> f.name.equals(name) && f.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + name + desc + " missing"));
    }

    public static MethodNode method(ClassNode node, String name, String desc) {
        return node.methods.stream().filter(m -> m.name.equals(name) && m.desc.equals(desc)).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + name + desc + " missing"));
    }

    public static void setVolatile(ClassNode node, String name, String desc) {
        FieldNode f = field(node, name, desc);
        if ((f.access & Opcodes.ACC_VOLATILE) != 0) {
            throw new IllegalStateException("already patched: " + node.name + "." + name);
        }
        f.access |= Opcodes.ACC_VOLATILE;
    }

    /** Volatile for every named field; validated first. */
    public static void setVolatile(ClassNode node, String[]... nameDesc) {
        for (String[] nd : nameDesc) {
            if ((field(node, nd[0], nd[1]).access & Opcodes.ACC_VOLATILE) != 0) {
                throw new IllegalStateException("already patched: " + node.name + "." + nd[0]);
            }
        }
        for (String[] nd : nameDesc) {
            field(node, nd[0], nd[1]).access |= Opcodes.ACC_VOLATILE;
        }
    }

    /**
     * {@code new from(); ... PUT field} => {@code new to()} in every method named {@code method} (any descriptor).
     * Exactly {@code expected} sites or throws. {@code to} must be assignable to the field type.
     */
    public static void swapNewFeedingField(ClassNode node, String method, String field, String from, String to, int expected) {
        List<AbstractInsnNode[]> sites = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if (!m.name.equals(method)) {
                continue;
            }
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof FieldInsnNode put && (put.getOpcode() == Opcodes.PUTFIELD || put.getOpcode() == Opcodes.PUTSTATIC)
                        && put.name.equals(field) && put.owner.equals(node.name)) {
                    sites.add(newSite(put, from));
                }
            }
        }
        if (sites.size() != expected) {
            throw new IllegalStateException(node.name + "." + method + ": expected " + expected + " `new " + from + "` feeding "
                    + field + ", found " + sites.size());
        }
        sites.forEach(s -> retarget(s, to));
    }

    /** {@code new from()} => {@code new to()} for every no-arg instantiation in {@code method}; exactly {@code expected}. */
    public static void swapNewInMethod(ClassNode node, String method, String desc, String from, String to, int expected) {
        MethodNode m = method(node, method, desc);
        List<AbstractInsnNode[]> sites = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode call && call.getOpcode() == Opcodes.INVOKESPECIAL && call.owner.equals(from)
                    && call.name.equals("<init>") && call.desc.equals("()V")) {
                sites.add(newSiteEndingAt(call, from));
            }
        }
        if (sites.size() != expected) {
            throw new IllegalStateException(node.name + "." + method + ": expected " + expected + " `new " + from + "()`, found " + sites.size());
        }
        sites.forEach(s -> retarget(s, to));
    }

    private static AbstractInsnNode[] newSite(FieldInsnNode put, String from) {
        AbstractInsnNode init = realPrevious(put);
        if (!(init instanceof MethodInsnNode call) || call.getOpcode() != Opcodes.INVOKESPECIAL || !call.owner.equals(from)
                || !call.name.equals("<init>") || !call.desc.equals("()V")) {
            throw new IllegalStateException("field " + put.name + " is not fed by `new " + from + "()`");
        }
        return newSiteEndingAt(call, from);
    }

    private static AbstractInsnNode[] newSiteEndingAt(MethodInsnNode init, String from) {
        AbstractInsnNode dup = realPrevious(init);
        AbstractInsnNode neu = dup == null ? null : realPrevious(dup);
        if (dup == null || dup.getOpcode() != Opcodes.DUP || !(neu instanceof TypeInsnNode t) || t.getOpcode() != Opcodes.NEW
                || !t.desc.equals(from)) {
            throw new IllegalStateException("unexpected instantiation shape of " + from);
        }
        return new AbstractInsnNode[]{neu, init};
    }

    private static void retarget(AbstractInsnNode[] site, String to) {
        ((TypeInsnNode) site[0]).desc = to;
        ((MethodInsnNode) site[1]).owner = to;
    }

    public static AbstractInsnNode realPrevious(AbstractInsnNode i) {
        AbstractInsnNode p = i.getPrevious();
        while (p != null && p.getOpcode() < 0) {
            p = p.getPrevious();
        }
        return p;
    }

    public static AbstractInsnNode realNext(AbstractInsnNode i) {
        AbstractInsnNode n = i.getNext();
        while (n != null && n.getOpcode() < 0) {
            n = n.getNext();
        }
        return n;
    }

    /**
     * Static {@code field} written by {@code method} (and its {@code lambda$method$*}) is built in a private twin and
     * published to {@code field} before every return of {@code method}; readers elsewhere never see a partial map.
     */
    public static void buildThenPublish(ClassNode node, String method, String field, String desc) {
        field(node, field, desc);
        MethodNode m = node.methods.stream().filter(x -> x.name.equals(method)).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + method + " missing"));
        List<MethodNode> scope = new ArrayList<>();
        scope.add(m);
        node.methods.stream().filter(x -> x.name.startsWith("lambda$" + method + "$")).forEach(scope::add);
        AbstractInsnNode first = null;
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof FieldInsnNode f && f.name.equals(field) && f.owner.equals(node.name)
                    && (f.getOpcode() == Opcodes.PUTSTATIC || f.getOpcode() == Opcodes.GETSTATIC)) {
                first = i;
                break;
            }
        }
        if (first == null || first.getOpcode() != Opcodes.PUTSTATIC) {
            throw new IllegalStateException(node.name + "." + method + ": " + field + " must be assigned before any read");
        }
        String twin = field + "$build";
        if (node.fields.stream().anyMatch(f -> f.name.equals(twin))) {
            throw new IllegalStateException("already patched: " + node.name + "." + field);
        }
        node.fields.add(new FieldNode(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC, twin, desc, null, null));
        for (MethodNode s : scope) {
            for (AbstractInsnNode i : s.instructions) {
                if (i instanceof FieldInsnNode f && f.name.equals(field) && f.owner.equals(node.name)) {
                    f.name = twin;
                }
            }
        }
        List<AbstractInsnNode> returns = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i.getOpcode() == Opcodes.RETURN) {
                returns.add(i);
            }
        }
        if (returns.isEmpty()) {
            throw new IllegalStateException(node.name + "." + method + " has no return");
        }
        for (AbstractInsnNode r : returns) {
            m.instructions.insertBefore(r, new FieldInsnNode(Opcodes.GETSTATIC, node.name, twin, desc));
            m.instructions.insertBefore(r, new FieldInsnNode(Opcodes.PUTSTATIC, node.name, field, desc));
        }
    }
}
