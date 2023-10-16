package tencentlibfekit.proto;

import com.google.gson.annotations.SerializedName;

public class DeviceInfoProto {
    @SerializedName("display")
    public byte[] display;

    @SerializedName("product")
    public byte[] product;

    @SerializedName("device")
    public byte[] device;

    @SerializedName("board")
    public byte[] board;

    @SerializedName("brand")
    public byte[] brand;

    @SerializedName("model")
    public byte[] model;

    @SerializedName("bootloader")
    public byte[] bootloader;

    @SerializedName("fingerprint")
    public byte[] fingerprint;

    @SerializedName("bootId")
    public byte[] bootId;

    @SerializedName("procVersion")
    public byte[] procVersion;

    @SerializedName("baseBand")
    public byte[] baseBand;

    @SerializedName("version")
    public Version version;

    @SerializedName("simInfo")
    public byte[] simInfo;

    @SerializedName("osType")
    public byte[] osType;

    @SerializedName("macAddress")
    public byte[] macAddress;

    @SerializedName("wifiBSSID")
    public byte[] wifiBSSID;
    @SerializedName("wifiSSID")
    public byte[] wifiSSID;

    @SerializedName("imsiMd5")
    public byte[] imsiMd5;
    @SerializedName("imei")
    public String imei;
    @SerializedName("apn")
    public byte[] apn;
    @SerializedName("androidId")
    public byte[] androidId;
    @SerializedName("guid")
    public byte[] guid;


    public static class Version {
        @SerializedName("incremental")
        public byte[] incremental;

        @SerializedName("release")
        public byte[] release;

        @SerializedName("codename")
        public byte[] codename;

        @SerializedName("sdk")
        public int sdk;
    }
}
