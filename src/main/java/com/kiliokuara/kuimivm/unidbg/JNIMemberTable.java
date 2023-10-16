package com.kiliokuara.kuimivm.unidbg;

import com.kiliokuara.kuimivm.KuimiField;
import com.kiliokuara.kuimivm.KuimiMember;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.attributes.AttributeKey;

import java.util.Arrays;

class JNIMemberTable {
    private static final int BASE_OFFSET = 0x180;

    private final AttributeKey<? super KuimiMember, SlotRecord> SLOT_RECORD = new AttributeKey<>("slot-record", it -> new SlotRecord(push(it)));

    private KuimiMember[] members = new KuimiMember[256];
    private volatile int pushSlotStart;

    public KuimiMember resolve(int mid) {
        if (mid < BASE_OFFSET) return null;
        return members[mid - BASE_OFFSET];
    }

    public synchronized int push(KuimiMember member) {
        if (member == null) return 0;

        var mems = members;
        for (int i = pushSlotStart, ed = mems.length; i < ed; i++) {
            if (mems[i] == null) {
                pushSlotStart = i + 1;
                mems[i] = member;
                return i + BASE_OFFSET;
            }
        }

        members = Arrays.copyOf(mems, mems.length + 256);
        return push(member);
    }

    public synchronized void drop(int slot) {
        slot -= BASE_OFFSET;

        var old = members[slot];
        members[slot] = null;
        if (old != null) {
            old.getAttributeMap().remove(SLOT_RECORD);
        }
    }

    public int getMemberId(KuimiMember member) {
        return member.getAttributeMap().attribute(SLOT_RECORD).slot;
    }

    public KuimiMethod resolveMethodId(int peer) {
        return (KuimiMethod) resolve(peer);
    }
    public KuimiField resolveFieldId(int peer){
        return (KuimiField) resolve(peer);
    }

    record SlotRecord(int slot) {
    }
}
