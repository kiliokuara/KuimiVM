package tencentlibfekit;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.EmulatorBuilder;

public class FEKitAndroidEmulatorBuilder extends EmulatorBuilder<AndroidEmulator> {

    public static FEKitAndroidEmulatorBuilder for64Bit() {
        return new FEKitAndroidEmulatorBuilder();
    }

    protected FEKitAndroidEmulatorBuilder() {
        super(true);
    }

    @Override
    public AndroidEmulator build() {
        return new FEKitAndroidARM64Emulator(processName, rootDir, backendFactories);
    }

}
