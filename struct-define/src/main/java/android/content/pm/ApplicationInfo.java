package android.content.pm;

public class ApplicationInfo extends PackageItemInfo{
   static ApplicationInfo instance= new ApplicationInfo();
    public int targetSdkVersion = 26;
    public String nativeLibraryDir = "/data/app/com.tencent.mobileqq/base.apk!/lib/arm64-v8a";
    public static ApplicationInfo getInstance() {
        return instance;
    }
}
