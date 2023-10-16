package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.Emulator;
import com.github.unidbg.pointer.UnidbgPointer;
import com.kiliokuara.kuimivm.vaarg.ShortenType;
import com.kiliokuara.kuimivm.vaarg.VaArgModelBuilder;
import com.sun.jna.Pointer;

import java.util.List;

@SuppressWarnings("DuplicatedCode")
public class VaList64Visitor {
    public static void read(Emulator<?> emulator, UnidbgPointer va_list, VaArgModelBuilder output, List<ShortenType> shorties) {


        long base_p = va_list.getLong(0);
        long base_integer = va_list.getLong(8);
        long base_float = va_list.getLong(16);
        int mask_integer = va_list.getInt(24);
        int mask_float = va_list.getInt(28);

        for (var shorty : shorties) {
            switch (shorty) {
                case INT -> {
                    Pointer pointer;
                    if ((mask_integer & 0x80000000) != 0) {
                        if (mask_integer + 8 <= 0) {
                            pointer = UnidbgPointer.pointer(emulator, base_integer + mask_integer);
                            mask_integer += 8;
                        } else {
                            pointer = UnidbgPointer.pointer(emulator, base_p);
                            mask_integer += 8;
                            base_p = (base_p + 11) & 0xfffffffffffffff8L;
                        }
                    } else {
                        pointer = UnidbgPointer.pointer(emulator, base_p);
                        base_p = (base_p + 11) & 0xfffffffffffffff8L;
                    }
                    assert pointer != null;
                    output.putInt(pointer.getInt(0));
                }
                case DOUBLE -> {
                    Pointer pointer;
                    if ((mask_float & 0x80000000) != 0) {
                        if (mask_float + 16 <= 0) {
                            pointer = UnidbgPointer.pointer(emulator, base_float + mask_float);
                            mask_float += 16;
                        } else {
                            pointer = UnidbgPointer.pointer(emulator, base_p);
                            mask_float += 16;
                            base_p = (base_p + 15) & 0xfffffffffffffff8L;
                        }
                    } else {
                        pointer = UnidbgPointer.pointer(emulator, base_p);
                        base_p = (base_p + 15) & 0xfffffffffffffff8L;
                    }
                    assert pointer != null;
                    output.putDouble(pointer.getDouble(0));
                }
                case FLOAT -> {
                    Pointer pointer;
                    if ((mask_float & 0x80000000) != 0) {
                        if (mask_float + 16 <= 0) {
                            pointer = UnidbgPointer.pointer(emulator, base_float + mask_float);
                            mask_float += 16;
                        } else {
                            pointer = UnidbgPointer.pointer(emulator, base_p);
                            mask_float += 16;
                            base_p = (base_p + 15) & 0xfffffffffffffff8L;
                        }
                    } else {
                        pointer = UnidbgPointer.pointer(emulator, base_p);
                        base_p = (base_p + 15) & 0xfffffffffffffff8L;
                    }
                    assert pointer != null;
                    output.putFloat((float) pointer.getDouble(0));
                }
                case LONG -> {
                    Pointer pointer;
                    if ((mask_integer & 0x80000000) != 0) {
                        if (mask_integer + 8 <= 0) {
                            pointer = UnidbgPointer.pointer(emulator, base_integer + mask_integer);
                            mask_integer += 8;
                        } else {
                            pointer = UnidbgPointer.pointer(emulator, base_p);
                            mask_integer += 8;
                            base_p = (base_p + 15) & 0xfffffffffffffff8L;
                        }
                    } else {
                        pointer = UnidbgPointer.pointer(emulator, base_p);
                        base_p = (base_p + 15) & 0xfffffffffffffff8L;
                    }
                    assert pointer != null;
                    output.putLong(pointer.getLong(0));
                }
                case REF -> {
                    Pointer pointer;
                    if ((mask_integer & 0x80000000) != 0) {
                        if (mask_integer + 8 <= 0) {
                            pointer = UnidbgPointer.pointer(emulator, base_integer + mask_integer);
                            mask_integer += 8;
                        } else {
                            pointer = UnidbgPointer.pointer(emulator, base_p);
                            mask_integer += 8;
                            base_p = (base_p + 15) & 0xfffffffffffffff8L;
                        }
                    } else {
                        pointer = UnidbgPointer.pointer(emulator, base_p);
                        base_p = (base_p + 15) & 0xfffffffffffffff8L;
                    }

                    assert pointer != null;
                    output.putInt(pointer.getInt(0));
                }
                default -> throw new IllegalStateException("c=" + shorty);
            }
        }
    }
}
