package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.objects.KuimiArrays;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class KuimiClass extends KuimiObject<Class<?>> {
    private static final int CLASS_STATE_NOT_INITIALIZED = 0;
    private static final int CLASS_STATE_CALLING_CLINIT = -1;
    private static final int CLASS_STATE_INITIALIZED = 1;
    private static final int CLASS_STATE_ERROR = -2;

    private final KuimiVM vm;
    private final Type classType;
    private final KuimiObject<?> classLoader;
    private final KuimiClass parent;
    private final List<KuimiClass> itfs;
    private final int modifiers;


    private final KuimiMethodTable methodTable;
    private final KuimiFieldTable fieldTable;

    private volatile int classState = CLASS_STATE_NOT_INITIALIZED;


    public KuimiClass(
            KuimiVM vm, Type classType,
            int modifiers,
            KuimiObject<?> classLoader,
            KuimiClass parentClass,
            List<KuimiClass> interfaces
    ) {
        Objects.requireNonNull(vm, "vm");
        Objects.requireNonNull(classType, "classType");

        if ((modifiers & Opcodes.ACC_INTERFACE) != 0) {
            modifiers |= Opcodes.ACC_ABSTRACT;
        }

        this.modifiers = modifiers;
        this.vm = vm;
        this.classType = classType; // fixme check name
        this.classLoader = classLoader;
        this.parent = parentClass;
        this.itfs = interfaces == null ? Collections.emptyList() : interfaces;

        this.methodTable = new KuimiMethodTable(this);
        this.fieldTable = new KuimiFieldTable(this);
    }

    @Override
    public KuimiVM getVm() {
        return vm;
    }

    @Override
    public KuimiClass getObjectClass() {
        return vm.getClassClass();
    }

    public KuimiObject<?> getClassLoader() {
        return classLoader;
    }

    /**
     * Internal name
     */
    public String getTypeName() {
        return classType.getClassName();
    }

    public Type getClassType() {
        return classType;
    }

    public KuimiClass getSuperClass() {
        return parent;
    }

    public List<KuimiClass> getInterfaces() {
        return itfs;
    }

    public int getModifiers() {
        return modifiers;
    }

    public KuimiMethodTable getMethodTable() {
        return methodTable;
    }

    public KuimiFieldTable getFieldTable() {
        return fieldTable;
    }

    public boolean isPrimitive() {
        var sort = classType.getSort();
        return sort != Type.ARRAY && sort != Type.OBJECT;
    }

    public boolean isArray() {
        return classType.getSort() == Type.ARRAY;
    }

    @Override
    public String toString() {
        if ((modifiers & Opcodes.ACC_INTERFACE) == 0) {
            return "class " + getTypeName();
        }
        return "interface " + getTypeName();
    }

    public KuimiObject<?> allocateNewObject() {
        if (this == vm.getClassClass()) throw new RuntimeException("Allocating java.lang.Class is not allowed");
        if (Modifier.isAbstract(modifiers)) throw new RuntimeException("Allocating a abstract class");
        var tsort = classType.getSort();
        if (tsort == Type.ARRAY) {
            throw new RuntimeException("Allocating a array type");
        }
        if (tsort != Type.OBJECT) {
            throw new RuntimeException("Allocating a primitive class");
        }

        ensureClassInitialized();

        return new KuimiObject<>(this);
    }

    transient KuimiArrays.ArrayClass arrayType;

    public KuimiArrays.ArrayClass arrayType() {
        if (arrayType != null) return arrayType;
        synchronized (this) {
            if (arrayType != null) return arrayType;

            return arrayType = new KuimiArrays.ArrayClass(this);
        }
    }

    public boolean isInterface() {
        return Modifier.isInterface(modifiers);
    }

    public void ensureClassInitialized() {
        var cs = classState;
        if (cs == CLASS_STATE_INITIALIZED) return;
        if (cs == CLASS_STATE_ERROR) {
            throw new IllegalStateException("Exception when initialize " + this);
        }

        synchronized (this) {
            cs = classState;
            if (cs == CLASS_STATE_INITIALIZED) return;
            if (cs == CLASS_STATE_CALLING_CLINIT) return;
            if (cs == CLASS_STATE_ERROR) {
                throw new IllegalStateException("Exception when initialize " + this);
            }
            if (cs == CLASS_STATE_NOT_INITIALIZED) {
                classState = CLASS_STATE_CALLING_CLINIT;

                try {
                    if (parent != null) {
                        parent.ensureClassInitialized();
                    }

                    var clinit = methodTable.getDeclaredMethods().stream()
                            .filter(it -> Modifier.isStatic(it.getModifiers()))
                            .filter(it -> it.getReturnType().getClassType().getSort() == Type.VOID)
                            .filter(it -> it.getParameters().size() == 0)
                            .filter(it -> it.getMethodName().equals("<clinit>"))
                            .findFirst();

                    if (clinit.isPresent()) {
                        clinit.get().resolveMethodHandle().invokeExact(vm, vm.getStackTrace());
                    }

                    classState = CLASS_STATE_INITIALIZED;
                } catch (Throwable throwable) {
                    classState = CLASS_STATE_ERROR;

                    if (throwable instanceof RuntimeException) throw (RuntimeException) throwable;
                    if (throwable instanceof Error) throw (Error) throwable;
                    throw new RuntimeException(throwable);
                }
            }
        }
    }


    @Deprecated
    public static class Internal {
        public static void attachArrayType(KuimiClass thiz, KuimiArrays.ArrayClass arrType) {
            thiz.arrayType = arrType;
        }
    }
}

