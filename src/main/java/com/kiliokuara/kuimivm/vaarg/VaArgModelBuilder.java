package com.kiliokuara.kuimivm.vaarg;

import com.kiliokuara.kuimivm.utils.DynamicClassAllocator;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Arrays;

public interface VaArgModelBuilder {
    VaArgModelBuilder putInt(int v);

    VaArgModelBuilder putLong(long v);

    VaArgModelBuilder putFloat(float v);

    VaArgModelBuilder putDouble(double v);

    VaArg build();

    static VaArgModelBuilder builder(Iterable<ShortenType> type) {
        return VaArgModelBuilderHolder.builder(type);
    }
}

@SuppressWarnings("DuplicatedCode")
class VaArgModelBuilderHolder {
    private static final DynamicClassAllocator ALLOCATOR = new DynamicClassAllocator(MethodHandles.lookup(), "ds") {
        @Override
        protected byte[] computeBytecode(String input, String classname, Object extParam) {
            var ccppx = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
            var cvkey = ShortenType.parse(input.replace('L', 'I'));

            ccppx.visit(Opcodes.V11, Opcodes.ACC_FINAL, classname, null, "java/lang/Object", new String[]{
                    VaArgModelBuilder.class.getName().replace('.', '/'),
                    VaArg.class.getName().replace('.', '/')
            });
            ccppx.visitField(Opcodes.ACC_PRIVATE, "c", "I", null, null);

            for (int i = 0, cvkeySize = cvkey.size(); i < cvkeySize; i++) {
                var f = cvkey.get(i);
                ccppx.visitField(Opcodes.ACC_PRIVATE, "f" + i, String.valueOf(f.v), null, null);
            }

            {
                var mttx = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "build", "()L" + VaArg.class.getName().replace('.', '/') + ";", null, null);
                mttx.visitVarInsn(Opcodes.ALOAD, 0);
                mttx.visitInsn(Opcodes.ARETURN);
                mttx.visitMaxs(0, 0);
            }
            {
                var initx = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
                initx.visitVarInsn(Opcodes.ALOAD, 0);
                initx.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
                initx.visitInsn(Opcodes.RETURN);
                initx.visitMaxs(0, 0);
            }

            var builderLT = "L" + VaArgModelBuilder.class.getName().replace('.', '/') + ";";

            var getI = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "getIntArg", "(I)I", null, null);
            var getL = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "getLongArg", "(I)J", null, null);
            var getF = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "getFloatArg", "(I)F", null, null);
            var getD = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "getDoubleArg", "(I)D", null, null);

            var putI = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "putInt", "(I)" + builderLT, null, null);
            var putL = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "putLong", "(J)" + builderLT, null, null);
            var putF = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "putFloat", "(F)" + builderLT, null, null);
            var putD = ccppx.visitMethod(Opcodes.ACC_PUBLIC, "putDouble", "(D)" + builderLT, null, null);


            for (var argT : new Type[]{
                    Type.INT_TYPE, Type.LONG_TYPE, Type.FLOAT_TYPE, Type.DOUBLE_TYPE,
            }) {
                MethodVisitor getX, putX;
                switch (argT.getSort()) {
                    case Type.INT -> {
                        getX = getI;
                        putX = putI;
                    }
                    case Type.LONG -> {
                        getX = getL;
                        putX = putL;
                    }
                    case Type.FLOAT -> {
                        getX = getF;
                        putX = putF;
                    }
                    case Type.DOUBLE -> {
                        getX = getD;
                        putX = putD;
                    }
                    default -> throw new AssertionError();
                }

                {
                    if (!cvkey.isEmpty()) {
                        getX.visitVarInsn(Opcodes.ILOAD, 1);
                        Label dfjmp = new Label();
                        Label[] lbbx = new Label[cvkey.size()];
                        Arrays.fill(lbbx, dfjmp);

                        for (int i = 0, cvkeySize = cvkey.size(); i < cvkeySize; i++) {
                            var pt = cvkey.get(i);
                            if (pt.t == argT) {
                                lbbx[i] = new Label();
                            }
                        }
                        getX.visitTableSwitchInsn(0, cvkey.size() - 1, dfjmp, lbbx);

                        for (int i = 0, cvkeySize = cvkey.size(); i < cvkeySize; i++) {
                            var pt = cvkey.get(i);
                            if (pt.t == argT) {

                                getX.visitLabel(lbbx[i]);
                                getX.visitVarInsn(Opcodes.ALOAD, 0);
                                getX.visitFieldInsn(Opcodes.GETFIELD, classname, "f" + i, pt.t.getDescriptor());
                                getX.visitInsn(pt.t.getOpcode(Opcodes.IRETURN));
                            }
                        }

                        getX.visitLabel(dfjmp);
                    }

                    getX.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
                    getX.visitInsn(Opcodes.DUP);
                    getX.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
                    getX.visitInsn(Opcodes.ATHROW);

                    getX.visitMaxs(0, 0);
                }

                {

                    if (!cvkey.isEmpty()) {
                        putX.visitVarInsn(Opcodes.ALOAD, 0);
                        putX.visitFieldInsn(Opcodes.GETFIELD, classname, "c", "I");

                        Label dfjmp = new Label(), increase = new Label();
                        Label[] lbbx = new Label[cvkey.size()];
                        Arrays.fill(lbbx, dfjmp);


                        for (int i = 0, cvkeySize = cvkey.size(); i < cvkeySize; i++) {
                            var pt = cvkey.get(i);
                            if (pt.t == argT) {
                                lbbx[i] = new Label();
                            }
                        }

                        putX.visitTableSwitchInsn(0, cvkey.size() - 1, dfjmp, lbbx);

                        for (int i = 0, cvkeySize = cvkey.size(); i < cvkeySize; i++) {
                            var pt = cvkey.get(i);
                            if (pt.t == argT) {
                                putX.visitLabel(lbbx[i]);
                                putX.visitVarInsn(Opcodes.ALOAD, 0);
                                putX.visitVarInsn(pt.t.getOpcode(Opcodes.ILOAD), 1);
                                putX.visitFieldInsn(Opcodes.PUTFIELD, classname, "f" + i, pt.t.getDescriptor());
                                putX.visitJumpInsn(Opcodes.GOTO, increase);
                            }
                        }


                        putX.visitLabel(increase);
                        putX.visitVarInsn(Opcodes.ALOAD, 0);
                        putX.visitInsn(Opcodes.DUP);
                        putX.visitInsn(Opcodes.DUP);
                        putX.visitFieldInsn(Opcodes.GETFIELD, classname, "c", "I");
                        putX.visitInsn(Opcodes.ICONST_1);
                        putX.visitInsn(Opcodes.IADD);
                        putX.visitFieldInsn(Opcodes.PUTFIELD, classname, "c", "I");
                        putX.visitInsn(Opcodes.ARETURN);

                        putX.visitLabel(dfjmp);
                    }

                    putX.visitTypeInsn(Opcodes.NEW, "java/lang/RuntimeException");
                    putX.visitInsn(Opcodes.DUP);
                    putX.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/RuntimeException", "<init>", "()V", false);
                    putX.visitInsn(Opcodes.ATHROW);

                    putX.visitMaxs(0, 0);
                }
            }


            return ccppx.toByteArray();
        }

        @Override
        protected Object extArg(MethodHandles.Lookup lookup, Object extParam) {
            try {
                return lookup.findConstructor(lookup.lookupClass(), MethodType.methodType(void.class))
                        .asType(MethodType.methodType(VaArgModelBuilder.class));
            } catch (ReflectiveOperationException e) {
                throw new RuntimeException(e);
            }
        }
    };


    static VaArgModelBuilder builder(Iterable<ShortenType> type) {
        var key = ShortenType.key(type).replace('L', 'I');
        try {
            return (VaArgModelBuilder) ((MethodHandle) ALLOCATOR.gExtArg(key, null)).invokeExact();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }
}