package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.memory.MemoryBlock;
import com.github.unidbg.pointer.UnidbgPointer;
import com.kiliokuara.kuimivm.KuimiObject;
import com.sun.jna.Pointer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class KuimiUnidbgObjectMemory {
    private final KuimiUnidbgVM vm;
    MemoryBlock memoryBlock;

    KuimiUnidbgObjectMemory(KuimiUnidbgVM vm) {
        this.vm = vm;
    }

    public UnidbgPointer allocateMemoryBlock(int length) {
        if (memoryBlock != null) return memoryBlock.getPointer();
        synchronized (this) {
            if (memoryBlock != null) return memoryBlock.getPointer();

            return (memoryBlock = vm.emulator.getMemory().malloc(length, true)).getPointer();
        }
    }

    public void freeMemoryBlock(Pointer pointer) {
        if (memoryBlock != null) {
            synchronized (this) {
                if (memoryBlock == null) return;

                if (pointer != null && !memoryBlock.isSame(pointer)) return;

                memoryBlock.free();
                memoryBlock = null;
            }
        }
    }

    public long arrayCritical(KuimiObject<?> arrx) {
        var arrObj = arrx.getDelegateInstance();

        if (arrObj instanceof boolean[] d) {
            var memory = allocateMemoryBlock(d.length);
            for (var i = 0; i < d.length; i++) {
                memory.setByte(i, d[i] ? (byte) 1 : 0);
            }
            return memory.peer;
        }
        if (arrObj instanceof byte[] d) {
            var memory = allocateMemoryBlock(d.length);
            memory.write(d);
            return memory.peer;
        }
        if (arrObj instanceof char[] d) {
            var memory = allocateMemoryBlock(d.length * 2);
            var wp = ByteBuffer.allocate(d.length * 2);
            wp.order(ByteOrder.LITTLE_ENDIAN).asCharBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        if (arrObj instanceof short[] d) {
            var memory = allocateMemoryBlock(d.length * 2);
            var wp = ByteBuffer.allocate(d.length * 2);
            wp.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        if (arrObj instanceof int[] d) {
            var memory = allocateMemoryBlock(d.length * 4);
            var wp = ByteBuffer.allocate(d.length * 4);
            wp.order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        if (arrObj instanceof long[] d) {
            var memory = allocateMemoryBlock(d.length * 8);
            var wp = ByteBuffer.allocate(d.length * 8);
            wp.order(ByteOrder.LITTLE_ENDIAN).asLongBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        if (arrObj instanceof float[] d) {
            var memory = allocateMemoryBlock(d.length * Float.BYTES);
            var wp = ByteBuffer.allocate(d.length * Float.BYTES);
            wp.order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        if (arrObj instanceof double[] d) {
            var memory = allocateMemoryBlock(d.length * Double.BYTES);
            var wp = ByteBuffer.allocate(d.length * Double.BYTES);
            wp.order(ByteOrder.LITTLE_ENDIAN).asDoubleBuffer().put(d);
            memory.write(wp.array());
            return memory.peer;
        }
        throw new IllegalArgumentException(arrx.toString());
    }

    @SuppressWarnings({"rawtypes", "UnnecessaryLocalVariable", "unchecked"})
    public void arrayCriticalRelease(KuimiObject<?> obj, Pointer pointer, int mode) {
        if (mode == VMConst.JNI_ABORT) {
            freeMemoryBlock(pointer);
            return;
        }
        KuimiObject objRaw = obj;
        if (mode == VMConst.JNI_COMMIT || mode == 0) {
            var arrObj = obj.getDelegateInstance();

            if (arrObj instanceof boolean[] d) {
                for (var i = 0; i < d.length; i++) {
                    d[i] = pointer.getByte(i) != 0;
                }
            } else if (arrObj instanceof byte[] d) {
                objRaw.setDelegateInstance(pointer.getByteArray(0, d.length));
            } else if (arrObj instanceof char[] d) {
                var buf = pointer.getByteBuffer(0, d.length * 2L);
                buf.asCharBuffer().get(d);
            } else if (arrObj instanceof short[] d) {
                var buf = pointer.getByteBuffer(0, d.length * 2L);
                buf.asShortBuffer().get(d);
            } else if (arrObj instanceof int[] d) {
                objRaw.setDelegateInstance(pointer.getIntArray(0, d.length));
            } else if (arrObj instanceof long[] d) {
                objRaw.setDelegateInstance(pointer.getLongArray(0, d.length));
            } else if (arrObj instanceof float[] d) {
                objRaw.setDelegateInstance(pointer.getFloatArray(0, d.length));
            } else if (arrObj instanceof double[] d) {
                objRaw.setDelegateInstance(pointer.getDoubleArray(0, d.length));
            } else {
                throw new IllegalArgumentException(obj.toString());
            }
        }
        if (mode == 0) {
            freeMemoryBlock(pointer);
        }
    }
}
