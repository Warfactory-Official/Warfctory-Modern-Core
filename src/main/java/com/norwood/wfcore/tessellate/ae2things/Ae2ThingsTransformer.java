package com.norwood.wfcore.tessellate.ae2things;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;

import java.util.List;
import java.util.Map;

/**
 * Delivers the flag edits of {@code patches/ae2things/0001} ({@code volatile}/{@code synchronized}/{@code final} are not
 * mixin-expressible). Applied from {@code TessellateMixinPlugin.postApply}, after every mixin on the target; validates
 * before mutating, mismatch or already applied => throws.
 */
public final class Ae2ThingsTransformer {

    public static final String MOD = "io.github.projectet.ae2things.AE2Things";
    public static final String MANAGER = "io.github.projectet.ae2things.util.StorageManager";
    public static final String DATA = "io.github.projectet.ae2things.util.DataStorage";

    private static final String UUID = "Ljava/util/UUID;";
    private static final String PROVIDER = "Lnet/minecraft/core/HolderLookup$Provider;";

    private static final Map<String, List<String[]>> VOLATILE = Map.of(
            MOD, List.of(
                    new String[]{"storageManager", "L" + MANAGER.replace('.', '/') + ";"},
                    new String[]{"storageManagerServer", "Lnet/minecraft/server/MinecraftServer;"}),
            MANAGER, List.<String[]>of(new String[]{"registries", "Ljava/lang/ref/WeakReference;"}));

    private static final List<String[]> SYNCHRONIZED = List.of(
            new String[]{"save", "(Lnet/minecraft/nbt/CompoundTag;" + PROVIDER + ")Lnet/minecraft/nbt/CompoundTag;"},
            new String[]{"updateDisk", "(" + UUID + "L" + DATA.replace('.', '/') + ";)V"},
            new String[]{"removeDisk", "(" + UUID + ")V"},
            new String[]{"hasUUID", "(" + UUID + ")Z"},
            new String[]{"getOrCreateDisk", "(" + UUID + ")L" + DATA.replace('.', '/') + ";"},
            new String[]{"modifyDisk", "(" + UUID + "Lnet/minecraft/nbt/ListTag;[JJ)V"});

    private static final List<String[]> FINAL = List.of(
            new String[]{"stackKeys", "Lnet/minecraft/nbt/ListTag;"},
            new String[]{"stackAmounts", "[J"},
            new String[]{"itemCount", "J"});

    private Ae2ThingsTransformer() {
    }

    public static boolean handles(String className) {
        return className.equals(MOD) || className.equals(MANAGER) || className.equals(DATA);
    }

    public static void apply(String className, ClassNode node) {
        switch (className) {
            case MOD -> flag(node, VOLATILE.get(MOD), List.of(), List.of());
            case MANAGER -> flag(node, VOLATILE.get(MANAGER), SYNCHRONIZED, List.of());
            case DATA -> {
                flag(node, List.of(), List.of(), FINAL);
                assignedOnlyInConstructors(node);
            }
            default -> throw new IllegalArgumentException(className);
        }
    }

    private static void flag(ClassNode node, List<String[]> volatiles, List<String[]> synchronizeds, List<String[]> finals) {
        List<FieldNode> volatileFields = volatiles.stream().map(f -> field(node, f)).toList();
        List<FieldNode> finalFields = finals.stream().map(f -> field(node, f)).toList();
        List<MethodNode> methods = synchronizeds.stream().map(m -> method(node, m)).toList();
        for (FieldNode f : volatileFields) {
            if ((f.access & Opcodes.ACC_VOLATILE) != 0) {
                throw new IllegalStateException("already patched: " + node.name + "." + f.name);
            }
        }
        for (FieldNode f : finalFields) {
            if ((f.access & Opcodes.ACC_FINAL) != 0) {
                throw new IllegalStateException("already patched: " + node.name + "." + f.name);
            }
        }
        for (MethodNode m : methods) {
            if ((m.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                throw new IllegalStateException("already patched: " + node.name + "." + m.name);
            }
        }
        volatileFields.forEach(f -> f.access |= Opcodes.ACC_VOLATILE);
        finalFields.forEach(f -> f.access |= Opcodes.ACC_FINAL);
        methods.forEach(m -> m.access |= Opcodes.ACC_SYNCHRONIZED);
    }

    /** A {@code final} field written outside {@code <init>} of its own class fails at link time; refuse before editing. */
    private static void assignedOnlyInConstructors(ClassNode node) {
        for (MethodNode m : node.methods) {
            if (m.name.equals("<init>")) {
                continue;
            }
            for (var insn : m.instructions) {
                if (insn instanceof org.objectweb.asm.tree.FieldInsnNode f && f.getOpcode() == Opcodes.PUTFIELD
                        && f.owner.equals(node.name)) {
                    throw new IllegalStateException(node.name + "." + m.name + " writes " + f.name);
                }
            }
        }
    }

    private static FieldNode field(ClassNode node, String[] nameDesc) {
        return node.fields.stream().filter(f -> f.name.equals(nameDesc[0]) && f.desc.equals(nameDesc[1])).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + nameDesc[0] + nameDesc[1] + " missing"));
    }

    private static MethodNode method(ClassNode node, String[] nameDesc) {
        return node.methods.stream().filter(m -> m.name.equals(nameDesc[0]) && m.desc.equals(nameDesc[1])).findFirst()
                .orElseThrow(() -> new IllegalStateException(node.name + "." + nameDesc[0] + nameDesc[1] + " missing"));
    }
}
