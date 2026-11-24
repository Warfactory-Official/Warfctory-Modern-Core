package com.norwood.wfcore.tessellate.umc;

import com.norwood.wfcore.tessellate.asm.ClassEdits;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/** Container swaps of {@code patches/umc/0001,0003}; logic parts are mixins. */
public final class UmcTransformer {

    public static final String WORLD = "cam72cam.mod.world.World";
    public static final String ENTITY_SYNC = "cam72cam.mod.entity.sync.EntitySync";
    public static final String FLUID = "cam72cam.mod.fluid.Fluid";
    public static final String MODDED_ENTITY = "cam72cam.mod.entity.ModdedEntity";

    private static final String HASH_MAP = "java/util/HashMap";
    private static final String CHM = "java/util/concurrent/ConcurrentHashMap";
    private static final String ARRAY_LIST = "java/util/ArrayList";
    private static final String COW = "java/util/concurrent/CopyOnWriteArrayList";

    private UmcTransformer() {
    }

    public static void apply(String className, ClassNode node) {
        switch (className) {
            case WORLD -> {
                ClassEdits.swapNewFeedingField(node, "<init>", "entityByID", HASH_MAP, CHM, 1);
                ClassEdits.swapNewFeedingField(node, "<init>", "entityByUUID", HASH_MAP, CHM, 1);
                ClassEdits.swapNewFeedingField(node, "<init>", "entitiesByClass", HASH_MAP, CHM, 1);
                ClassEdits.swapNewFeedingField(node, "<clinit>", "clientWorlds", HASH_MAP, CHM, 1);
                ClassEdits.swapNewFeedingField(node, "<clinit>", "serverWorlds", HASH_MAP, CHM, 1);
                ClassEdits.swapNewInMethod(node, "onEntityAdded", "(Lnet/minecraft/world/entity/Entity;)V", ARRAY_LIST, COW, 1);
            }
            case ENTITY_SYNC -> ClassEdits.swapNewFeedingField(node, "<clinit>", "precisionMapper", HASH_MAP, CHM, 1);
            case FLUID -> {
                ClassEdits.swapNewFeedingField(node, "<clinit>", "registryCache", HASH_MAP, CHM, 1);
                MethodNode get = ClassEdits.method(node, "getFluid", "(Ljava/lang/String;)Lcam72cam/mod/fluid/Fluid;");
                if ((get.access & Opcodes.ACC_SYNCHRONIZED) != 0) {
                    throw new IllegalStateException("already patched: " + node.name + ".getFluid");
                }
                get.access |= Opcodes.ACC_SYNCHRONIZED;
            }
            case MODDED_ENTITY -> moddedEntity(node);
            default -> throw new IllegalArgumentException(className);
        }
    }

    public static boolean handles(String className) {
        return switch (className) {
            case WORLD, ENTITY_SYNC, FLUID, MODDED_ENTITY -> true;
            default -> false;
        };
    }

    private static void moddedEntity(ClassNode node) {
        ClassEdits.swapNewFeedingField(node, "<init>", "deferredTasks", ARRAY_LIST, COW, 1);
        MethodNode tick = ClassEdits.method(node, "tick", "()V");
        MethodInsnNode forEach = null;
        MethodInsnNode clear = null;
        for (AbstractInsnNode i : tick.instructions) {
            if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("deferredTasks")) {
                AbstractInsnNode indy = ClassEdits.realNext(f);
                AbstractInsnNode call = indy == null ? null : ClassEdits.realNext(indy);
                if (indy != null && indy.getOpcode() == Opcodes.INVOKEDYNAMIC && call instanceof MethodInsnNode m
                        && m.name.equals("forEach") && m.owner.equals("java/util/List")) {
                    forEach = m;
                } else if (indy instanceof MethodInsnNode m && m.name.equals("clear") && m.owner.equals("java/util/List")) {
                    clear = m;
                }
            }
        }
        if (forEach == null || clear == null) {
            throw new IllegalStateException("ModdedEntity.tick: deferredTasks.forEach/clear not found");
        }
        AbstractInsnNode indy = forEach.getPrevious();
        while (indy.getOpcode() < 0) {
            indy = indy.getPrevious();
        }
        InsnList list = tick.instructions;
        list.insertBefore(forEach, new MethodInsnNode(Opcodes.INVOKESTATIC, "com/norwood/wfcore/tessellate/umc/UmcTasks", "drain",
                "(Ljava/util/List;)V", false));
        list.remove(indy);
        list.remove(forEach);
        AbstractInsnNode getfield = clear.getPrevious();
        while (getfield.getOpcode() < 0) {
            getfield = getfield.getPrevious();
        }
        AbstractInsnNode aload = getfield.getPrevious();
        while (aload.getOpcode() < 0) {
            aload = aload.getPrevious();
        }
        list.remove(aload);
        list.remove(getfield);
        list.remove(clear);
    }
}
