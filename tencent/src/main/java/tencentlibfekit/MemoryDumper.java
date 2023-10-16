package tencentlibfekit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;

public class MemoryDumper {
    private static final Logger LOGGER = LoggerFactory.getLogger(MemoryDumper.class);
    public static void dump() {
        var factory = ManagementFactory.getMemoryMXBean();
        var lines = new CharSequence[][]{
                {"committed", "init", "used", "max"},
                render(factory.getHeapMemoryUsage()),
                render(factory.getNonHeapMemoryUsage()),
        };

        int[] widths = new int[4];
        for (var i = 0; i < 4; i++) {
            for (var subl : lines) {
                widths[i] = Math.max(widths[i], subl[i].length());
            }
        }

        try {
            var sb = new StringBuilder();
            synchronized (LOGGER) {
                sb.append("                 ");
                lfRender(sb, lines[0], widths);
                LOGGER.debug(sb.toString());

                sb = new StringBuilder();
                sb.append("    Heap Memory: ");
                lfRender(sb, lines[1], widths);
                LOGGER.debug(sb.toString());

                sb = new StringBuilder();
                sb.append("Non-Heap Memory: ");
                lfRender(sb, lines[2], widths);
                LOGGER.debug(sb.toString());
            }
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
    }

    private static void lfRender(Appendable appendable, CharSequence[] sv, int[] wds) throws IOException {
        for (var i = 0; i < 4; i++) {
            if (i != 0) {
                appendable.append(" | ");
            }
            renderMUNum(appendable, wds[i], sv[i]);
        }
    }

    static CharSequence[] render(MemoryUsage memoryUsage) {
        return new CharSequence[]{
                renderMemoryUsageNumber(memoryUsage.getCommitted()),
                renderMemoryUsageNumber(memoryUsage.getInit()),
                renderMemoryUsageNumber(memoryUsage.getUsed()),
                renderMemoryUsageNumber(memoryUsage.getMax()),
        };
    }

    private static final long MEM_B = 1024L;
    private static final long MEM_KB = 1024L << 10;
    private static final long MEM_MB = 1024L << 20;
    private static final long MEM_GB = 1024L << 30;


    private static StringBuilder appendDouble(StringBuilder sb, double number) {
        return sb.append(Math.floor(number * 100) / 100);
    }

    private static StringBuilder renderMemoryUsageNumber(long num) {
        var sb = new StringBuilder();
        renderMemoryUsageNumber(sb, num);
        return sb;
    }

    private static void renderMemoryUsageNumber(StringBuilder builder, long num) {
        if (num == -1L) {
            builder.append(num);
            return;
        }
        if (num < MEM_B) {
            builder.append(num).append("B");
            return;
        }
        if (num < MEM_KB) {
            appendDouble(builder, num / 1024.0).append("KB");
            return;
        }
        if (num < MEM_MB) {
            appendDouble(builder, (num >>> 10) / 1024.0).append("MB");
            return;
        }
        appendDouble(builder, (num >>> 20) / 1024.0).append("GB");

    }

    private static final String emptyLine = "    ".repeat(10);

    private static void emptyLine(Appendable sb, int size) throws IOException {
        while (size > 0) {
            var apd = Math.min(size, emptyLine.length());
            sb.append(emptyLine, 0, apd);
            size -= apd;
        }
    }

    private static void renderMUNum(Appendable sb, int contentLen, CharSequence code) throws IOException {

        var padding = contentLen - code.length();
        var left = padding / 2;
        var right = padding - left;
        emptyLine(sb, left);
        sb.append(code);
        emptyLine(sb, right);
    }

}
