package com.tencent.mobileqq.qsec.qsecurity;

import android.content.Context;
import com.tencent.mobileqq.qsec.qsecdandelionsdk.Dandelion;
import com.tencent.mobileqq.qsec.qsecprotocol.ByteData;

public class QSec {
    public static final QSec INSTANCE = new QSec();

    public static QSec getInstance() {
        return INSTANCE;
    }

    public static byte[] getLiteSign(String str, byte[] barr) {
        return Dandelion.getInstance().fly(str, barr);
    }

    public static byte[] getSign(String uin, String str, byte[] barr) {
        return ByteData.INSTANCE.getSign(uin, str, barr);
    }

    public void updateO3DID(String str) {
        QSecConfig.business_o3did=str;
    }
    public void updateUserID(String str) {
        QSecConfig.business_uin = str;
    }

    private native int doReport(String str, String str2, String str3, String str4);

    private native int doSomething(Context context, int i2);
}
