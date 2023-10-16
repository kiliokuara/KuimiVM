package com.kiliokuara.kuimivm.execute;

import com.kiliokuara.kuimivm.KuimiTestBase;
import org.junit.jupiter.api.Test;

public class ObjectPoolTest extends KuimiTestBase {
    private static void dump(String title, ObjectPool pool) {
        printTitle(title);
        System.out.println("ObjIndx: " + pool.objIndex);

        var objects = pool.objects;
        for (int i = 0, objectsLength = objects.length; i < objectsLength; i++) {
            var obj = objects[i];
            if (obj != null) {
                System.out.println("| " + i + " -> " + obj);
            }
        }
    }


    @Test
    void test() {
        var pool = new ObjectPool(vm, 16);
        var bclass = pool.addObject(vm.getBaseClass());
        var cwclass = pool.addObject(vm.getBaseClass());
        var evclass = pool.addObject(vm.getBaseClass());
        System.out.println(bclass);
        System.out.println(cwclass);
        System.out.println(evclass);
        dump("FD", pool);

        pool.removeObject(0);
        dump("ED", pool);
        pool.removeObject(1);
        dump("SV", pool);

        pool.addObject(vm.getBaseClass());
        dump("EEA", pool);

        pool.addObject(vm.getBaseClass());
        dump("EEB", pool);

        pool.addObject(vm.getBaseClass());
        dump("EEC", pool);
    }
}
