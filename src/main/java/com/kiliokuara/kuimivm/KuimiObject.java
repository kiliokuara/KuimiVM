package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.attributes.AttributeMap;

import java.util.Objects;

public class KuimiObject<T> {
    private final KuimiClass type;
    protected volatile T delegateInstance;
    private AttributeMap<KuimiObject<T>> attributeMap;


    public KuimiObject(KuimiClass objectType) {
        Objects.requireNonNull(objectType, "object type");
        this.type = objectType;
    }

    KuimiObject() { // reserved for KuimiClass
        this.type = null;
    }

    public KuimiVM getVm() {
        assert type != null;
        return type.getVm();
    }

    public T getDelegateInstance() {
        return delegateInstance;
    }

    public void setDelegateInstance(T delegateInstance) {
        this.delegateInstance = delegateInstance;
    }

    public KuimiClass getObjectClass() {
        return type;
    }

    public AttributeMap<KuimiObject<T>> getAttributeMap() {
        if (attributeMap != null) return attributeMap;
        synchronized (this) {
            if (attributeMap != null) return attributeMap;

            return attributeMap = new AttributeMap<>(this);
        }
    }

    public KuimiObjectMemory memoryView() {
        return getAttributeMap().attribute(KuimiObjectMemory.ATTRIBUTE_KEY);
    }

    @Override
    public String toString() {
        var d = delegateInstance;
        if (d != null) return d.toString();

        return getObjectClass().getTypeName() + "@" + hashCode();
    }

    @Override
    public int hashCode() {
        var d = delegateInstance;
        if (d != null) return d.hashCode();
        return System.identityHashCode(this);
    }
}
