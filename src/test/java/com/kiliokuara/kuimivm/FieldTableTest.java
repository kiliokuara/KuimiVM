package com.kiliokuara.kuimivm;

import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

public class FieldTableTest extends KuimiTestBase {
    @Test
    void fieldExtends() {
        var baseC = new KuimiClass(vm, Type.getObjectType("TestSup"), 0, null, vm.getBaseClass(), null);

        var subC = new KuimiClass(vm, Type.getObjectType("TestSub"), 0, null, baseC, null);


        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "objFI", vm.getPrimitiveClass(Type.INT_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "objLA", vm.getPrimitiveClass(Type.LONG_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "objFI2", vm.getPrimitiveClass(Type.INT_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "objFI3", vm.getPrimitiveClass(Type.INT_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "f4", vm.getPrimitiveClass(Type.BYTE_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, 0, "f5", vm.getPrimitiveClass(Type.INT_TYPE)));


        baseC.getFieldTable().addField(new KuimiField(baseC, Opcodes.ACC_STATIC, "st1", vm.getPrimitiveClass(Type.INT_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, Opcodes.ACC_STATIC, "st2", vm.getPrimitiveClass(Type.LONG_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, Opcodes.ACC_STATIC, "st3", vm.getPrimitiveClass(Type.INT_TYPE)));
        baseC.getFieldTable().addField(new KuimiField(baseC, Opcodes.ACC_STATIC, "st4", baseC));

        baseC.getFieldTable().closeTable();


        subC.getFieldTable().addField(new KuimiField(subC, 0, "objFI", vm.getPrimitiveClass(Type.INT_TYPE)));


        subC.getFieldTable().addField(new KuimiField(subC, Opcodes.ACC_STATIC, "si", vm.getPrimitiveClass(Type.INT_TYPE)));

        subC.getFieldTable().closeTable();

        System.out.println(baseC.getFieldTable());
        System.out.println(subC.getFieldTable());
        System.out.println(vm.getBaseClass().getFieldTable());
        System.out.println(vm.getClassClass().getFieldTable());
    }
}
