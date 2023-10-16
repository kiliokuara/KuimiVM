package com.kiliokuara.kuimivm;

import com.kiliokuara.kuimivm.abstractvm.KuimiAbstractVM;
import org.junit.jupiter.api.BeforeEach;
import org.objectweb.asm.Type;

public class KuimiTestBase {
    private static final String titleHeaderLine = "=".repeat(200);

    public static int printTitle(String title) {

        var sb = new StringBuilder();
        sb.append(" [ ").append(title).append(" ] ");

        var titleSize = Math.max(sb.length(), 80);
        var padding = titleSize - sb.length();
        var lpadding = padding / 2;

        sb.insert(0, titleHeaderLine, 0, lpadding);
        sb.append(titleHeaderLine, 0, padding - lpadding);

        System.out.println(sb);
        return titleSize;
    }

    public static void printFooter(int len) {
        while (len > 0) {
            var ps = Math.min(titleHeaderLine.length(), len);
            len -= ps;
            System.out.append(titleHeaderLine, 0, ps);
        }
        System.out.println();
    }


    protected KuimiVM vm;

    @BeforeEach
    void initVM() {
        vm = new KuimiAbstractVM();
    }

    protected KuimiClass resolveClass(Type type) {
        if (type.getSort() == Type.ARRAY || type.getSort() == Type.OBJECT) {
            return vm.resolveClass(type);
        }
        return vm.getPrimitiveClass(type);
    }
}
