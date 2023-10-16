package com.kiliokuara.kuimivm.abstractvm;

import com.kiliokuara.kuimivm.KuimiClass;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ClassPool {
    private ClassPool parent;
    private final Map<String, KuimiClass> classMap = new ConcurrentHashMap<>();

    public ClassPool() {
    }

    public ClassPool(ClassPool parent) {
        this.parent = parent;
    }

    public void put(String type, KuimiClass target) {
        classMap.put(type, target);
    }

    public void put(KuimiClass target) {
        put(target.getTypeName(), target);
    }

    public KuimiClass resolve(String type) {
        if (parent != null) {
            var pt = parent.resolve(type);
            if (pt != null) return pt;
        }
        return classMap.get(type);
    }

}
