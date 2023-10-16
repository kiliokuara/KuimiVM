package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.Module;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.util.ArrayList;

public class KuimiJniMethodHandle {
    private static final MethodHandle ARRAY_LIST_NEW;
    private static final MethodHandle ARRAY_LIST_ADD;
    private static final MethodHandle NUMBER_LONG_VALUE;
    private static final MethodHandle NUMBER_INT_VALUE;
    private static final MethodHandle NUMBER_SHORT_VALUE;
    private static final MethodHandle NUMBER_BYTE_VALUE;
    private static final MethodHandle FLOAT_INT_BITS_TO_FLOAT;
    private static final MethodHandle DOUBLE_LONG_BITS_TO_DOUBLE;


    private static final MethodHandle KUIMI_VM_RESOLVE_OBJECT;


    private static final MethodHandle N2C, N2B;
    private static final MethodHandle RE_INVOKE;

    static {
        var lk = MethodHandles.lookup();
        try {
            ARRAY_LIST_NEW = lk.findConstructor(ArrayList.class, MethodType.methodType(void.class));
            ARRAY_LIST_ADD = MethodHandles.dropReturn(lk.findVirtual(ArrayList.class, "add", MethodType.methodType(boolean.class, Object.class)));

            NUMBER_LONG_VALUE = lk.findVirtual(Number.class, "longValue", MethodType.methodType(long.class));
            NUMBER_INT_VALUE = lk.findVirtual(Number.class, "intValue", MethodType.methodType(int.class));
            NUMBER_SHORT_VALUE = lk.findVirtual(Number.class, "shortValue", MethodType.methodType(short.class));
            NUMBER_BYTE_VALUE = lk.findVirtual(Number.class, "byteValue", MethodType.methodType(byte.class));
            FLOAT_INT_BITS_TO_FLOAT = lk.findStatic(Float.class, "intBitsToFloat", MethodType.methodType(float.class, int.class));
            DOUBLE_LONG_BITS_TO_DOUBLE = lk.findStatic(Double.class, "longBitsToDouble", MethodType.methodType(double.class, long.class));

            KUIMI_VM_RESOLVE_OBJECT = lk.findVirtual(KuimiVM.class, "resolveObject", MethodType.methodType(KuimiObject.class, int.class));

            RE_INVOKE = lk.findStatic(KuimiJniMethodHandle.class, "reinvoke", MethodType.methodType(Number.class, KuimiUnidbgVM.class, KuimiMethod.class, long.class, ArrayList.class));
            N2C = lk.findStatic(KuimiJniMethodHandle.class, "n2c", MethodType.methodType(char.class, Number.class));
            N2B = lk.findStatic(KuimiJniMethodHandle.class, "n2b", MethodType.methodType(boolean.class, Number.class));


        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }

    private static char n2c(Number i) {
        return (char) i.intValue();
    }

    private static boolean n2b(Number i) {
        return i.intValue() != 0;
    }

    private static Number reinvoke(KuimiUnidbgVM vm, KuimiMethod method, long peer, ArrayList<?> args) {
        var st = (StackTrace) args.get(1);
        var isStatic = Modifier.isStatic(method.getModifiers());
        Object[] nativeArgs = new Object[isStatic ? args.size() : args.size() - 1];
        nativeArgs[0] = vm.getJNIEnv();
        int argIndex;
        if (isStatic) {
            nativeArgs[1] = st.pushObject(method.getDeclaredClass());
            argIndex = 2;
        } else {
            argIndex = 1;
        }

        args.remove(0); // KuimiVM
        args.remove(0); // StackTrace

        for (var arg : args) {
            if (arg instanceof Boolean bool) {
                nativeArgs[argIndex++] = bool ? VMConst.JNI_TRUE : VMConst.JNI_FALSE;
            } else if (arg instanceof KuimiObject<?> kobj) {
                nativeArgs[argIndex++] = st.pushObject(kobj);
            } else if (arg instanceof Number) {
                nativeArgs[argIndex++] = arg;
            } else {
                nativeArgs[argIndex++] = arg;
            }
        }

        var rsp = Module.emulateFunction(vm.emulator, peer, nativeArgs);
        if (st.throwable != null) return 0;
        return rsp;
    }

    public static MethodHandle invoker(
            KuimiMethod targetMethod,
            KuimiUnidbgVM vm,
            long peer
    ) {
        //(KuimiVM, StackTrace, <KuimiObject>, .....)R
        var mtType = targetMethod.flattenMethodHandleType();

        // (ArrayList, KuimiVM, ArrayList, StackTrace, .....)V
        MethodHandle emptyModel = MethodHandles.identity(ArrayList.class);
        {
            var ptList = mtType.parameterList();
            for (var i = ptList.size() - 1; i >= 0; i--) {
                emptyModel = MethodHandles.collectArguments(emptyModel, 1, ARRAY_LIST_ADD);
            }
        }

        {
            var mpx = new ArrayList<Class<?>>();
            mpx.add(ArrayList.class);
            for (var ignored : mtType.parameterList()) {
                mpx.add(Object.class);
            }

            var newType = MethodType.methodType(ArrayList.class, mpx);
            int[] slots = new int[emptyModel.type().parameterCount()];
            for (var i = 0; i < mtType.parameterCount(); i++) {
                slots[i * 2 + 2] = i + 1;
            }

            emptyModel = MethodHandles.permuteArguments(emptyModel, newType, slots);

            mpx.clear();
            mpx.add(ArrayList.class);
            mpx.addAll(mtType.parameterList());
            emptyModel = emptyModel.asType(MethodType.methodType(ArrayList.class, mpx));

            emptyModel = MethodHandles.collectArguments(emptyModel, 0, ARRAY_LIST_NEW);
        }
        // emptyModel = ArgumentCollector
        var reinvoker = MethodHandles.insertArguments(RE_INVOKE, 0, vm, targetMethod, peer);

        emptyModel = MethodHandles.filterReturnValue(emptyModel, reinvoker);

        var rspType = mtType.returnType();
        if (rspType == void.class) {
            emptyModel = MethodHandles.dropReturn(emptyModel);
        } else if (rspType == long.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_LONG_VALUE);
        } else if (rspType == int.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_INT_VALUE);
        } else if (rspType == short.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_SHORT_VALUE);
        } else if (rspType == byte.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_BYTE_VALUE);
        } else if (rspType == char.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, N2C);
        } else if (rspType == boolean.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, N2B);
        } else if (rspType == KuimiObject.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_INT_VALUE);
            emptyModel = MethodHandles.filterReturnValue(emptyModel, KUIMI_VM_RESOLVE_OBJECT.bindTo(vm.vm));
        } else if (rspType == float.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_INT_VALUE);
            emptyModel = MethodHandles.filterReturnValue(emptyModel, FLOAT_INT_BITS_TO_FLOAT);
        } else if (rspType == double.class) {
            emptyModel = MethodHandles.filterReturnValue(emptyModel, NUMBER_LONG_VALUE);
            emptyModel = MethodHandles.filterReturnValue(emptyModel, DOUBLE_LONG_BITS_TO_DOUBLE);
        } else {
            throw new IllegalArgumentException("Unknown how to convert to " + rspType);
        }

        return emptyModel;
    }
}
