package kfc.vivo50.code45;

import java.io.File;

//Modified by packer
public class ServiceSelector {
    static ConsoleServiceFactory get(File apkFile, File libFekit) {
        return new ConsoleServiceFactory.LocalTest(apkFile, libFekit);
    }

    public static String mainClass() {
        return "";
    }

    public static String resourceSha1() {
        return "";
    }

    public static boolean isLocal() {
        return true;
    }

    static boolean doRpc() {
        return false;
    }

    public static String commitHash() {
        return "dev";
    }

    public static long buildTime() {
        return System.currentTimeMillis();
    }
}
