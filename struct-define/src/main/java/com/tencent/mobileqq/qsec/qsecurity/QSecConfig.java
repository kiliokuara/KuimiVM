package com.tencent.mobileqq.qsec.qsecurity;

import android.content.Context;

/* compiled from: P */
/* loaded from: classes14.dex */
public class QSecConfig {
    public static byte[] CONFIG_KEY_BUF = null;
    public static int CONFIG_KEY_ID = 0;
    public static int CONFIG_TIME_GAP = 0;
    public static final int CONST_CONFIG_TASK_ID = 1;
    public static final int CONST_HEARTBEAT_TASK_ID = 0;
    public static final int CONST_KEYEXCHANGE_TASK_ID = 2;
    public static final int CONST_LUA_TASK_ID = 3;
    public static final int CONST_REPORT_TASK_ID = 4;
    public static final int DO_TYPE_DELE = 4;
    public static final int DO_TYPE_INIT = 3;
    public static final int DO_TYPE_START = 1;
    public static final int DO_TYPE_STOP = 2;
    public static int HEART_BEAT_SEQ_NUM;
    public static String business_guid;
    public static String business_o3did;
    public static int business_os;
    public static String business_q36;
    public static String business_qua;
    public static String business_seed;
    public static String business_uin;
    public static Context sContext;
    public static int sign_strategy;

    static {
        HEART_BEAT_SEQ_NUM = 0;
        CONFIG_TIME_GAP = 5000;
        CONFIG_KEY_ID = 0;
        CONFIG_KEY_BUF = null;
        sContext = null;
        sign_strategy = 0;
        business_os = 1;
    }

    public QSecConfig() {
    }

    public static void setupBusinessInfo(Context context, String uin, String guid, String seed, String str4, String qimei36, String qua) {
        sContext = context;
        business_qua = qua;
        business_uin = uin;
        business_guid = guid;
        //""
        business_seed = seed;
        //""
        business_o3did = str4;
        business_q36 = qimei36;
    }
}
