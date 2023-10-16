package com.kiliokuara.kuimivm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.*;

public class KuimiMethodTable extends KuimiMemberTable {
    private final KuimiClass declaredClass;

    private final List<KuimiMethod> mergedMethods;
    private final List<KuimiMethod> declaredMethods;
    private final Map<KuimiClass, InterfaceMethodTable> interfaceMethodTableMap;

    public KuimiMethodTable(KuimiClass declaredClass) {
        this.declaredClass = declaredClass;
        mergedMethods = new ArrayList<>();
        declaredMethods = new ArrayList<>();
        interfaceMethodTableMap = new IdentityHashMap<>();
    }

    public void closeTable() {
        { // close parent & itfs' tables first
            var sup = declaredClass.getSuperClass();
            if (sup != null) sup.getMethodTable().closeTable();

            for (var itf : declaredClass.getInterfaces()) {
                itf.getMethodTable().closeTable();
            }
        }

        while (true) {
            var s = status;
            if (s > 0) {
                throw new IllegalStateException("This method table is still under editing.");
            }
            if (s == 0) {
                if (STATUS.compareAndSet((KuimiMemberTable) this, 0, -1)) break;
                continue;
            }
            return;
        }

        var pendingMethods = new ArrayList<>(declaredMethods);

        { // inherit super class methods
            var sup = declaredClass.getSuperClass();
            if (sup != null) {
                mergedMethods.addAll(sup.getMethodTable().mergedMethods);
            }
        }


        final var Helper = new Object() {
            String pkg(Type ctype) {
                var cname = ctype.getClassName();
                var idx = cname.lastIndexOf('.');
                if (idx == -1) return "";
                return cname.substring(0, idx);
            }

            @SuppressWarnings("StatementWithEmptyBody")
            KuimiMethod tryFindOverride(KuimiMethod req) {
                for (var met : mergedMethods) {
                    var modifiers = met.getModifiers();

                    if (Modifier.isPrivate(modifiers)) continue;
                    if (Modifier.isStatic(modifiers)) continue;

                    // modifier check
                    if (Modifier.isPublic(modifiers)) {
                        // noop, ok
                    } else if (Modifier.isProtected(modifiers)) {
                        // noop, ok
                    } else { // package private
                        if (!Objects.equals(
                                declaredClass.getClassLoader(),
                                met.getDeclaredClass().getClassLoader()
                        )) { // class loader not match, skip
                            continue;
                        }

                        if (!Objects.equals(
                                pkg(declaredClass.getClassType()),
                                pkg(met.getDeclaredClass().getClassType())
                        )) { // package not match, skip
                            continue;
                        }
                    }


                    if (!isSignMatch(req, met)) continue;

                    return met;
                }
                return null;
            }

            boolean isSignMatch(KuimiMethod req, KuimiMethod met) {
                if (!req.getReturnType().equals(met.getReturnType())) return false;
                if (!req.getParameters().equals(met.getParameters())) return false;
                if (!req.getMethodName().equals(met.getMethodName())) return false;

                return true;
            }

            KuimiMethod findPublic(KuimiMethod req) {
                for (var met : mergedMethods) {
                    var mod = met.getModifiers();
                    if (Modifier.isStatic(mod)) continue;
                    if (!Modifier.isPublic(mod)) continue;

                    if (!isSignMatch(req, met)) continue;

                    return met;
                }
                return null;
            }
        };

        { // step 1. Direct inherit static/private/<init> methods
            pendingMethods.removeIf(it -> {

                check:
                {
                    var mod = it.getModifiers();
                    if (Modifier.isStatic(mod)) break check;
                    if (Modifier.isPrivate(mod)) break check;

                    if ("<init>".equals(it.getMethodName())) break check;

                    return false;
                }

                it.methodSlot = mergedMethods.size();
                mergedMethods.add(it);
                return true;
            });
        }

        { // step 2. try override methods
            pendingMethods.removeIf(it -> {
                var theOverride = Helper.tryFindOverride(it);
                if (theOverride != null) {
                    if (Modifier.isFinal(theOverride.getModifiers())) {
                        throw new IllegalStateException("Method " + it + " trying overriding a final method: " + theOverride);
                    }

                    it.methodSlot = theOverride.methodSlot;
                    mergedMethods.set(theOverride.methodSlot, it);
                    return true;
                }
                return false;
            });
        }

        { // step 3. merge pending methods
            pendingMethods.removeIf(it -> {
                it.methodSlot = mergedMethods.size();
                mergedMethods.add(it);

                return true;
            });
        }

        // step 4. merge interfaces
        if (!declaredClass.getInterfaces().isEmpty()) {
            for (var itfx : declaredClass.getInterfaces()) {
                pendingMethods.addAll(itfx.getMethodTable().getMergedMethods());
            }

            // drop static/private methods (not used in itf table)
            pendingMethods.removeIf(it -> Modifier.isStatic(it.getModifiers()) || Modifier.isPrivate(it.getModifiers()));

            // drop already exists methods
            pendingMethods.removeIf(it -> Helper.findPublic(it) != null);

            { // drop empty method if possible
                var mirror = new ArrayList<>(pendingMethods);
                pendingMethods.removeIf(it -> {
                    if ((it.getModifiers() & Opcodes.ACC_ABSTRACT) != 0) {

                        for (var met : mirror) {
                            if ((it.getModifiers() & Opcodes.ACC_ABSTRACT) != 0) continue;
                            if (!Helper.isSignMatch(it, met)) continue;

                            return true;
                        }

                    }
                    return false;
                });
            }

            { // drop duplicated empty method
                topScan:
                while (true) {
                    //var mirror = new ArrayList<>(pendingMethods);

                    for (var iterator = pendingMethods.iterator(); iterator.hasNext(); ) {
                        var it = iterator.next();

                        if ((it.getModifiers() & Opcodes.ACC_ABSTRACT) == 0) continue;


                        for (var met : pendingMethods) {
                            if (met == it) continue;

                            if ((met.getModifiers() & Opcodes.ACC_ABSTRACT) == 0) continue;
                            if (!Helper.isSignMatch(it, met)) continue;

                            iterator.remove();
                            continue topScan;
                        }

                    }

                    break;
                }
            }

            { // check duplicated method not exists
                for (var it : pendingMethods) {
                    for (var met : pendingMethods) {
                        if (met == it) continue;

                        check:
                        {
                            if (Helper.isSignMatch(met, it)) break check;

                            continue;
                        }

                        throw new IllegalStateException("Duplicated interface define method found: " + met + ", " + it);
                    }
                }
            }


            for (var met : pendingMethods) {
                var copied = new KuimiMethod.Internal.DelegatedMethod(met, declaredClass);
                copied.methodSlot = mergedMethods.size();
                mergedMethods.add(copied);
            }

        }

        // step 5. recalculate interface method table
        if (declaredClass.getInterfaces().isEmpty() && (declaredClass.getModifiers() & Opcodes.ACC_INTERFACE) == 0) {

            var sup = declaredClass.getSuperClass();
            if (sup != null) {
                interfaceMethodTableMap.putAll(sup.getMethodTable().interfaceMethodTableMap);
            }
        } else {
            var allItfs = new HashSet<KuimiClass>();
            {
                var sup = declaredClass.getSuperClass();
                if (sup != null) {
                    allItfs.addAll(sup.getMethodTable().interfaceMethodTableMap.keySet());
                }
            }
            allItfs.addAll(declaredClass.getInterfaces());
            for (var itfx : declaredClass.getInterfaces()) {
                allItfs.addAll(itfx.getMethodTable().interfaceMethodTableMap.keySet());
            }

            if ((declaredClass.getModifiers() & Opcodes.ACC_INTERFACE) != 0) {
                allItfs.add(declaredClass);
            }

            for (var itf : allItfs) {
                var methods = itf.getMethodTable().getMergedMethods();
                var slots = new int[methods.size()];
                for (int i = 0, methodsSize = methods.size(); i < methodsSize; i++) {
                    var itfMet = methods.get(i);

                    if (Modifier.isPrivate(itfMet.getModifiers())) continue;
                    if (Modifier.isStatic(itfMet.getModifiers())) continue;

                    var mtx = Helper.findPublic(itfMet);
                    if (mtx == null) {
                        throw new IllegalStateException("Internal exception: " + itfMet + " lost. request itf: " + itf);
                    }
                    slots[i] = mtx.methodSlot;
                }

                interfaceMethodTableMap.put(itf, new InterfaceMethodTable(itf, slots));
            }
        }

    }

    public KuimiMethod addMethod(KuimiMethod kuimiMethod) {
        Objects.requireNonNull(kuimiMethod);
        if (kuimiMethod.methodSlot != -1) {
            throw new RuntimeException("Target method already attached.");
        }
        if (kuimiMethod.getDeclaredClass() != this.declaredClass) {
            throw new IllegalArgumentException("Declared class not match, table=" + declaredClass + ", method=" + kuimiMethod.getDeclaredClass());
        }
        acquireModifyLock();
        try {
            if (findDeclaredMethod(kuimiMethod) != null) {
                throw new IllegalStateException("A method with same signature was already defined.");
            }
            declaredMethods.add(kuimiMethod);

            kuimiMethod.methodSlot = -2; // mark as attached

            return kuimiMethod;
        } finally {
            releaseModifyLock();
        }
    }


    private KuimiMethod findDeclaredMethod(KuimiMethod tried) {
        for (var dm : declaredMethods) {
            if (dm.getReturnType() != tried.getReturnType()) continue;
            if (!dm.getParameters().equals(tried.getParameters())) continue;
            if (!dm.getMethodName().equals(tried.getMethodName())) continue;

            return dm;
        }

        return null;
    }


    public List<KuimiMethod> getDeclaredMethods() {
        closeTable();
        return Collections.unmodifiableList(declaredMethods);
    }

    public List<KuimiMethod> getDeclaredMethodNoClose() {
        return Collections.unmodifiableList(declaredMethods);
    }

    public List<KuimiMethod> getMergedMethods() {
        closeTable();
        return Collections.unmodifiableList(mergedMethods);
    }

    private List<KuimiMethod> loopedMethods(String name) {
        if (name.equals("<clinit>") || name.equals("<init>")) return getDeclaredMethods();
        return getMergedMethods();
    }

    public KuimiMethod resolveMethod(String name, KuimiClass retType, List<KuimiClass> params, boolean isStatic) {
        var list = loopedMethods(name);
        for (var iterator = list.listIterator(list.size()); iterator.hasPrevious(); ) {
            var met = iterator.previous();

            if (Modifier.isStatic(met.getModifiers()) != isStatic) continue;
            if (!retType.equals(met.getReturnType())) continue;
            if (!params.equals(met.getParameters())) continue;
            if (!name.equals(met.getMethodName())) continue;
            return met;
        }
        return null;
    }

    public KuimiMethod resolveMethod(String name, String desc, boolean isStatic) {
        var retType = declaredClass.getVm().resolveClass(Type.getReturnType(desc));
        var parmas = Arrays.stream(Type.getArgumentTypes(desc)).map(it -> declaredClass.getVm().resolveClass(it)).toList();
        return resolveMethod(name, retType, parmas, isStatic);
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("MethodTable[").append(declaredClass).append(", status=").append(status).append('\n');

        sb.append("|= declared:\n");
        for (var dec : declaredMethods) {
            sb.append("| S[").append(dec.methodSlot).append("] ").append(dec).append('\n');
        }
        sb.append("|= merged:\n");
        for (var dec : mergedMethods) {
            sb.append("| S[").append(dec.methodSlot).append("] ").append(dec).append('\n');
        }
        sb.append("|= itfs:\n");
        for (var itfs : interfaceMethodTableMap.entrySet()) {
            sb.append("| |- ").append(itfs.getKey().getTypeName()).append("\n");

            var mtTable = itfs.getValue().itfType.getMethodTable().mergedMethods;
            var slotMap = itfs.getValue().slotMap;
            var slotMapSize = slotMap.length;


            for (int i = 0, mtTableSize = mtTable.size(); i < mtTableSize; i++) {
                var met = mtTable.get(i);

                if (Modifier.isStatic(met.getModifiers())) continue;
                if (Modifier.isPrivate(met.getModifiers())) continue;

                sb.append("| |  `- S[").append(i).append("->");
                if (i < slotMapSize) {
                    sb.append(slotMap[i]).append("\t] ").append(met);
                    sb.append("\t\t-->  ").append(mergedMethods.get(slotMap[i]));
                } else {
                    sb.append("??\t] ").append(met);
                }
                sb.append('\n');
            }

        }


        sb.append(']');
        return sb.toString();
    }

    public Map<KuimiClass, InterfaceMethodTable> getInterfaceMethodTableMap() {
        return Collections.unmodifiableMap(interfaceMethodTableMap);
    }

    public KuimiMethod resolveMethod(KuimiMethod method) {
        var mod = method.getModifiers();
        if (Modifier.isPrivate(mod)) return method;
        if (Modifier.isStatic(mod)) return method;
        if (method.isInterfaceMethod()) {
            try {
                var attached = method.getAttachedClass();
                if (!attached.isInterface()) {
                    // attached to parent class / current class
                    return method;
                }
                var remapped = interfaceMethodTableMap.get(attached);
                return getMergedMethods().get(remapped.slotMap[method.getMethodSlot()]);
            } catch (Throwable throwable) {
                throw new RuntimeException("Exception when resolving " + method + "<" + method.methodSlot + ">{" + method.getClass() + "} by " + this + ", \n" + method.getAttachedClass().getMethodTable());
            }
        }
        return getMergedMethods().get(method.getMethodSlot());
    }

    public static class InterfaceMethodTable {
        private final KuimiClass itfType;
        private final int[] slotMap;

        public InterfaceMethodTable(KuimiClass itfType, int[] slotMap) {
            this.itfType = itfType;
            this.slotMap = slotMap;
        }

        @Override
        public String toString() {
            var sb = new StringBuilder();
            sb.append("InterfaceMethodTable[").append(itfType.getTypeName()).append('\n');

            var mtTable = itfType.getMethodTable().getMergedMethods();

            var slotMapLocal = slotMap;
            var slotMapSize = slotMapLocal.length;

            for (int i = 0, mtTableSize = mtTable.size(); i < mtTableSize; i++) {
                var met = mtTable.get(i);

                if (Modifier.isStatic(met.getModifiers())) continue;
                if (Modifier.isPrivate(met.getModifiers())) continue;

                sb.append("| S[").append(i).append("->");
                if (i < slotMapSize) {
                    sb.append(slotMapLocal[i]);
                } else {
                    sb.append("??");
                }
                sb.append("\t] ").append(met);
                sb.append('\n');
            }

            sb.append(']');
            return sb.toString();
        }
    }
}
