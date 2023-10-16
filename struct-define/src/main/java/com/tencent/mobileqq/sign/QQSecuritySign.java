package com.tencent.mobileqq.sign;

import com.tencent.mobileqq.fe.EventCallback;
import com.tencent.mobileqq.qsec.qsecurity.QSec;

public class QQSecuritySign {
    private static final String TAG = "QQSecuritySDK";
    private static final QQSecuritySign INSTANCE = new QQSecuritySign();
    private static String mExtra;

    public static class SignResult {
        public byte[] extra;
        public byte[] sign;
        public byte[] token;
    }

    public static QQSecuritySign getInstance() {
        return INSTANCE;
    }

    private native SignResult getSign(QSec qSec, String str, String str2, byte[] bArr, byte[] bArr2, String str3);

    public native void dispatchEvent(String str, String str2, EventCallback eventCallback);

    public SignResult getSign(QSec qSec, String str, byte[] bArr, byte[] bArr2, String str2) {
        if (bArr != null && bArr.length > 0) {
            if (str == null || str.isEmpty()) {
                return new SignResult();
            }
            if (this.mExtra == null) {
                this.mExtra = "";
            }
            return getSign(qSec, this.mExtra, str, bArr, bArr2, str2);
        }
        return new SignResult();
    }

    public void init(String str) {
        this.mExtra = str;
    }

    public native void initSafeMode(boolean z);

    public native void requestToken();
}
