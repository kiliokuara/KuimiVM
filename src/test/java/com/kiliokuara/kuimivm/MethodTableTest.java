package com.kiliokuara.kuimivm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.util.List;

public class MethodTableTest extends KuimiTestBase {

    @Test
    void canExpandClassModel() {
        var baseClassMT = vm.getBaseClass().getMethodTable();
        var baseClassMTSize = baseClassMT.getDeclaredMethods().size();


        var baseC = new KuimiClass(vm, Type.getObjectType("TestSup"), 0, null, vm.getBaseClass(), null);
        var parentMethod = new KuimiMethod(baseC, 0, "met1", vm.getPrimitiveClass(Type.VOID_TYPE), List.of());
        baseC.getMethodTable().addMethod(parentMethod);
        baseC.getMethodTable().closeTable();
        System.out.println(baseC.getMethodTable());


        var extC = new KuimiClass(vm, Type.getObjectType("Ext"), 0, null, baseC, null);
        var subMethod = new KuimiMethod(extC, 0, "met2", vm.getPrimitiveClass(Type.VOID_TYPE), List.of());
        extC.getMethodTable().addMethod(subMethod);
        extC.getMethodTable().closeTable();
        System.out.println(extC.getMethodTable());


        Assertions.assertEquals(baseClassMTSize + 1, baseC.getMethodTable().getMergedMethods().size());
        Assertions.assertSame(parentMethod, baseC.getMethodTable().getMergedMethods().get(baseClassMTSize));

        Assertions.assertEquals(baseClassMTSize + 2, extC.getMethodTable().getMergedMethods().size());
        Assertions.assertEquals(subMethod, extC.getMethodTable().getMergedMethods().get(baseClassMTSize + 1));

    }

    @Test
    void canOverrideClassModel() {
        var baseClassMT = vm.getBaseClass().getMethodTable();
        var baseClassMTSize = baseClassMT.getDeclaredMethods().size();


        var baseC = new KuimiClass(vm, Type.getObjectType("TestSup"), 0, null, vm.getBaseClass(), null);
        var parentMethod = new KuimiMethod(baseC, 0, "met1", vm.getPrimitiveClass(Type.VOID_TYPE), List.of());
        baseC.getMethodTable().addMethod(parentMethod);
        baseC.getMethodTable().closeTable();
        System.out.println(baseC.getMethodTable());


        var extC = new KuimiClass(vm, Type.getObjectType("Ext"), 0, null, baseC, null);
        var subMethod = new KuimiMethod(extC, 0, "met1", vm.getPrimitiveClass(Type.VOID_TYPE), List.of());
        extC.getMethodTable().addMethod(subMethod);
        extC.getMethodTable().closeTable();
        System.out.println(extC.getMethodTable());


        Assertions.assertEquals(baseClassMTSize + 1, baseC.getMethodTable().getMergedMethods().size());
        Assertions.assertSame(parentMethod, baseC.getMethodTable().getMergedMethods().get(baseClassMTSize));

        Assertions.assertEquals(baseClassMTSize + 1, extC.getMethodTable().getMergedMethods().size());
        Assertions.assertEquals(subMethod, extC.getMethodTable().getMergedMethods().get(baseClassMTSize));
    }

    @Test
    void interfaces() {

        var itf1 = new KuimiClass(vm, Type.getObjectType("Itf1"), Opcodes.ACC_INTERFACE, null, vm.getBaseClass(), null);
        var itf2 = new KuimiClass(vm, Type.getObjectType("Itf2"), Opcodes.ACC_INTERFACE, null, vm.getBaseClass(), null);
        var itf3 = new KuimiClass(vm, Type.getObjectType("Itf3"), Opcodes.ACC_INTERFACE, null, vm.getBaseClass(), null);
        var itf4 = new KuimiClass(vm, Type.getObjectType("Itf4"), Opcodes.ACC_INTERFACE, null, vm.getBaseClass(), null);

        itf1.getMethodTable().addMethod(new KuimiMethod(itf1, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "met1", vm.getPrimitiveClass(Type.VOID_TYPE), List.of()));
        itf2.getMethodTable().addMethod(new KuimiMethod(itf2, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "met2", vm.getPrimitiveClass(Type.VOID_TYPE), List.of()));
        itf3.getMethodTable().addMethod(new KuimiMethod(itf3, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "met3", vm.getPrimitiveClass(Type.VOID_TYPE), List.of()));
        itf4.getMethodTable().addMethod(new KuimiMethod(itf4, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "met4", vm.getPrimitiveClass(Type.VOID_TYPE), List.of()));


        itf2.getMethodTable().addMethod(new KuimiMethod(itf2, Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT, "met4", vm.getPrimitiveClass(Type.VOID_TYPE), List.of()));

        itf1.getMethodTable().closeTable();
        itf2.getMethodTable().closeTable();
        itf3.getMethodTable().closeTable();
        itf4.getMethodTable().closeTable();


        var subc = new KuimiClass(vm, Type.getObjectType("SubC"), 0, null, vm.getBaseClass(), List.of(itf1, itf2, itf3, itf4));
        subc.getMethodTable().closeTable();
        System.out.println(subc.getMethodTable());


        System.out.println(itf4.getMethodTable());
    }
}
