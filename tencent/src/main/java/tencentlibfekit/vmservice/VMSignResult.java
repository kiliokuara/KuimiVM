package tencentlibfekit.vmservice;

import java.util.HexFormat;

public class VMSignResult {
    public byte[] extra;
    public byte[] sign;
    public byte[] token;

    private static void appendByteArray(StringBuilder sb, byte[] barr) {
        if (barr == null) {
            sb.append("null");
        } else {
            sb.append("byte[").append(barr.length).append("] ");
            HexFormat.ofDelimiter(" ").withUpperCase().formatHex(sb, barr);
        }
    }

    @Override
    public String toString() {
        var sb = new StringBuilder();
        sb.append("SignResult[");
        appendByteArray(sb.append("\n  extra = "), extra);
        appendByteArray(sb.append("\n  sign  = "), sign);
        appendByteArray(sb.append("\n  token = "), token);
        sb.append("\n]");
        return sb.toString();
    }
}
