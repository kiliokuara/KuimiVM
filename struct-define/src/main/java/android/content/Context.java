package android.content;

import android.content.pm.ApplicationInfo;

import java.io.File;

public class Context {
    public ApplicationInfo getApplicationInfo() {
        return ApplicationInfo.getInstance();
    }

    public ContentResolver getContentResolver() {
        return ContentResolver.getInstance();
    }
    public String getPackageName(){
        return "com.tencent.mobileqq";
    }
    public String getPackageResourcePath(){
        return "/data/app/com.tencent.mobileqq/base.apk";
    }
    public File getFilesDir(){
        return new File("/data/user/0/com.tencent.mobile.qq/files");
    }
}
