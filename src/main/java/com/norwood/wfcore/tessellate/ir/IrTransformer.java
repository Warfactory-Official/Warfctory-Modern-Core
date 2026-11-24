package com.norwood.wfcore.tessellate.ir;

import com.norwood.wfcore.tessellate.asm.ClassEdits;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.LdcInsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.TypeInsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import java.util.ArrayList;
import java.util.List;

/** {@code patches/ir/0001-0004} as bytecode edits on the stock 1.11.0 classes; validate-then-mutate, mismatch => throws. */
public final class IrTransformer {

    private static final String P = "cam72cam.immersiverailroading.";
    public static final String MOVEABLE = P + "entity.EntityMoveableRollingStock";
    public static final String COUPLEABLE = P + "entity.EntityCoupleableRollingStock";
    public static final String ROLLING = P + "entity.EntityRollingStock";
    public static final String CONTROL_MAPPER = P + "entity.EntityRollingStock$ControlPositionMapper";
    public static final String RIDABLE = P + "entity.EntityRidableRollingStock";
    public static final String SIMULATION = P + "entity.physics.Simulation";
    public static final String CHRONO = P + "entity.physics.chrono.ServerChronoState";
    public static final String RAIL_BASE = P + "tile.TileRailBase";
    public static final String RAIL = P + "tile.TileRail";
    public static final String DEFINITIONS = P + "registry.DefinitionManager";

    private static final String WORLD = "cam72cam/mod/world/World";
    private static final String LIST = "Ljava/util/List;";
    private static final String UUID = "Ljava/util/UUID;";
    private static final String DIRTY = "com/norwood/wfcore/tessellate/ir/IrDirty";

    private IrTransformer() {
    }

    public static boolean handles(String className) {
        return switch (className) {
            case MOVEABLE, COUPLEABLE, ROLLING, CONTROL_MAPPER, RIDABLE, SIMULATION, CHRONO, RAIL_BASE, RAIL, DEFINITIONS -> true;
            default -> false;
        };
    }

    public static void apply(String className, ClassNode node) {
        switch (className) {
            case MOVEABLE -> {
                ClassEdits.setVolatile(node, new String[]{"positions", LIST}, new String[]{"states", LIST});
                onTickQueries(node);
            }
            case COUPLEABLE -> ClassEdits.setVolatile(node,
                    new String[]{"coupledFront", UUID}, new String[]{"frontCouplerEngaged", "Z"},
                    new String[]{"coupledBack", UUID}, new String[]{"backCouplerEngaged", "Z"},
                    new String[]{"hasElectricalPower", "Z"}, new String[]{"linkedToLocomotive", "Z"});
            case ROLLING -> ClassEdits.swapNewFeedingField(node, "<init>", "controlPositions", "java/util/HashMap",
                    "java/util/concurrent/ConcurrentHashMap", 1);
            case CONTROL_MAPPER -> concurrentDeserialisedMap(node);
            case RIDABLE -> doorReach(node);
            case SIMULATION -> simulation(node);
            case CHRONO -> {
                ClassEdits.setVolatile(node, new String[]{"tickID", "I"});
                ClassEdits.swapNewFeedingField(node, "<clinit>", "states", "java/util/HashMap",
                        "java/util/concurrent/ConcurrentHashMap", 1);
            }
            case RAIL_BASE -> railBase(node);
            case RAIL -> railDirty(node);
            case DEFINITIONS -> {
                ClassEdits.setVolatile(node, new String[]{"definitions", "Ljava/util/Map;"},
                        new String[]{"stockTags", "Lcam72cam/immersiverailroading/util/BiMultiMap;"},
                        new String[]{"tracks", "Ljava/util/Map;"});
                ClassEdits.buildThenPublish(node, "initModels", "stockTags", "Lcam72cam/immersiverailroading/util/BiMultiMap;");
                ClassEdits.buildThenPublish(node, "initModels", "definitions", "Ljava/util/Map;");
                ClassEdits.buildThenPublish(node, "initTracks", "tracks", "Ljava/util/Map;");
            }
            default -> throw new IllegalArgumentException(className);
        }
    }

    private static List<MethodInsnNode> calls(MethodNode m, String owner, String name, String desc) {
        List<MethodInsnNode> found = new ArrayList<>();
        for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode c && c.owner.equals(owner) && c.name.equals(name) && c.desc.equals(desc)) {
                found.add(c);
            }
        }
        return found;
    }

    /** {@code world.getEntities(filter, type)} => {@code getEntitiesWithinBB(bb, filter, type)}; bb = collision (this) / the captured local. */
    private static void onTickQueries(ClassNode node) {
        MethodNode onTick = ClassEdits.method(node, "onTick", "()V");
        String old = "(Ljava/util/function/Predicate;Ljava/lang/Class;)Ljava/util/List;";
        List<MethodInsnNode> sites = calls(onTick, WORLD, "getEntities", old);
        if (sites.size() != 2) {
            throw new IllegalStateException("EntityMoveableRollingStock.onTick: expected 2 getEntities(filter,type), found " + sites.size());
        }
        List<Runnable> edits = new ArrayList<>();
        for (MethodInsnNode call : sites) {
            AbstractInsnNode ldc = ClassEdits.realPrevious(call);
            AbstractInsnNode indy = ldc == null ? null : ClassEdits.realPrevious(ldc);
            if (!(ldc instanceof LdcInsnNode) || !(indy instanceof InvokeDynamicInsnNode id)) {
                throw new IllegalStateException("getEntities site shape");
            }
            AbstractInsnNode capture = ClassEdits.realPrevious(indy);
            AbstractInsnNode getWorld = capture == null ? null : ClassEdits.realPrevious(capture);
            if (!(getWorld instanceof MethodInsnNode gw) || !gw.name.equals("getWorld")) {
                throw new IllegalStateException("getEntities site: world not from getWorld()");
            }
            InsnList bb = new InsnList();
            if (id.desc.startsWith("(Lcam72cam/immersiverailroading/util/RealBB;)") && capture instanceof VarInsnNode v
                    && v.getOpcode() == Opcodes.ALOAD) {
                bb.add(new VarInsnNode(Opcodes.ALOAD, v.var));
            } else if (capture instanceof VarInsnNode v && v.var == 0) {
                bb.add(new VarInsnNode(Opcodes.ALOAD, 0));
                bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, node.name, "getCollision",
                        "()Lcam72cam/immersiverailroading/util/RealBB;", false));
            } else {
                throw new IllegalStateException("getEntities site: unknown bounding box source");
            }
            edits.add(() -> {
                onTick.instructions.insert(getWorld, bb);
                call.name = "getEntitiesWithinBB";
                call.desc = "(Lcam72cam/mod/entity/boundingbox/IBoundingBox;Ljava/util/function/Predicate;Ljava/lang/Class;)Ljava/util/List;";
            });
        }
        edits.forEach(Runnable::run);
    }

    private static void concurrentDeserialisedMap(ClassNode node) {
        List<MethodInsnNode> sites = new ArrayList<>();
        for (MethodNode m : node.methods) {
            if (!calls(m, DIRTY, "concurrent", "(Ljava/util/Map;)Ljava/util/Map;").isEmpty()) {
                throw new IllegalStateException("already patched: " + node.name);
            }
            sites.addAll(calls(m, "cam72cam/mod/serialization/TagCompound", "getMap",
                    "(Ljava/lang/String;Ljava/util/function/Function;Ljava/util/function/Function;)Ljava/util/Map;"));
        }
        if (sites.size() != 1) {
            throw new IllegalStateException("ControlPositionMapper: expected 1 TagCompound.getMap, found " + sites.size());
        }
        MethodInsnNode call = sites.get(0);
        MethodNode owner = node.methods.stream().filter(m -> m.instructions.contains(call)).findFirst().orElseThrow();
        owner.instructions.insert(call, new MethodInsnNode(Opcodes.INVOKESTATIC, DIRTY, "concurrent",
                "(Ljava/util/Map;)Ljava/util/Map;", false));
    }

    /** Whole-dimension player scan => players within the door reach box (stock length around the stock position). */
    private static void doorReach(ClassNode node) {
        MethodNode target = null;
        MethodInsnNode call = null;
        for (MethodNode m : node.methods) {
            for (MethodInsnNode c : calls(m, WORLD, "getEntities", "(Ljava/lang/Class;)Ljava/util/List;")) {
                if (call != null) {
                    throw new IllegalStateException("EntityRidableRollingStock: multiple World.getEntities(Class)");
                }
                target = m;
                call = c;
            }
        }
        if (call == null) {
            throw new IllegalStateException("EntityRidableRollingStock: World.getEntities(Class) not found");
        }
        AbstractInsnNode ldc = ClassEdits.realPrevious(call);
        if (!(ldc instanceof LdcInsnNode)) {
            throw new IllegalStateException("EntityRidableRollingStock: getEntities(Class) argument shape");
        }
        String self = node.name;
        String def = "cam72cam/immersiverailroading/registry/EntityRollingStockDefinition";
        int reach = target.maxLocals;
        int center = reach + 2;
        InsnList bb = new InsnList();
        bb.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, self, "getDefinition", "()L" + def + ";", false));
        bb.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bb.add(new FieldInsnNode(Opcodes.GETFIELD, self, "gauge", "Lcam72cam/immersiverailroading/library/Gauge;"));
        bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, def, "getLength", "(Lcam72cam/immersiverailroading/library/Gauge;)D", false));
        bb.add(new VarInsnNode(Opcodes.DSTORE, reach));
        bb.add(new VarInsnNode(Opcodes.ALOAD, 0));
        bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, self, "getPosition", "()Lcam72cam/mod/math/Vec3d;", false));
        bb.add(new VarInsnNode(Opcodes.ASTORE, center));
        bb.add(new VarInsnNode(Opcodes.ALOAD, center));
        for (int k = 0; k < 3; k++) {
            bb.add(new VarInsnNode(Opcodes.DLOAD, reach));
        }
        bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cam72cam/mod/math/Vec3d", "subtract",
                "(DDD)Lcam72cam/mod/math/Vec3d;", false));
        bb.add(new VarInsnNode(Opcodes.ALOAD, center));
        for (int k = 0; k < 3; k++) {
            bb.add(new VarInsnNode(Opcodes.DLOAD, reach));
        }
        bb.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "cam72cam/mod/math/Vec3d", "add",
                "(DDD)Lcam72cam/mod/math/Vec3d;", false));
        bb.add(new MethodInsnNode(Opcodes.INVOKESTATIC, "cam72cam/mod/entity/boundingbox/IBoundingBox", "from",
                "(Lcam72cam/mod/math/Vec3d;Lcam72cam/mod/math/Vec3d;)Lcam72cam/mod/entity/boundingbox/IBoundingBox;", true));
        target.instructions.insertBefore(ldc, bb);
        call.name = "getEntitiesWithinBB";
        call.desc = "(Lcam72cam/mod/entity/boundingbox/IBoundingBox;Ljava/lang/Class;)Ljava/util/List;";
        target.maxLocals = center + 1;
    }

    private static void simulation(ClassNode node) {
        ClassEdits.setVolatile(node, new String[]{"forceQuickUpdates", "Z"});
        MethodNode ctor = ClassEdits.method(node, "<init>", "(L" + WORLD + ";)V");
        FieldInsnNode putStates = null;
        FieldInsnNode putPositions = null;
        List<FieldInsnNode> getStates = new ArrayList<>();
        for (AbstractInsnNode i : ctor.instructions) {
            if (i instanceof FieldInsnNode f && f.name.equals("states") && f.desc.equals(LIST)) {
                if (f.getOpcode() == Opcodes.PUTFIELD) {
                    if (putStates != null) {
                        throw new IllegalStateException("Simulation.<init>: multiple states writes");
                    }
                    putStates = f;
                } else if (f.getOpcode() == Opcodes.GETFIELD) {
                    getStates.add(f);
                }
            } else if (i instanceof FieldInsnNode f && f.name.equals("positions") && f.desc.equals(LIST)
                    && f.getOpcode() == Opcodes.PUTFIELD) {
                if (putPositions != null) {
                    throw new IllegalStateException("Simulation.<init>: multiple positions writes");
                }
                putPositions = f;
            }
        }
        if (putStates == null || putPositions == null || getStates.size() != 2) {
            throw new IllegalStateException("Simulation.<init>: states/positions apply loop not found");
        }
        int stock = -1;
        for (FieldInsnNode g : getStates) {
            if (!(ClassEdits.realPrevious(g) instanceof VarInsnNode v) || v.getOpcode() != Opcodes.ALOAD) {
                throw new IllegalStateException("Simulation.<init>: states read shape");
            }
            stock = stock == -1 ? v.var : stock;
            if (stock != v.var) {
                throw new IllegalStateException("Simulation.<init>: states reads of different stock");
            }
        }
        MethodNode simulate = ClassEdits.method(node, "simulate", "(L" + WORLD + ";)V");
        TypeInsnNode neu = null;
        for (AbstractInsnNode i : simulate.instructions) {
            if (i instanceof TypeInsnNode t && t.getOpcode() == Opcodes.NEW && t.desc.equals(node.name)) {
                neu = t;
            }
        }
        if (neu == null) {
            throw new IllegalStateException("Simulation.simulate: new Simulation not found");
        }
        int next = ctor.maxLocals;
        String owner = putStates.owner;
        InsnList list = ctor.instructions;
        list.insertBefore(putStates, new VarInsnNode(Opcodes.ASTORE, next));
        list.set(putStates, new InsnNode(Opcodes.POP));
        for (FieldInsnNode g : getStates) {
            AbstractInsnNode aload = ClassEdits.realPrevious(g);
            list.remove(g);
            list.set(aload, new VarInsnNode(Opcodes.ALOAD, next));
        }
        InsnList publish = new InsnList();
        publish.add(new VarInsnNode(Opcodes.ALOAD, stock));
        publish.add(new VarInsnNode(Opcodes.ALOAD, next));
        publish.add(new FieldInsnNode(Opcodes.PUTFIELD, owner, "states", LIST));
        list.insert(putPositions, publish);
        ctor.maxLocals = next + 1;
        simulate.instructions.insertBefore(neu, new VarInsnNode(Opcodes.ALOAD, 0));
        simulate.instructions.insertBefore(neu, new MethodInsnNode(Opcodes.INVOKESTATIC, DIRTY, "apply", "(Ljava/lang/Object;)V", false));
    }

    private static void railBase(ClassNode node) {
        String type = "Lcam72cam/immersiverailroading/entity/EntityMoveableRollingStock;";
        ClassEdits.setVolatile(node, new String[]{"overhead", type});
        int captured = 0;
        for (MethodNode m : node.methods) {
            List<FieldInsnNode> reads = new ArrayList<>();
            for (AbstractInsnNode i : m.instructions) {
                if (i instanceof FieldInsnNode f && f.getOpcode() == Opcodes.GETFIELD && f.name.equals("overhead")) {
                    reads.add(f);
                }
            }
            if (reads.size() < 2) {
                continue;
            }
            int local = m.maxLocals;
            for (FieldInsnNode f : reads) {
                AbstractInsnNode aload = ClassEdits.realPrevious(f);
                if (!(aload instanceof VarInsnNode v) || v.var != 0 || v.getOpcode() != Opcodes.ALOAD) {
                    throw new IllegalStateException("TileRailBase." + m.name + ": overhead read shape");
                }
                m.instructions.remove(f);
                m.instructions.set(aload, new VarInsnNode(Opcodes.ALOAD, local));
            }
            InsnList head = new InsnList();
            head.add(new VarInsnNode(Opcodes.ALOAD, 0));
            head.add(new FieldInsnNode(Opcodes.GETFIELD, node.name, "overhead", type));
            head.add(new VarInsnNode(Opcodes.ASTORE, local));
            m.instructions.insert(head);
            m.maxLocals = local + 1;
            captured++;
        }
        if (captured != 2) {
            throw new IllegalStateException("TileRailBase: expected 2 methods reading overhead twice, found " + captured);
        }
    }

    private static void railDirty(ClassNode node) {
        MethodNode target = null;
        MethodInsnNode call = null;
        for (MethodNode m : node.methods) {
            for (MethodInsnNode c : calls(m, WORLD, "getEntities", "(Ljava/util/function/Predicate;Ljava/lang/Class;)Ljava/util/List;")) {
                if (call != null) {
                    throw new IllegalStateException("TileRail: multiple World.getEntities(filter,type)");
                }
                target = m;
                call = c;
            }
        }
        if (call == null) {
            throw new IllegalStateException("TileRail: World.getEntities(filter,type) not found");
        }
        target.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, DIRTY, "defer",
                "(Ljava/lang/Object;Ljava/util/function/Predicate;Ljava/lang/Class;)Ljava/util/List;", false));
    }
}
