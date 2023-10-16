package com.tencent.mobileqq.qsec.qseccodec;

public class SecCipher {
    private static final int SEC_INFO_TYPE_DECODE = 1;
    private static final int SEC_INFO_TYPE_ENCODE = 2;
    private static final String sVersion = "0.0.3";

    public static class SecInfo {
        public int err;
        public Object result;
        public int ver;
    }

    public native Object codec(Object obj, int i);

    public SecInfo decrypt(String str) {
        return (SecInfo) codec(str, 1);
    }

    public SecInfo encrypt(String str) {
        return (SecInfo) codec(str, 2);
    }
}
