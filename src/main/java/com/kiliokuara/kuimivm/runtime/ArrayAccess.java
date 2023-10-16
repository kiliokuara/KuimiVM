package com.kiliokuara.kuimivm.runtime;

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiObject;

public class ArrayAccess {
    //@formatter:off
    public static KuimiObject<?>    AALOAD(KuimiObject<?> array, int index) {return ((KuimiObject<?>[])     array.getDelegateInstance())[index];}
    public static byte              BALOAD(KuimiObject<?> array, int index) {return ((byte[])               array.getDelegateInstance())[index];}
    public static char              CALOAD(KuimiObject<?> array, int index) {return ((char[])               array.getDelegateInstance())[index];}
    public static short             SALOAD(KuimiObject<?> array, int index) {return ((short[])              array.getDelegateInstance())[index];}
    public static int               IALOAD(KuimiObject<?> array, int index) {return ((int[])                array.getDelegateInstance())[index];}
    public static long              LALOAD(KuimiObject<?> array, int index) {return ((long[])               array.getDelegateInstance())[index];}
    public static float             FALOAD(KuimiObject<?> array, int index) {return ((float[])              array.getDelegateInstance())[index];}
    public static double            DALOAD(KuimiObject<?> array, int index) {return ((double[])             array.getDelegateInstance())[index];}

    public static void AASTORE(KuimiObject<?> array, int index, KuimiObject<?> value) { ((KuimiObject<?>[]) array.getDelegateInstance())[index] = value;}
    public static void BASTORE(KuimiObject<?> array, int index, byte value          ) { ((byte[])           array.getDelegateInstance())[index] = value;}
    public static void CASTORE(KuimiObject<?> array, int index, char value          ) { ((char[])           array.getDelegateInstance())[index] = value;}
    public static void SASTORE(KuimiObject<?> array, int index, short value         ) { ((short[])          array.getDelegateInstance())[index] = value;}
    public static void IASTORE(KuimiObject<?> array, int index, int value           ) { ((int[])            array.getDelegateInstance())[index] = value;}
    public static void LASTORE(KuimiObject<?> array, int index, long value          ) { ((long[])           array.getDelegateInstance())[index] = value;}
    public static void FASTORE(KuimiObject<?> array, int index, float value         ) { ((float[])          array.getDelegateInstance())[index] = value;}
    public static void DASTORE(KuimiObject<?> array, int index, double value        ) { ((double[])         array.getDelegateInstance())[index] = value;}
    //@formatter:on

    public static KuimiObject<?> newArray(int size, KuimiClass type) {
        return type.arrayType().allocateArray(size);
    }
}
