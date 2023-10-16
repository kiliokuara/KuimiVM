package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.execute.ObjectPool;
import org.objectweb.asm.Type;

public abstract class KuimiVM {
    public abstract KuimiClass getBaseClass();

    public abstract KuimiClass getClassClass();
    public abstract KuimiClass getStringClass();

    public abstract KuimiClass getPrimitiveClass(Type type);

    public abstract long objectPointerSize();

    public abstract KuimiClass resolveClass(Type type);

    public abstract KuimiObject<?> resolveObject(int ptr);

    public abstract ObjectPool getGlobalPool();

    public abstract ObjectPool getWeakGlobalPool();


    public abstract StackTrace getStackTrace();

    public abstract void attachThread(StackTrace stackTrace);

    public abstract void detatchThread();
}
