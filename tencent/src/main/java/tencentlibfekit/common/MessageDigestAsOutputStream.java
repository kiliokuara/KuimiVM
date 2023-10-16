package tencentlibfekit.common;

import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.OutputStream;
import java.security.MessageDigest;

public class MessageDigestAsOutputStream extends OutputStream {
    private final MessageDigest md;

    public MessageDigestAsOutputStream(MessageDigest md) {
        this.md = md;
    }

    @Override
    public void write(int b) throws IOException {
        md.update((byte) b);
    }

    @Override
    public void write(@NotNull byte[] b, int off, int len) throws IOException {
        md.update(b, off, len);
    }
}
