package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.Emulator;
import com.github.unidbg.pointer.UnidbgPointer;
import com.kiliokuara.kuimivm.vaarg.VaArgModelBuilder;
import com.kiliokuara.kuimivm.KuimiClass;
import com.sun.jna.Pointer;
import org.objectweb.asm.Type;

import java.util.List;

public class JValueListVisitor {
    public static void read(Emulator<?> emulator, UnidbgPointer jvalue, VaArgModelBuilder output, List<KuimiClass> params) {

        Pointer pointer = jvalue;
        for (var param : params) {
            switch (param.getClassType().getSort()) {
                case Type.OBJECT, Type.ARRAY -> {
                    UnidbgPointer ptr = (UnidbgPointer) pointer.getPointer(0);
                    output.putInt(ptr == null ? 0 : (int) ptr.toUIntPeer());
                }
                case Type.BYTE -> {
                    byte val = pointer.getByte(0);
                    output.putInt(val & 0xff);
                }
                case Type.BOOLEAN -> {
                    byte val = pointer.getByte(0);
                    output.putInt(val & 1);
                }
                case Type.CHAR -> {
                    char val = pointer.getChar(0);
                    output.putInt(val);
                }
                case Type.SHORT -> {
                    output.putInt(pointer.getShort(0));
                }
                case Type.INT -> {
                    output.putInt(pointer.getInt(0));
                }
                case Type.FLOAT -> {
                    output.putFloat((float) pointer.getDouble(0));
                }
                case Type.DOUBLE -> {
                    output.putDouble(pointer.getDouble(0));
                }
                case Type.LONG -> {
                    output.putLong(pointer.getLong(0));
                }
                default -> throw new IllegalStateException("c=" + param);
            }

            pointer = pointer.share(8);
        }

    }
}
