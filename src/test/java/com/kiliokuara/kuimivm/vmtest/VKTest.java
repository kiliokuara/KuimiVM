package com.kiliokuara.kuimivm.vmtest;

import com.github.unidbg.Module;
import com.github.unidbg.arm.backend.Unicorn2Factory;
import com.github.unidbg.linux.android.AndroidEmulatorBuilder;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.ElfLibraryRawFile;
import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.unidbg.KuimiJniMethodHandle;
import com.kiliokuara.kuimivm.unidbg.KuimiUnidbgVM64;
import com.kiliokuara.kuimivm.abstractvm.KuimiAbstractVM;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import unicorn.UnicornConst;

import java.io.FileInputStream;
import java.security.SecureRandom;
import java.util.List;

public class VKTest {
    public static void main(String[] args) throws Throwable {
        var emulator = AndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.tencent.mobileqq")
                .addBackendFactory(new Unicorn2Factory(true))
                .build();


        var random = new SecureRandom();

        var memory = emulator.getMemory();
        memory.mmap(0x64812448, UnicornConst.UC_PROT_READ | UnicornConst.UC_PROT_WRITE);
        memory.setLibraryResolver(new AndroidResolver(23));

        var data = new FileInputStream("A:\\resources\\lib\\arm64-v8a/libfekit.so").readAllBytes();

        Module module = emulator.getMemory().load(new ElfLibraryRawFile("libfekit", data, emulator.is64Bit()), false);

        var vm = new KuimiAbstractVM();
        var univm = new KuimiUnidbgVM64(vm, emulator) {
            @Override
            protected KuimiClass resolve1(StackTrace st, Type type) {
                var vms = vm.resolveClass(type);
                if (vms == null) {
                    var arrx = type.getSort();
                    if (arrx == Type.ARRAY) {
                        var res = resolveClass(st, type.getElementType());
                        if (res == null) return null;

                        return vm.resolveClass(type);
                    }
                    if (type.getInternalName().equals("com/tencent/mobileqq/dt/Dc")) return null;

                    var dyn = new KuimiClass(vm, type, Opcodes.ACC_PUBLIC, null, vm.getBaseClass(), null);

                    ((KuimiAbstractVM) vm).getBootstrapPool().put(dyn);

                    return dyn;
                }
                return vms;
            }
        };
        univm.verbose = true;

        vm.attachThread(new StackTrace(2048, 2048, 2048));

        {
            var onLoad = module.findSymbolByName("JNI_OnLoad", false);
            System.out.println("OOL: " + onLoad);
            if (onLoad != null) {
                System.out.println("RSP: " + onLoad.call(emulator, univm.getJavaVM(), null));
            }
        }

        {
            var vh = new KuimiMethod(vm.getBaseClass(), 0, "test", vm.getPrimitiveClass(Type.VOID_TYPE), List.of(vm.getClassClass(), vm.getPrimitiveClass(Type.INT_TYPE)));
            System.out.println(vh.flattenMethodHandleType());
            var nativeExec = KuimiJniMethodHandle.invoker(vh, univm, 0xa4a432ecL);
            System.out.println(nativeExec);

            //nativeExec.invokeExact((KuimiVM) vm, vm.getStackTrace(), (KuimiObject<?>) vm.getBaseClass(), (KuimiObject<?>) vm.getClassClass(), 114);

        }
    }
}
