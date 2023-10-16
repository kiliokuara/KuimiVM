package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.vaarg.ShortenType;
import com.kiliokuara.kuimivm.vaarg.VaArgModelBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodType;
import java.util.Arrays;
import java.util.List;

public class MethodTest extends KuimiTestBase {
    private KuimiMethod newEmptyMethod(Type ret, Type... args) {
        return new KuimiMethod(vm.getBaseClass(), Opcodes.ACC_STATIC, "test",
                resolveClass(ret),
                Arrays.stream(args).map(this::resolveClass).toList()
        );
    }

    @Test
    void testMethodHandleTypeResolve() {
        Assertions.assertEquals(
                MethodType.methodType(void.class, KuimiVM.class, StackTrace.class),
                newEmptyMethod(Type.VOID_TYPE).flattenMethodHandleType()
        );


        Assertions.assertEquals(
                MethodType.methodType(int.class, KuimiVM.class, StackTrace.class, long.class, float.class, short.class, KuimiObject.class),
                newEmptyMethod(Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.SHORT_TYPE, Type.getObjectType("java/lang/Object")).flattenMethodHandleType()
        );
    }

    @Test
    void testMethodHandleResolve() throws Throwable {
        var mh = newEmptyMethod(Type.VOID_TYPE).resolveMethodHandle();
        System.out.println(mh);
        Assertions.assertThrows(InternalError.class, () -> {
            mh.invokeExact(vm, (StackTrace) null);
        }).printStackTrace(System.out);


        var mh2 = new KuimiMethod(
                vm.getBaseClass(), 0, "test",
                resolveClass(Type.VOID_TYPE),
                List.of()
        ) {
            private void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz) {
                new Throwable("OOOOOOO: " + thiz).printStackTrace(System.out);
            }
        }.resolveMethodHandle();
        System.out.println(mh2);

        mh2.invokeExact(vm, (StackTrace) null, (KuimiObject<?>) vm.getClassClass());
    }

    @Test
    void methodInvoker() throws Throwable {
        var mh = new KuimiMethod(
                vm.getBaseClass(), 0, "test",
                resolveClass(Type.LONG_TYPE),
                List.of(resolveClass(Type.INT_TYPE))
        ) {
            private long execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, int intx) {
                new Throwable("rspx: " + thiz + " - " + intx).printStackTrace(System.out);
                return 114L;
            }
        };
        vm.attachThread(new StackTrace(1024, 1024, 1024));

        var invoker = mh.getInvoker();
        System.out.println(invoker);

        var varsp = VaArgModelBuilder.builder(ShortenType.parse("II"))
                .putInt(vm.getStackTrace().pushObject(vm.getBaseClass()))
                .putInt(2048)
                .build();

        System.out.println(varsp);
        invoker.voidInvoke(vm, vm.getStackTrace(), mh, varsp);
    }
}
