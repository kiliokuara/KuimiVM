package tencentlibfekit.vmservice;

import com.github.unidbg.spi.AbstractLoader;
import io.github.karlatemp.unsafeaccessor.Root;

import java.lang.invoke.VarHandle;

public class UnidbgHelper {
    static final VarHandle AbstractLoader$mmapBaseAddress;

    static {
        var lk = Root.getTrusted(AbstractLoader.class);
        try {
            AbstractLoader$mmapBaseAddress = lk.findVarHandle(AbstractLoader.class, "mmapBaseAddress", long.class);
        } catch (Throwable throwable) {
            throw new ExceptionInInitializerError(throwable);
        }
    }
}
