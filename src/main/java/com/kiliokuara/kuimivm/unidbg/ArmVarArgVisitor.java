package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.Emulator;
import com.github.unidbg.pointer.UnidbgPointer;

public class ArmVarArgVisitor {
    protected static final int REG_OFFSET = 3;


    protected static UnidbgPointer getArg(Emulator<?> emulator, int index) {
        return emulator.getContext().getPointerArg(REG_OFFSET + index);
    }

    protected static int getInt(Emulator<?> emulator, int index) {
        UnidbgPointer ptr = getArg(emulator, index);
        return ptr == null ? 0 : ptr.toIntPeer();
    }

}
