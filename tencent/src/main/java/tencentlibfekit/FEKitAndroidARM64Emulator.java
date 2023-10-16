package tencentlibfekit;

import com.github.unidbg.Emulator;
import com.github.unidbg.arm.backend.Backend;
import com.github.unidbg.arm.backend.BackendFactory;
import com.github.unidbg.arm.context.RegisterContext;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.ARM64SyscallHandler;
import com.github.unidbg.linux.android.AndroidARM64Emulator;
import com.github.unidbg.memory.SvcMemory;
import com.github.unidbg.unix.UnixEmulator;
import com.github.unidbg.unix.UnixSyscallHandler;
import com.sun.jna.Pointer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.util.Collection;

public class FEKitAndroidARM64Emulator extends AndroidARM64Emulator {
    protected FEKitAndroidARM64Emulator(String processName, File rootDir, Collection<BackendFactory> backendFactories) {
        super(processName, rootDir, backendFactories);
    }

    @Override
    protected UnixSyscallHandler<AndroidFileIO> createSyscallHandler(SvcMemory svcMemory) {
        return new FEKitARM64SyscallHandler(svcMemory);
    }


    public static class FEKitARM64SyscallHandler extends ARM64SyscallHandler {
        private static final Log log = LogFactory.getLog(ARM64SyscallHandler.class);

        public FEKitARM64SyscallHandler(SvcMemory svcMemory) {
            super(svcMemory);
        }
        long lastHookNanoTime =System.nanoTime();
        @Override
        public void hook(Backend backend, int intno, int swi, Object user) {
            super.hook(backend, intno, swi, user);
            lastHookNanoTime =System.nanoTime();
        }
        //Temporary solution
        @Override
        protected int clock_gettime(Emulator<?> emulator) {
            RegisterContext context = emulator.getContext();
            int clk_id = context.getIntArg(0);
            Pointer tp = context.getPointerArg(1);
            long offset = Math.abs(System.nanoTime() - lastHookNanoTime);
            long tv_sec = offset / 1000000000L;
            long tv_nsec = offset % 1000000000L;
            if (clk_id == 3){
                tp.setLong(0, tv_sec);
                tp.setLong(8, tv_nsec);
                return 0;
            }else return super.clock_gettime(emulator);
        }
        @Override
        protected long fork(Emulator<?> emulator) {
            log.info("fork");
            emulator.getMemory().setErrno(UnixEmulator.EPERM);
            return -1;
        }
    }
}
