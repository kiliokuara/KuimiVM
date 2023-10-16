package com.tencent.mobileqq.qsec.qsecdandelionsdk;

public class Dandelion {

    private native byte[] energy(Object v1, Object v2);

    public static final Dandelion INSTANCE = new Dandelion();
    private static final boolean isInit = false;

    public static Dandelion getInstance() {
        return INSTANCE;
    }

    public byte[] fly(String key, byte[] data) {
        // load so
        return energy(key, data);
    }
}
