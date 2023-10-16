package com.tencent.mobileqq.channel;

import java.util.ArrayList;
import java.util.Random;

/* compiled from: P */
/* loaded from: classes10.dex */
public class ChannelReport {
    private static final String EVENT_TYPE_CLICK = "click";
    private static final String EVENT_TYPE_EXPOSURE = "exposure";
    private static final String EVENT_TYPE_STEP = "step";
    private static final String REPORT_CMD = "trpc.o3.report.Report.SsoEventReport";
    private static final ChannelReport mInstance = new ChannelReport();

    public static ChannelReport getInstance() {
        return mInstance;
    }

    private void commonReport(String type, String value, String additional) {
        System.out.println("commonReport type=" + type + " value=" + value + " additional=" + additional);
        /*
       ReportReq reportReq = new ReportReq();
        reportReq.attaId = "0df00071646";
        SingleData singleData = new SingleData();
        singleData.data = new String[]{QSecConfig.business_qua,
                "6.2.221",
                type,
                value,
                Build.VERSION.RELEASE,
                Build.BRAND + DeviceInfoMonitor.getModel(),
                additional,
                "",
                QSecConfig.business_guid,
                QSecConfig.business_q36
        };
        reportReq.singleDatas = new SingleData[]{singleData};
        ChannelManager.getInstance().sendMessage("trpc.o3.report.Report.SsoReport", MessageNano.toByteArray(reportReq), -1L);
    */
    }

    private void eventReport(ArrayList<String> arrayList) {
        System.out.println("eventReport arrayList=" + arrayList);
/*
        String version = "6.2.221";//"6.2.221"
        ReportReq reportReq = new ReportReq();
        reportReq.attaId = "0df00071646";
        ArrayList<String> arrayList2 = new ArrayList<>();
        arrayList2.add(QSecConfig.business_qua);
        arrayList2.add(version);
        arrayList2.addAll(arrayList);
        SingleData singleData = new SingleData();
        singleData.data = arrayList2.toArray(new String[0]);
        reportReq.singleDatas = new SingleData[]{singleData};
        //ChannelManager.getInstance().sendMessage("trpc.o3.report.Report.SsoEventReport", MessageNano.toByteArray(reportReq), -1L);
    */
    }

    public void reportClick(String str, String str2, float f, float f2) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("click");
        arrayList.add(str);
        arrayList.add(str2);
        arrayList.add(f + "|" + f2);
        eventReport(arrayList);
    }

    public void reportExposure(String str, String str2) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("exposure");
        arrayList.add(str);
        arrayList.add(str2);
        eventReport(arrayList);
    }

    public void reportInitTime() {
        long initTimeMS = new Random().nextInt(0, 180);
        int nextInt = new Random().nextInt(10);
        if (initTimeMS >= 200 || nextInt == 0) {
            //FEKit.getInstance().sleepCheckResult->"0"
            commonReport("init_time", String.valueOf(initTimeMS), "0");
        }
    }

    public void reportLoadSo() {
        //C81896b.m224165d() ? 2 : C81897c.m224152c().m224149f()
        //mmKVValue SafeMode_Value_Key_20220909 boolean true->2 false->isSoLoaded
        //isSoLoaded true->1 false->0 (jvm boolean)
        int status = 1;
        int nextInt = new Random().nextInt(10);
        if (status != 1 || nextInt == 0) {
            commonReport("load_so", String.valueOf(status), "");
        }
    }

    public void reportStep(String str) {
        ArrayList<String> arrayList = new ArrayList<>();
        arrayList.add("exposure");
        arrayList.add(str);
        eventReport(arrayList);
    }
}