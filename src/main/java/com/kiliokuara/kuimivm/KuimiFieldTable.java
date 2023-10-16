package com.kiliokuara.kuimivm;

import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public class KuimiFieldTable extends KuimiMemberTable {
    private final KuimiClass declaredClass;
    private final List<KuimiField> staticFields; // merged
    private final List<KuimiField> objectFields; // merged
    private final List<KuimiField> declaredFields;
    private final List<KuimiField> mergedFields;

    long staticFieldsSize, objectFieldsSize;
    int staticObjectCount, objectCount;

    public KuimiFieldTable(KuimiClass declaredClass) {
        this.declaredClass = declaredClass;

        this.staticFields = new ArrayList<>();
        this.objectFields = new ArrayList<>();
        this.declaredFields = new ArrayList<>();
        this.mergedFields = new ArrayList<>();
    }


    public void addField(KuimiField field) {
        Objects.requireNonNull(field, "field");
        if (field.slot != -1) {
            throw new IllegalStateException("Field is already attached.");
        }
        if (field.getDeclaredClass() != declaredClass) {
            throw new IllegalStateException("Attaching non-class field");
        }

        {
            var cmod = declaredClass.getModifiers();
            if ((cmod & Opcodes.ACC_INTERFACE) != 0) {
                var fmod = field.getModifiers();
                if ((fmod & Opcodes.ACC_STATIC) == 0) {
                    throw new IllegalArgumentException("Attaching a object field to a interface");
                }
            }
        }

        acquireModifyLock();
        try {
            if (findDeclaredField(field) != null) {
                throw new IllegalStateException("Field " + field + " is already attached with same signature");
            }

            declaredFields.add(field);

            field.slot = -2; // mark already attached
        } finally {
            releaseModifyLock();
        }
    }

    public void closeTable() {
        { // close parent first
            var sup = declaredClass.getSuperClass();
            if (sup != null) {
                sup.getFieldTable().closeTable();
            }
        }

        while (true) {
            var s = status;
            if (s > 0) {
                throw new IllegalStateException("This field table is still under editing.");
            }
            if (s == 0) {
                if (STATUS.compareAndSet((KuimiMemberTable) this, 0, -1)) {
                    break;
                }
                continue;
            }
            return;
        }

        {
            var sup = declaredClass.getSuperClass();
            if (sup != null) {
                var supft = sup.getFieldTable();
                staticFieldsSize = supft.staticFieldsSize;
                objectFieldsSize = supft.objectFieldsSize;

                staticObjectCount = supft.staticObjectCount;
                objectCount = supft.objectCount;

                staticFields.addAll(supft.staticFields);
                objectFields.addAll(supft.objectFields);

                mergedFields.addAll(supft.mergedFields);
            }
        }


        if (declaredClass.getSuperClass() == null) { // java.lang.Object or other no parent class
            var jlc = declaredClass.getVm().getClassClass();
            var jlo = declaredClass.getVm().getBaseClass();

            var objFields = Stream.concat(jlc.getFieldTable().declaredFields.stream(), jlo.getFieldTable().declaredFields.stream())
                    .filter(it -> !Modifier.isStatic(it.getModifiers()))
                    .toList();

            long spadding = 0;
            for (var f : objFields) {
                spadding = alignUp(spadding, f.size);
                spadding += f.size;


                boolean isObject;
                {
                    var stype = f.getType().getClassType().getSort();
                    isObject = stype == Type.ARRAY || stype == Type.OBJECT;
                }

                if (isObject) {
                    staticObjectCount++;
                }
            }
            staticFieldsSize = spadding; // reserved for java.lang.Object & java.lang.Class fields
        }

        var sfstart = staticFieldsSize;
        var ofstart = objectFieldsSize;

        var oocount = objectCount;
        var socount = staticObjectCount;

        for (var f : declaredFields) {
            var mod = f.getModifiers();
            var isStatic = Modifier.isStatic(mod);
            boolean isObject;
            {
                var stype = f.getType().getClassType().getSort();
                isObject = stype == Type.ARRAY || stype == Type.OBJECT;
            }

            f.slot = mergedFields.size();
            mergedFields.add(f);


            if (isStatic) {
                staticFields.add(f);

                sfstart = alignUp(sfstart, f.size);
                f.offset = sfstart;
                sfstart += f.size;

                if (isObject) {
                    f.objIndex = socount;
                    socount++;
                }
            } else {
                objectFields.add(f);

                ofstart = alignUp(ofstart, f.size);
                f.offset = ofstart;
                ofstart += f.size;

                if (isObject) {
                    f.objIndex = oocount;
                    oocount++;
                }
            }
        }

        staticFieldsSize = sfstart;
        objectFieldsSize = ofstart;

        objectCount = oocount;
        staticObjectCount = socount;

        { // inherit java.lang.Class
            var jc = declaredClass.getVm().getClassClass();
            jc.getFieldTable().closeTable();
        }
    }

    private KuimiField findDeclaredField(KuimiField req) {
        for (var f : declaredFields) {
            if (!f.getType().equals(req.getType())) continue;
            if (!f.getName().equals(req.getName())) continue;

            return f;
        }
        return null;
    }


    private static long alignUp(long n, long alignment) {
        return (n + alignment - 1) & -alignment;
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("FieldTable[").append(declaredClass.getTypeName())
                .append(", status=").append(status)
                .append(", objectFieldSize=").append(objectFieldsSize)
                .append(", objectObjectCount=").append(objectCount)
                .append(", staticFieldSize=").append(staticFieldsSize)
                .append(", staticObjectCount=").append(staticObjectCount)
                .append('\n');

        var vp = true;
        for (var f : mergedFields) {
            if (vp && f.getDeclaredClass().equals(declaredClass)) {
                sb.append("|===\n");
                vp = false;
            }

            sb.append("| S[").append(f.slot).append("]<").append(f.offset).append("\t..").append(f.offset + f.size).append("\t>{").append(f.size).append("\t}");
            if (f.objIndex != -1) {
                sb.append('[').append(f.objIndex).append(']');
            }
            sb.append(' ').append(f).append('\n');
        }
        return sb.append(']').toString();
    }

    public List<KuimiField> getDeclaredFields() {
        closeTable();
        return Collections.unmodifiableList(declaredFields);
    }

    public List<KuimiField> getMergedFields() {
        closeTable();
        return Collections.unmodifiableList(mergedFields);
    }

    public List<KuimiField> getObjectFields() {
        closeTable();
        return Collections.unmodifiableList(objectFields);
    }

    public List<KuimiField> getStaticFields() {
        closeTable();
        return Collections.unmodifiableList(staticFields);
    }

    public KuimiClass getDeclaredClass() {
        return declaredClass;
    }

    public long getObjectFieldsSize() {
        closeTable();
        return objectFieldsSize;
    }

    public long getStaticFieldsSize() {
        closeTable();
        return staticFieldsSize;
    }

    public KuimiField findField(boolean isStatic, String name) {
        closeTable();
        for (var iterator = mergedFields.listIterator(mergedFields.size()); iterator.hasPrevious(); ) {
            var f = iterator.previous();

            if (Modifier.isStatic(f.getModifiers()) != isStatic) continue;
            if (!f.getName().equals(name)) continue;
            return f;
        }
        return null;
    }

    public KuimiField findField(boolean isStatic, String name, KuimiClass type) {
        closeTable();
        for (var iterator = mergedFields.listIterator(mergedFields.size()); iterator.hasPrevious(); ) {
            var f = iterator.previous();

            if (Modifier.isStatic(f.getModifiers()) != isStatic) continue;
            if (!f.getType().equals(type)) continue;
            if (!f.getName().equals(name)) continue;
            return f;
        }
        return null;
    }
}
