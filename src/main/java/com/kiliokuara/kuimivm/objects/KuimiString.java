package com.kiliokuara.kuimivm.objects;

import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;

public class KuimiString extends KuimiObject<String> {
    public KuimiString(KuimiVM kuimiVM, String data) {
        super(kuimiVM.getStringClass());
        super.setDelegateInstance(data);
    }

    @Override
    public String toString() {
        return getDelegateInstance();
    }

    @Override
    public void setDelegateInstance(String delegateInstance) {
        throw new UnsupportedOperationException();
    }


    public void forceChangeValue(String value) {
        super.setDelegateInstance(value);
    }

    @Override
    public int hashCode() {
        return getDelegateInstance().hashCode();
    }
}
