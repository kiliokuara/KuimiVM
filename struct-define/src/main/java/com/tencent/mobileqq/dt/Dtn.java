package com.tencent.mobileqq.dt;

import android.content.Context;
import com.tencent.mobileqq.fe.IFEKitLog;

public class Dtn {

    public native void initContext(Context context);

    public native void initLog(IFEKitLog iFEKitLog);

    public native void initUin(String str);

    public static final Dtn INSTANCE = new Dtn();

    public static Dtn getInstance() { return INSTANCE; }
}
