package com.kiliokuara.kuimivm.vaarg;

import org.objectweb.asm.Type;

import java.util.ArrayList;
import java.util.List;

public enum ShortenType {
    INT('I', Type.INT_TYPE),
    LONG('J', Type.LONG_TYPE),
    DOUBLE('D', Type.DOUBLE_TYPE),
    FLOAT('F', Type.FLOAT_TYPE),
    REF('L', Type.getObjectType("java/lang/Object")),
    VOID('V', Type.VOID_TYPE);

    public final char v;
    public final Type t;

    public static final Type REF_TYPE = REF.t;

    private ShortenType(char v, Type t) {
        this.v = v;
        this.t = t;
    }

    public static ShortenType from(Type type) {
        return switch (type.getSort()) {
            case Type.ARRAY, Type.OBJECT -> ShortenType.REF;
            case Type.BOOLEAN, Type.CHAR, Type.SHORT, Type.INT -> ShortenType.INT;
            case Type.LONG -> ShortenType.LONG;
            case Type.FLOAT -> ShortenType.FLOAT;
            case Type.DOUBLE -> ShortenType.DOUBLE;
            case Type.VOID -> ShortenType.VOID;
            default -> null;
        };
    }

    public static String key(Iterable<ShortenType> type) {
        var sb = new StringBuilder();
        for (var s : type) {
            sb.append(s.v);
        }
        return sb.toString();
    }

    public static List<ShortenType> parse(String input) {
        var rsp = new ArrayList<ShortenType>();
        for (var c : input.toCharArray()) {
            rsp.add(switch (c) {
                case 'I' -> ShortenType.INT;
                case 'J' -> ShortenType.LONG;
                case 'D' -> ShortenType.DOUBLE;
                case 'F' -> ShortenType.FLOAT;
                case 'L' -> ShortenType.REF;
                case 'V' -> ShortenType.VOID;
                default -> throw new IllegalArgumentException("Unknown type " + c);
            });
        }
        return rsp;
    }
}
