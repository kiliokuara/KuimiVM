package com.kiliokuara.kuimivm.vaarg;

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.utils.DynamicClassAllocator;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface KuimiMethodInvoker {
    static KuimiMethodInvoker invoker(KuimiClass ret, List<KuimiClass> params) {
        return KuimiMethodInvokerHolder.invoker(ret, params);
    }

    int intInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);

    float floatInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);

    double doubleInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);

    long longInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);

    Object anyInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);

    void voidInvoke(KuimiVM vm, StackTrace stackTrace, KuimiMethod method, VaArg vaArg);
}

@SuppressWarnings("DuplicatedCode")
class KuimiMethodInvokerHolder {
    record MetInf(KuimiClass ret, List<KuimiClass> params) {
    }

    private static final String MTDESC;

    static {
        var s = MethodType.methodType(void.class, KuimiVM.class, StackTrace.class, KuimiMethod.class, VaArg.class).toMethodDescriptorString();
        MTDESC = s.substring(0, s.length() - 1);
    }

    private static final DynamicClassAllocator ALLOCATOR = new DynamicClassAllocator(MethodHandles.lookup(), "") {
        @Override
        protected byte[] computeBytecode(String input, String classname, Object extParam) {
            var cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            cw.visit(Opcodes.V11, Opcodes.ACC_FINAL, classname, null, "java/lang/Object", new String[]{
                    KuimiMethodInvoker.class.getName().replace('.', '/')
            });


            {
                var initx = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                initx.visitVarInsn(Opcodes.ALOAD, 0);
                initx.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                initx.visitInsn(Opcodes.RETURN);
                initx.visitMaxs(0, 0);
            }

            var metInf = (MetInf) extParam;
            boolean isRetRef;
            var returnTypeShorten = ShortenType.from(metInf.ret.getClassType()).t;
            {
                var rtSort = metInf.ret.getClassType().getSort();
                isRetRef = (rtSort == Type.ARRAY || rtSort == Type.OBJECT);
            }

            MethodVisitor topVS;
            if (isRetRef) {
                topVS = cw.visitMethod(Opcodes.ACC_PUBLIC, "anyInvoke", MTDESC + "Ljava/lang/Object;", null, null);
            } else {
                topVS = cw.visitMethod(Opcodes.ACC_PUBLIC, returnTypeShorten.getClassName() + "Invoke", MTDESC + returnTypeShorten.getDescriptor(), null, null);
            }

            topVS.visitVarInsn(Opcodes.ALOAD, 3);
            topVS.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cname(KuimiMethod.class), "resolveMethodHandle", MethodType.methodType(MethodHandle.class).descriptorString(), false);

            topVS.visitVarInsn(Opcodes.ALOAD, 1);
            topVS.visitVarInsn(Opcodes.ALOAD, 2);

            List<KuimiClass> params = metInf.params;
            for (int i = 0, paramsSize = params.size(); i < paramsSize; i++) {
                var param = params.get(i);
                topVS.visitVarInsn(Opcodes.ALOAD, 4);
                topVS.visitIntInsn(Opcodes.SIPUSH, i);

                var st = ShortenType.from(param.getClassType());
                var ittx = cname(VaArg.class);
                switch (st) {
                    case INT -> topVS.visitMethodInsn(Opcodes.INVOKEINTERFACE, ittx, "getIntArg", "(I)I", true);
                    case LONG -> topVS.visitMethodInsn(Opcodes.INVOKEINTERFACE, ittx, "getLongArg", "(I)J", true);
                    case FLOAT -> topVS.visitMethodInsn(Opcodes.INVOKEINTERFACE, ittx, "getFloatArg", "(I)F", true);
                    case DOUBLE -> topVS.visitMethodInsn(Opcodes.INVOKEINTERFACE, ittx, "getDoubleArg", "(I)D", true);
                    case REF -> {
                        topVS.visitMethodInsn(Opcodes.INVOKEINTERFACE, ittx, "getIntArg", "(I)I", true);
                        topVS.visitVarInsn(Opcodes.ALOAD, 1);
                        topVS.visitInsn(Opcodes.SWAP);
                        topVS.visitMethodInsn(Opcodes.INVOKEVIRTUAL, cname(KuimiVM.class), "resolveObject", MethodType.methodType(KuimiObject.class, int.class).descriptorString(), false);
                    }
                }
                switch (param.getClassType().getSort()) {
                    case Type.BOOLEAN -> topVS.visitInsn(Opcodes.I2B);
                    case Type.SHORT -> topVS.visitInsn(Opcodes.I2S);
                    case Type.CHAR -> topVS.visitInsn(Opcodes.I2C);
                }
            }
            var objectType = Type.getType(KuimiObject.class);
            topVS.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/invoke/MethodHandle", "invokeExact",
                    Stream.concat(Stream.of(
                                            Type.getType(KuimiVM.class), Type.getType(StackTrace.class)
                                    ),
                                    metInf.params.stream().map(KuimiClass::getClassType)
                                            .map(it -> {
                                                if (it.getSort() == Type.ARRAY || it.getSort() == Type.OBJECT)
                                                    return objectType;

                                                return it;
                                            })
                            )
                            .map(Type::getDescriptor)
                            .collect(Collectors.joining("", "(", ")"))
                            + (isRetRef ? objectType.getDescriptor() : metInf.ret.getClassType().getDescriptor())
                    , false);

            topVS.visitInsn(metInf.ret.getClassType().getOpcode(Opcodes.IRETURN));
            topVS.visitMaxs(0, 0);

            if (!isRetRef) {
                var subVS = cw.visitMethod(Opcodes.ACC_PUBLIC, "anyInvoke", MTDESC + "Ljava/lang/Object;", null, null);
                subVS.visitVarInsn(Opcodes.ALOAD, 0);
                subVS.visitVarInsn(Opcodes.ALOAD, 1);
                subVS.visitVarInsn(Opcodes.ALOAD, 2);
                subVS.visitVarInsn(Opcodes.ALOAD, 3);
                subVS.visitVarInsn(Opcodes.ALOAD, 4);

                subVS.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classname, returnTypeShorten.getClassName() + "Invoke", MTDESC + returnTypeShorten.getDescriptor(), false);

                switch (returnTypeShorten.getSort()) {
                    case Type.VOID -> subVS.visitInsn(Opcodes.ACONST_NULL);
                    case Type.INT ->
                            subVS.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Integer", "valueOf", "(I)Ljava/lang/Integer;", false);
                    case Type.LONG ->
                            subVS.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Long", "valueOf", "(J)Ljava/lang/Long;", false);
                    case Type.FLOAT ->
                            subVS.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Float", "valueOf", "(F)Ljava/lang/Float;", false);
                    case Type.DOUBLE ->
                            subVS.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Double", "valueOf", "(D)Ljava/lang/Double;", false);
                }
                subVS.visitInsn(Opcodes.ARETURN);
                subVS.visitMaxs(0, 0);
            }

            if (returnTypeShorten.getSort() != Type.VOID) {
                var subVS = cw.visitMethod(Opcodes.ACC_PUBLIC, "voidInvoke", MTDESC + "V", null, null);
                subVS.visitVarInsn(Opcodes.ALOAD, 0);
                subVS.visitVarInsn(Opcodes.ALOAD, 1);
                subVS.visitVarInsn(Opcodes.ALOAD, 2);
                subVS.visitVarInsn(Opcodes.ALOAD, 3);
                subVS.visitVarInsn(Opcodes.ALOAD, 4);

                subVS.visitMethodInsn(Opcodes.INVOKEVIRTUAL, classname,
                        returnTypeShorten == ShortenType.REF_TYPE ? "anyInvoke" : returnTypeShorten.getClassName() + "Invoke",
                        MTDESC + returnTypeShorten.getDescriptor(), false
                );


                subVS.visitInsn(returnTypeShorten.getSize() == 1 ? Opcodes.POP : Opcodes.POP2);

                subVS.visitInsn(Opcodes.RETURN);
                subVS.visitMaxs(0, 0);
            }

            return cw.toByteArray();
        }

        @Override
        protected Object extArg(MethodHandles.Lookup lookup, Object extParam) {
            try {
                return lookup.findConstructor(lookup.lookupClass(), MethodType.methodType(void.class)).asType(MethodType.methodType(KuimiMethodInvoker.class));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    };

    private static String cname(Class<?> c) {
        return c.getName().replace('.', '/');
    }

    public static KuimiMethodInvoker invoker(KuimiClass ret, List<KuimiClass> params) {
        var shorten = new StringBuilder();
        for (var param : params) {
            var ts = param.getClassType().getSort();
            if (ts == Type.ARRAY || ts == Type.OBJECT) {
                shorten.append('R');
            } else {
                shorten.append(param.getClassType().getDescriptor());
            }
        }
        shorten.append('_');
        var rtx = ret.getClassType().getSort();
        if (rtx == Type.ARRAY || rtx == Type.OBJECT) {
            shorten.append('R');
        } else {
            shorten.append(ret.getClassType().getDescriptor());
        }

        try {
            return (KuimiMethodInvoker) ((MethodHandle) ALLOCATOR.gExtArg(shorten.toString(), new MetInf(ret, params))).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}
