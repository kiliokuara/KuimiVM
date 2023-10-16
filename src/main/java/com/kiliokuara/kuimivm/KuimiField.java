package com.kiliokuara.kuimivm;

import org.objectweb.asm.Type;

public class KuimiField extends KuimiMember {
    private final KuimiClass declaredClass;
    private final String name;
    private final KuimiClass type;
    private final int modifiers;

    int slot = -1, objIndex = -1;
    long offset = -1;

    final long size;

    public KuimiField(KuimiClass declaredClass, int modifiers, String name, KuimiClass type) {
        this.declaredClass = declaredClass;
        this.name = name;
        this.type = type;
        this.modifiers = modifiers;

        this.size = switch (type.getClassType().getSort()) {
            case Type.ARRAY, Type.OBJECT -> type.getVm().objectPointerSize();
            case Type.BOOLEAN, Type.BYTE -> 1;
            case Type.CHAR -> Character.BYTES;
            case Type.SHORT -> Short.BYTES;
            case Type.INT -> Integer.BYTES;
            case Type.LONG -> Long.BYTES;
            case Type.DOUBLE -> Double.BYTES;
            case Type.FLOAT -> Float.BYTES;

            default -> throw new RuntimeException("Assertion error: Cannot detect field type size");
        };
    }


    public int getModifiers() {
        return modifiers;
    }

    public KuimiClass getDeclaredClass() {
        return declaredClass;
    }

    public KuimiClass getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        KuimiMethod.sharedAppendModifiers(modifiers, sb);

        sb.append(declaredClass.getTypeName()).append(".").append(name).append(": ").append(type.getTypeName());
        return sb.toString();
    }


    public int getFieldSlot() {
        if (slot < 0) throw new IllegalStateException("Field slot not allocated");
        return slot;
    }

    public int getObjectIndex() {
        if (objIndex < 0) {
            throw new IllegalStateException("Object index not allocated or field is not a object field");
        }
        return objIndex;
    }

    public long getOffset() {
        if (offset < 0) {
            throw new IllegalStateException("Offset not allocated");
        }
        return offset;
    }
}
