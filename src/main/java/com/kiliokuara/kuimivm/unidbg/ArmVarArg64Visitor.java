package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.Emulator;
import com.github.unidbg.pointer.UnidbgPointer;
import com.kiliokuara.kuimivm.vaarg.ShortenType;
import com.kiliokuara.kuimivm.vaarg.VaArgModelBuilder;
import unicorn.Arm64Const;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

public class ArmVarArg64Visitor extends ArmVarArgVisitor {
    public static void read(Emulator<?> emulator, VaArgModelBuilder output, List<ShortenType> shorties) {
        int offset = 0;
        int floatOff = 0;
        for (var shorty : shorties) {
            switch (shorty) {
                case INT, REF -> {
                    output.putInt(getInt(emulator, offset++));
                }
                case DOUBLE -> {
                    output.putDouble(getVectorArg(emulator, floatOff++));
                }
                case FLOAT -> {
                    output.putFloat((float) getVectorArg(emulator, floatOff++));
                }
                case LONG -> {
                    UnidbgPointer ptr = getArg(emulator, offset++);
                    output.putLong(ptr == null ? 0L : ptr.peer);
                }
                default -> throw new IllegalStateException("c=" + shorty);
            }
        }
    }


    private static double getVectorArg(Emulator<?> emulator, int index) {
        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(emulator.getBackend().reg_read_vector(Arm64Const.UC_ARM64_REG_Q0 + index));
        buffer.flip();
        return buffer.getDouble();
    }
}
