package com.tencent.mobileqq.dt.app;

import android.content.Context;

public class Dtc {

    public static native String mmKVValue(String str);

    public static native String getPropSafe(String key);

    public static native String systemGetSafe(String key);

    public static native String getAppVersionName(String key);

    public static native String getAppVersionCode(String key);

    public static native String getAppInstallTime(String key);

    public static native String getAndroidID(String key);

    public static native String getCMC(String key);

    public static native String getDensity(String key);

    public static native String getFontDpi(String key);

    public static native String getIME(String key);

    public static native String getLibraryList(String key);

    public static native String getScreenSize(String key);

    public static native String getOaid();

    public static native Context getContext();

    public static native String getBSSID(Context key);

    public static native String getStorage(String key);

    public static native void mmKVSaveValue(String str, String str2);

    public static void saveList(String str, String str2, String str3, String str4, String str5, String str6, String str7) {
    }
}
