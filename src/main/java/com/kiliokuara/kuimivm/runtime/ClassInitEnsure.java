package com.kiliokuara.kuimivm.runtime;

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiField;
import com.kiliokuara.kuimivm.KuimiMethod;

public class ClassInitEnsure {
    public static KuimiClass ensureInit(KuimiClass kuimiClass) {
        kuimiClass.ensureClassInitialized();
        return kuimiClass;
    }

    public static KuimiField ensureInit(KuimiField field) {
        field.getDeclaredClass().ensureClassInitialized();
        return field;
    }

    public static KuimiMethod ensureInit(KuimiMethod method) {
        method.getDeclaredClass().ensureClassInitialized();
        return method;
    }
}
