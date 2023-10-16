package com.tencent.mobileqq.channel;

import java.util.ArrayList;

public class ChannelManager {
    public static final ChannelManager INSTANCE = new ChannelManager();
    private static ChannelProxy mProxy;

    public static ChannelManager getInstance() {
        return INSTANCE;
    }

    public void init(ChannelProxy proxy) { mProxy = proxy; }

    public native void initReport(String str, String str2, String str3, String str4, String str5, String str6);
//    public native ArrayList<String> getCmdWhiteList();

    public native void onNativeReceive(String str, byte[] barr, boolean z, long j);

    public native void sendMessageTest();

    public native void setChannelProxy(ChannelProxy proxy);

    public native ArrayList<String> getCmdWhiteList();
}
