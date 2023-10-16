package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.attributes.AttributeMap;

public abstract class KuimiMember {
    public abstract KuimiClass getDeclaredClass();

    private volatile AttributeMap<? extends KuimiMember> attributeMap;

    public AttributeMap<? extends KuimiMember> getAttributeMap() {
        if (attributeMap != null) return attributeMap;

        synchronized (this) {
            if (attributeMap != null) return attributeMap;
            return attributeMap = new AttributeMap<>(this);
        }
    }
}
