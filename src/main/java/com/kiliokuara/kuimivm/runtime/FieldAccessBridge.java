package com.kiliokuara.kuimivm.runtime;

import com.kiliokuara.kuimivm.KuimiField;
import com.kiliokuara.kuimivm.KuimiObject;

public class FieldAccessBridge {
    public static KuimiObject<?> getobject(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().objects[field.getObjectIndex()];
    }

    public static boolean getboolean(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.get((int) field.getOffset()) != 0;
    }

    public static byte getbyte(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.get((int) field.getOffset());
    }

    public static short getshort(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getShort((int) field.getOffset());
    }

    public static char getchar(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getChar((int) field.getOffset());
    }

    public static int getint(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getInt((int) field.getOffset());
    }

    public static long getlong(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getLong((int) field.getOffset());
    }

    public static float getfloat(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getFloat((int) field.getOffset());
    }

    public static double getdouble(KuimiObject<?> object, KuimiField field) {
        return object.memoryView().buffer.getDouble((int) field.getOffset());
    }

    //

    public static void put(KuimiObject<?> thiz, byte val, KuimiField field) {
        thiz.memoryView().buffer.put((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, short val, KuimiField field) {
        thiz.memoryView().buffer.putShort((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, char val, KuimiField field) {
        thiz.memoryView().buffer.putChar((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, int val, KuimiField field) {
        thiz.memoryView().buffer.putInt((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, long val, KuimiField field) {
        thiz.memoryView().buffer.putLong((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, boolean val, KuimiField field) {
        thiz.memoryView().buffer.put((int) field.getOffset(), val ? (byte) 1 : 0);
    }

    public static void put(KuimiObject<?> thiz, float val, KuimiField field) {
        thiz.memoryView().buffer.putFloat((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, double val, KuimiField field) {
        thiz.memoryView().buffer.putDouble((int) field.getOffset(), val);
    }

    public static void put(KuimiObject<?> thiz, KuimiObject<?> val, KuimiField field) {
        thiz.memoryView().objects[field.getObjectIndex()] = val;
    }


    //

    public static void put(byte val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(short val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(char val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(int val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(long val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(boolean val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(float val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(double val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

    public static void put(KuimiObject<?> val, KuimiField field) {
        put(field.getDeclaredClass(), val, field);
    }

}
