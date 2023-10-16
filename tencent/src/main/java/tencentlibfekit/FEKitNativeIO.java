package tencentlibfekit;

import com.github.unidbg.Emulator;
import com.github.unidbg.file.FileResult;
import com.github.unidbg.file.IOResolver;
import com.github.unidbg.file.linux.AndroidFileIO;
import com.github.unidbg.linux.file.ByteArrayFileIO;
import com.github.unidbg.linux.file.DirectoryFileIO;
import com.github.unidbg.linux.file.SimpleFileIO;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class FEKitNativeIO implements IOResolver<AndroidFileIO> {
    private static byte[] render(String s) {
        return (s.strip() + '\n').replaceAll("\\=+", "").getBytes();
    }

    private static final byte[] CPUINFO = render("""
            processor       : 0
            vendor_id       : GenuineIntel
            cpu family      : 6
            model           : 151
            model name      : 12th Gen Intel(R) Core(TM) i9-12900K
            stepping        : 2
            cpu MHz         : 3187.200
            cache size      : 30720 KB
            physical id     : 0
            siblings        : 4
            core id         : 0
            cpu cores       : 4
            apicid          : 0
            initial apicid  : 0
            fpu             : yes
            fpu_exception   : yes
            cpuid level     : 22
            wp              : yes
            flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx rdtscp lm constant_tsc rep_good nopl xtopology nonstop_tsc pni pclmulqdq ssse3 cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt aes xsave avx rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ibrs ibpb stibp fsgsbase avx2 invpcid rdseed clflushopt arch_capabilities
            bugs            : spectre_v1 spectre_v2 spec_store_bypass
            bogomips        : 6376.00
            clflush size    : 64
            cache_alignment : 64
            address sizes   : 46 bits physical, 48 bits virtual
            power management:
            ====
            processor       : 1
            vendor_id       : GenuineIntel
            cpu family      : 6
            model           : 151
            model name      : 12th Gen Intel(R) Core(TM) i9-12900K
            stepping        : 2
            cpu MHz         : 3187.200
            cache size      : 30720 KB
            physical id     : 0
            siblings        : 4
            core id         : 1
            cpu cores       : 4
            apicid          : 1
            initial apicid  : 1
            fpu             : yes
            fpu_exception   : yes
            cpuid level     : 22
            wp              : yes
            flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx rdtscp lm constant_tsc rep_good nopl xtopology nonstop_tsc pni pclmulqdq ssse3 cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt aes xsave avx rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ibrs ibpb stibp fsgsbase avx2 invpcid rdseed clflushopt arch_capabilities
            bugs            : spectre_v1 spectre_v2 spec_store_bypass
            bogomips        : 6376.00
            clflush size    : 64
            cache_alignment : 64
            address sizes   : 46 bits physical, 48 bits virtual
            power management:
            ====
            processor       : 2
            vendor_id       : GenuineIntel
            cpu family      : 6
            model           : 151
            model name      : 12th Gen Intel(R) Core(TM) i9-12900K
            stepping        : 2
            cpu MHz         : 3187.200
            cache size      : 30720 KB
            physical id     : 0
            siblings        : 4
            core id         : 2
            cpu cores       : 4
            apicid          : 2
            initial apicid  : 2
            fpu             : yes
            fpu_exception   : yes
            cpuid level     : 22
            wp              : yes
            flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx rdtscp lm constant_tsc rep_good nopl xtopology nonstop_tsc pni pclmulqdq ssse3 cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt aes xsave avx rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ibrs ibpb stibp fsgsbase avx2 invpcid rdseed clflushopt arch_capabilities
            bugs            : spectre_v1 spectre_v2 spec_store_bypass
            bogomips        : 6376.00
            clflush size    : 64
            cache_alignment : 64
            address sizes   : 46 bits physical, 48 bits virtual
            power management:
            ====
            processor       : 3
            vendor_id       : GenuineIntel
            cpu family      : 6
            model           : 151
            model name      : 12th Gen Intel(R) Core(TM) i9-12900K
            stepping        : 2
            cpu MHz         : 3187.200
            cache size      : 30720 KB
            physical id     : 0
            siblings        : 4
            core id         : 3
            cpu cores       : 4
            apicid          : 3
            initial apicid  : 3
            fpu             : yes
            fpu_exception   : yes
            cpuid level     : 22
            wp              : yes
            flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx rdtscp lm constant_tsc rep_good nopl xtopology nonstop_tsc pni pclmulqdq ssse3 cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt aes xsave avx rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ibrs ibpb stibp fsgsbase avx2 invpcid rdseed clflushopt arch_capabilities
            bugs            : spectre_v1 spectre_v2 spec_store_bypass
            bogomips        : 6376.00
            clflush size    : 64
            cache_alignment : 64
            address sizes   : 46 bits physical, 48 bits virtual
            power management:
            ====
            """
    ), MEMINFO = render("""
            MemTotal:        8163540 kB
            MemFree:         7050460 kB
            MemAvailable:    7341592 kB
            Buffers:           49028 kB
            Cached:           275412 kB
            SwapCached:            0 kB
            Active:           756568 kB
            Inactive:         156984 kB
            Active(anon):     592116 kB
            Inactive(anon):      376 kB
            Active(file):     164452 kB
            Inactive(file):   156608 kB
            Unevictable:        3088 kB
            Mlocked:            3088 kB
            SwapTotal:             0 kB
            SwapFree:              0 kB
            Dirty:                96 kB
            Writeback:             0 kB
            AnonPages:        592288 kB
            Mapped:           229148 kB
            Shmem:               824 kB
            Slab:             140924 kB
            SReclaimable:      87136 kB
            SUnreclaim:        53788 kB
            KernelStack:       18720 kB
            PageTables:        24136 kB
            NFS_Unstable:          0 kB
            Bounce:                0 kB
            WritebackTmp:          0 kB
            CommitLimit:     4081768 kB
            Committed_AS:   20566976 kB
            VmallocTotal:   34359738367 kB
            VmallocUsed:           0 kB
            VmallocChunk:          0 kB
            CmaTotal:              0 kB
            CmaFree:               0 kB
            HugePages_Total:       0
            HugePages_Free:        0
            HugePages_Rsvd:        0
            HugePages_Surp:        0
            Hugepagesize:       2048 kB
            DirectMap4k:       36800 kB
            DirectMap2M:     8351744 kB
            """
    ), BOOTID = render("406f2641-49c0-497c-8cff-50bffb0c947d");

    private final FeKitSettings settings;
    public final FEKitNativeIOFiles nativeIOFiles;

    public FEKitNativeIO(FeKitSettings settings, FEKitNativeIOFiles nativeIOFiles) {
        this.settings = settings;
        if (settings.bootId == null) {
            settings.bootId = BOOTID;
        }
        this.nativeIOFiles = nativeIOFiles;
    }

    @Override
    public FileResult<AndroidFileIO> resolve(Emulator<AndroidFileIO> emulator, String pathname, int oflags) {
        System.out.println("[Native IO       ] read: " + pathname + ", oflags: " + oflags);

        if (pathname.equals("/proc/cpuinfo")) {
            return FileResult.success(new ByteArrayFileIO(oflags, pathname, CPUINFO));
        }
        if (pathname.equals("/proc/meminfo")) {
            return FileResult.success(new ByteArrayFileIO(oflags, pathname, MEMINFO));
        }
        if (pathname.equals("/proc/sys/kernel/random/boot_id")) {
            return FileResult.success(new ByteArrayFileIO(oflags, pathname, settings.bootId));
        }
        //QQ apk
        if (pathname.equals("/data/app/com.tencent.mobileqq/base.apk")) {
            return FileResult.success(new SimpleFileIO(oflags, nativeIOFiles.apkFile, pathname));
        }
        //x86转译arm指令的so
        if (pathname.equals("/system/lib/libhoudini.so") || pathname.equals("/system/lib64/libhoudini.so")) {
            //ENOENT
            return FileResult.failed(2);
        }
        if (pathname.startsWith("/data/user/")) {
            if (!(pathname.equals("/data/user/0"))) {
                //ENOENT
                return FileResult.failed(2);
            } else {
                // note: 11 = sizeof("/data/user/")

                var path = nativeIOFiles.userData.toPath().resolve(pathname.substring(11));
                if (Files.isDirectory(path)) {
                    return FileResult.success(new DirectoryFileIO(oflags, pathname, path.toFile()));
                } else if (Files.isRegularFile(path)) {
                    return FileResult.success(new SimpleFileIO(oflags, path.toFile(), pathname));
                } else return FileResult.failed(2);
            }
        }
        //处理检测，这三个文件已在库中包含
        if (pathname.equals("/system/bin/sh") || pathname.equals("/system/lib/libc.so") || pathname.equals("/system/bin/ls")) {
            return FileResult.success(new SimpleFileIO(oflags, new File(nativeIOFiles.linuxFiles, pathname.split("/")[3]), pathname));
        }
        if (pathname.equals("/data/app/com.tencent.mobileqq/base.apk!/lib/arm64-v8a/libfekit.so")) {
            return FileResult.success(new SimpleFileIO(oflags, nativeIOFiles.soFile, pathname));
        }
        if (pathname.equals("/proc/stat/cmdline")) {
            return FileResult.success(new ByteArrayFileIO(oflags, pathname, "com.tencent.mobileqq:MSF".getBytes(StandardCharsets.UTF_8)));
        }
        return null;
    }
}
