package tencentlibfekit;

import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.BackendException;
import com.github.unidbg.file.linux.BaseAndroidFileIO;
import com.sun.jna.Pointer;

import java.io.IOException;
import java.io.InputStream;

public class TxtAndroidFileIO extends BaseAndroidFileIO {
    private final InputStream raw;
    private final byte[] buf = new byte[2048];

    public TxtAndroidFileIO(int oflags, InputStream raw) {
        super(oflags);
        this.raw = raw;
    }

    @Override
    public int read(Backend backend, Pointer buffer, int count) {
        try {
            var rl = raw.read(buf, 0, Math.min(count, buf.length));
            if (rl == -1) return 0;

            buffer.write(0, buf, 0, rl);
            return rl;
        } catch (IOException e) {
            throw new BackendException(e);
        }
    }
}
