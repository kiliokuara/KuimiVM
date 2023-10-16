package com.kiliokuara.kuimivm;

import java.lang.invoke.ConstantBootstraps;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;

public class KuimiMemberTable {
    protected volatile int status;
    static final VarHandle STATUS = ConstantBootstraps.fieldVarHandle(
            MethodHandles.lookup(), "status", VarHandle.class,
            KuimiMemberTable.class, int.class
    ).withInvokeExactBehavior();



    public boolean isImmutable() {
        return status < 0;
    }

    protected final void acquireModifyLock() {
        while (true) {
            var s = status;
            if (s < 0) {
                throw new IllegalStateException("This method table is immutable.");
            }
            if (STATUS.compareAndSet(this, s, s + 1)) return;
        }
    }

    protected final void releaseModifyLock() {
        while (true) {
            var s = status;
            if (s < 1) {
                throw new IllegalStateException("Internal exception: bad status, no lock can be released.");
            }
            if (STATUS.compareAndSet(this, s, s - 1)) return;
        }
    }

}
