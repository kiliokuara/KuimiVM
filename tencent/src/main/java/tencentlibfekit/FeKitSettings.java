package tencentlibfekit;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class FeKitSettings {
    public Properties buildProps = new Properties();
    public Properties sysProps = new Properties();
    public Map<String, String> mmkv = new HashMap<>();
    public byte[] bootId;

    public String mmkv(String key) {
        return mmkv.get(key);
    }

    public void mmkvSet(String key, String value) {
        mmkv.put(key, value);
    }

    public String getBuildProp(String key) {
        return buildProps.getProperty(key);
    }

    public String getSystemProp(String key) {
        return sysProps.getProperty(key);
    }

    public String getAppVersionName(String key) {
        return "8.9.58";
    }

    public String getAppVersionCode(String key) {
        return "4106";
    }

    public String getAppInstallTime(String key) {
        return "1687305773000";
    }

    public String getDensity() {
        return "3.6";
    }

    public String getFontDpi() {
        return "3.6";
    }

    public String getIME() {
        return "keyboard";
    }

    public String getScreenSize() {
        return "[1440,2858]";
    }

    public String getOaid() {
        return mmkv.get("DeviceToken-oaid-V001");
    }

    public String getBSSID() {
        return mmkv.get("DeviceToken-wifissid-V001");
    }

    public String getAndroidID() {
        return mmkv.get("DeviceToken-ANDROID-ID-V001");
    }

    public String getStorage() {
        return String.valueOf(1024L * 1024L * 1024L * 128L);
    }
}
