package com.kiliokuara.kuimivm.execute;

// 0000 0000 0000 0000  0000 0000 0000 0000
// [][     ] [                            ] offset
//  |  |
//  | Page
// Type

// Type:
// 11: Global objects
// 01: Local frame objects
// 10: Weak global references

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;
import org.objectweb.asm.Type;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class ObjectPool {

    public static final int LOCAL_OBJECT_PREFIX = 0b0100_0000_0000_0000__0000_0000_0000_0000;
    public static final int GLOBAL_OBJECT_PREFIX = 0b1100_0000_0000_0000__0000_0000_0000_0000;
    public static final int WEAK_GLOBAL_OBJECT_PREFIX = 0b1000_0000_0000_0000__0000_0000_0000_0000;

    public static final int OBJECT_PREFIX = 0b1100_0000_0000_0000__0000_0000_0000_0000;


    final KuimiObject<?>[] objects;
    volatile int objIndex; // first null start index

    private static final VarHandle OBJ_INDEX, OBJ_MOD;

    private final KuimiObject<?> OBJ_UPDATE_PLACEHOLDER;

    static {
        var lk = MethodHandles.lookup();
        try {
            OBJ_INDEX = lk.findVarHandle(ObjectPool.class, "objIndex", int.class).withInvokeExactBehavior();
            OBJ_MOD = MethodHandles.arrayElementVarHandle(KuimiObject[].class).withInvokeExactBehavior();
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public ObjectPool(KuimiVM vm, int size) {
        objects = new KuimiObject[size];
        OBJ_UPDATE_PLACEHOLDER = new KuimiClass(vm, Type.VOID_TYPE, 0, null, null, null);
    }

    public int addObject(KuimiObject<?> object) {
        if (object == null) return -1;
        while (true) {
            var sv = objIndex;
            if (objects[sv] == null) {
                if (OBJ_MOD.compareAndSet(objects, sv, (KuimiObject<?>) null, OBJ_UPDATE_PLACEHOLDER)) {
                    OBJ_MOD.setVolatile(objects, sv, object);

                    OBJ_INDEX.compareAndSet(this, sv, sv + 1);

                    return sv;
                }
            } else {
                OBJ_INDEX.compareAndSet(this, sv, sv + 1);
            }
        }
    }

    public KuimiObject<?> removeObject(int index) {
        if (index < 0) return null;
        while (true) {
            var obj = (KuimiObject<?>) OBJ_MOD.getVolatile(objects, index);
            if (obj == null) return null;
            if (obj == OBJ_UPDATE_PLACEHOLDER) continue;

            if (OBJ_MOD.compareAndSet(objects, index, obj, OBJ_UPDATE_PLACEHOLDER)) {
                OBJ_MOD.setVolatile(objects, index, (KuimiObject<?>) null);

                while (true) {
                    var cidx = objIndex;
                    if (cidx < index) break;
                    if (OBJ_INDEX.compareAndSet(this, cidx, index)) break;
                }

                return obj;
            }
        }
    }

    public KuimiObject<?> getObject(int index) {
        while (true) {
            var rsp = (KuimiObject<?>) OBJ_MOD.getVolatile(objects, index);
            if (rsp == OBJ_UPDATE_PLACEHOLDER) continue;

            return rsp;
        }
    }
}
