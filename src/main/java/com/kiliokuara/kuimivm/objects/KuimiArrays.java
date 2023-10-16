package com.kiliokuara.kuimivm.objects;

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiObject;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.HexFormat;
import java.util.function.IntFunction;

public class KuimiArrays {
    public interface Array {
        int length();
    }

    public static class ArrayObject extends KuimiObject<KuimiObject<?>[]> implements Array {

        public ArrayObject(KuimiClass objectType, KuimiObject<?>[] data) {
            super(objectType);
            setDelegateInstance(data);
        }

        @Override
        public int length() {
            return getDelegateInstance().length;
        }
    }

    public static class ArrayClass extends KuimiClass {

        public ArrayClass(KuimiClass elmType) {
            super(
                    elmType.getVm(), Type.getType("[" + elmType.getClassType().getDescriptor()),
                    Opcodes.ACC_PUBLIC,
                    elmType.getClassLoader(),
                    elmType.getVm().getBaseClass(),
                    null
            );
        }

        @Override
        public boolean isArray() {
            return true;
        }

        @Override
        public boolean isPrimitive() {
            return false;
        }

        @Override
        public KuimiObject<?> allocateNewObject() {
            throw new RuntimeException("Allocating a array directly is not allowed");
        }

        public KuimiObject<?> allocateArray(int size) {
            return new ArrayObject(this, new KuimiObject[size]);
        }
    }

    public static class PrimitiveArrayClass extends ArrayClass {

        private final IntFunction<Object> arrayAllocator;

        public PrimitiveArrayClass(KuimiClass elmType, IntFunction<Object> arrayAllocator) {
            super(elmType);
            this.arrayAllocator = arrayAllocator;
        }

        @Override
        public KuimiObject<?> allocateArray(int size) {
            return new PrimitiveArray<>(this, arrayAllocator.apply(size));
        }
    }

    public static class PrimitiveArray<T> extends KuimiObject<T> implements Array {
        public PrimitiveArray(KuimiClass objectType, T array) {
            super(objectType);
            setDelegateInstance(array);
        }

        @Override
        public int length() {
            return java.lang.reflect.Array.getLength(getDelegateInstance());
        }

        @Override
        public String toString() {
            var dobj = getDelegateInstance();
            if (dobj instanceof byte[] barr) {
                var sb = new StringBuilder()
                        .append("byte[").append(barr.length).append("]@").append(hashCode()).append(": ");

                HexFormat.ofDelimiter(" ").withUpperCase().formatHex(sb, barr);
                return sb.toString();
            }
            return super.toString();
        }
    }
}
