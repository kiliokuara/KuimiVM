package com.kiliokuara.kuimivm.unidbg;

import com.github.unidbg.AndroidEmulator;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;
import com.kiliokuara.kuimivm.attributes.AttributeKey;
import com.sun.jna.Pointer;

public abstract class KuimiUnidbgVM {
    public final KuimiVM vm;
    public final AndroidEmulator emulator;
    public boolean verbose;

    final AttributeKey<KuimiObject<?>, KuimiUnidbgObjectMemory> MEMORY = new AttributeKey<>("unidbg-memory", o -> new KuimiUnidbgObjectMemory(this));
    final JNIMemberTable jniMemberTable = new JNIMemberTable();

    public KuimiUnidbgVM(KuimiVM vm, AndroidEmulator emulator) {
        this.vm = vm;
        this.emulator = emulator;
    }


    public abstract Pointer getJavaVM();

    public abstract Pointer getJNIEnv();

}
