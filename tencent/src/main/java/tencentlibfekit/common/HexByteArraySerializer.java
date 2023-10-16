package tencentlibfekit.common;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.util.HexFormat;

public class HexByteArraySerializer extends TypeAdapter<byte[]> {
    public static final HexByteArraySerializer INSTANCE = new HexByteArraySerializer();

    public static String hex(byte[] value) {
        if (value == null) return null;
        return HexFormat.of().formatHex(value);
    }

    @Override
    public void write(JsonWriter out, byte[] value) throws IOException {
        out.value(hex(value));
    }

    @Override
    public byte[] read(JsonReader in) throws IOException {
        if (in.peek() == JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        return HexFormat.of().parseHex(in.nextString());
    }
}
