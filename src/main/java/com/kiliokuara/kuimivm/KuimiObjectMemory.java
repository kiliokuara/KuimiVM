package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.attributes.AttributeKey;

import java.nio.ByteBuffer;

/**
 * Object fields' memory
 */
public class KuimiObjectMemory {
    public static final AttributeKey<KuimiObject<?>, KuimiObjectMemory> ATTRIBUTE_KEY = new AttributeKey<>("field-table-memory", f -> {
        if (f instanceof KuimiClass c) {
            return new KuimiObjectMemory(c.getFieldTable(), true);
        } else {
            return new KuimiObjectMemory(f.getObjectClass().getFieldTable(), false);
        }
    });

    public final ByteBuffer buffer;
    public final KuimiObject<?>[] objects;

    public KuimiObjectMemory(KuimiFieldTable table, boolean isStatic) {
        buffer = ByteBuffer.allocate(
                (int) (isStatic ? table.staticFieldsSize : table.objectFieldsSize)
        );
        objects = new KuimiObject[isStatic ? table.staticObjectCount : table.objectCount];
    }
}
