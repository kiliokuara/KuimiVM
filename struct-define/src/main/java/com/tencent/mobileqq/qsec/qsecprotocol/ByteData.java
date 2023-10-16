package com.tencent.mobileqq.qsec.qsecprotocol;

import android.content.Context;

public class ByteData {
    public static final ByteData INSTANCE = new ByteData();
    private static Context context;
    public static ByteData getInstance() { return INSTANCE; }

    public native byte[] getByte(Context context, Object obj);

    public byte[] getSign(String str, String str2, byte[] data) {
        return getByte(context, data);
    }

    public void init(Context context) {
        this.context = context;
    }
}
