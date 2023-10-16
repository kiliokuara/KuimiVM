package com.kiliokuara.kuimivm.execute;

import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.KuimiTestBase;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;

import java.util.List;

public class StackTraceTest extends KuimiTestBase {
    private void dumpFrame(StackTrace stackTrace, int from, int to) {
        to = Math.min(stackTrace.localFramePoint, to);
        for (var i = from + 1; i <= to; i++) {
            var f = stackTrace.localFrameObjectStackStart[i];
            System.out.append("  F ").append(String.valueOf(i)).append(": objectStart=").append(String.valueOf(f));

            var objectEnd = stackTrace.frameObjectPointer;

            var nxt = i + 1;
            if (nxt <= stackTrace.localFramePoint) {
                var nnext = stackTrace.localFrameObjectStackStart[nxt];
                System.out.append(", end=").append(String.valueOf(nnext));

                objectEnd = nnext;
            }
            if (stackTrace.localFrame$hasObjectDeletion.get(i)) {
                System.out.append(", has object deletion");
            }
            System.out.println();

            for (var oi = f + 1; oi <= objectEnd; oi++) {
                System.out.append("    |- [").append(String.valueOf(oi)).append("  ").println(stackTrace.frameObjects[oi]);
            }
        }
    }

    private void dumpStackTrace(String title, StackTrace stackTrace) {
        printTitle(title);

        System.out.append("Stack  point: ").println(stackTrace.stackPoint);
        System.out.append("Frame  point: ").println(stackTrace.localFramePoint);
        System.out.append("Object point: ").println(stackTrace.frameObjectPointer);

        var frameStartIndex = -1;
        for (var i = 0; i <= stackTrace.stackPoint; i++) {
            dumpFrame(stackTrace, frameStartIndex, stackTrace.stackTrace$localFrameStart[i]);
            System.out.println("S " + i + ": " + stackTrace.stackTrace$trace[i] + " - " + stackTrace.stackTrace$localFrameStart[i]);

            frameStartIndex = stackTrace.stackTrace$localFrameStart[i];
        }
        dumpFrame(stackTrace, frameStartIndex, stackTrace.localFramePoint);

    }

    @Test
    void testExecute() {
        var testC = new KuimiClass(vm, Type.getObjectType("TestC"), 0, null, vm.getBaseClass(), null);

        var method = new KuimiMethod(testC, 0, "<clinit>", vm.getPrimitiveClass(Type.VOID_TYPE), List.of());

        var stackTrace = new StackTrace(20, 20, 20);
        stackTrace.enter(method);
        stackTrace.pushFrame();

        stackTrace.enter(method);
        stackTrace.pushFrame();
        stackTrace.pushObject(testC);

        dumpStackTrace("finit", stackTrace);

        stackTrace.popFrame();
        dumpStackTrace("1pop", stackTrace);

        stackTrace.pushFrame();
        stackTrace.pushFrame();
        stackTrace.pushObject(testC);
        stackTrace.pushFrame();
        stackTrace.pushFrame();
        var cvpush = stackTrace.pushObject(testC);
        stackTrace.pushObject(testC);
        stackTrace.pushObject(testC);

        dumpStackTrace("vpush", stackTrace);

        stackTrace.deleteObject(cvpush);
        stackTrace.deleteObject(cvpush);
        dumpStackTrace("deleteObject", stackTrace);
        stackTrace.pushObject(vm.getBaseClass());
        stackTrace.pushObject(vm.getClassClass());
        dumpStackTrace("repush object", stackTrace);


        stackTrace.popFrame();
        stackTrace.popFrame();
        dumpStackTrace("ipop", stackTrace);

        stackTrace.leave();
        dumpStackTrace("met leave", stackTrace);
    }
}
