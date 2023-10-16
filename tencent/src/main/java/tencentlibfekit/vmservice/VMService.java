package tencentlibfekit.vmservice;

import com.github.unidbg.AndroidEmulator;
import com.github.unidbg.linux.android.AndroidResolver;
import com.github.unidbg.linux.android.ElfLibraryRawFile;
import com.github.unidbg.spi.AbstractLoader;
import com.kiliokuara.kuimivm.KuimiClass;
import com.kiliokuara.kuimivm.KuimiMethod;
import com.kiliokuara.kuimivm.KuimiObject;
import com.kiliokuara.kuimivm.KuimiVM;
import com.kiliokuara.kuimivm.abstractvm.KuimiAbstractVM;
import com.kiliokuara.kuimivm.execute.StackTrace;
import com.kiliokuara.kuimivm.objects.KuimiArrays;
import com.kiliokuara.kuimivm.objects.KuimiString;
import com.kiliokuara.kuimivm.unidbg.KuimiUnidbgVM;
import com.kiliokuara.kuimivm.unidbg.KuimiUnidbgVM64;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.AbstractCoroutine;
import org.jetbrains.annotations.NotNull;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import tencentlibfekit.FEKitAndroidEmulatorBuilder;
import tencentlibfekit.FEKitNativeIO;
import tencentlibfekit.FEKitNativeIOFiles;
import tencentlibfekit.FeKitSettings;
import tencentlibfekit.proto.DeviceInfoProto;
import tencentlibfekit.proto.QVersionConst;
import tencentlibfekit.storage.DStringStorage;
import tencentlibfekit.structloader.StructLoader;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Modifier;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class VMService implements IVMService {
    private static final Random RANDOM = new SecureRandom();
    public static final String QUA = QVersionConst.QUA;

    private final long botUin;
    public final KuimiAbstractVM vm;
    public final KuimiUnidbgVM univm;
    private final FeKitSettings settings;
    private final FEKitNativeIOFiles nativeIOFiles;
    public final AndroidEmulator emulator;
    private final VMClassHolder holder;
    public final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final File workingDir;
    private Thread executorThread;
    private DeviceInfoProto deviceInfoProto;
    private IChannelProxy channelProxy;
    private Map<String, Object> extArgs;
    private final Set<String> cmdList = new HashSet<>();
    private final AtomicBoolean tokenRequested = new AtomicBoolean(false);


    public VMService(
            File workingDir, StructLoader structFile,
            FEKitNativeIOFiles nativeIOFiles,
            boolean verbose,
            long botUin
    ) throws IOException {
        this.workingDir = workingDir;
        this.botUin = botUin;
        this.nativeIOFiles = nativeIOFiles;

        FeKitSettings feKitSettings = new FeKitSettings();
        var mmkvfile = new File(workingDir, "mmkv.bin");
        var mmkv = new DStringStorage(() -> new FileOutputStream(mmkvfile));
        if (mmkvfile.isFile()) {
            try (var input = new FileInputStream(mmkvfile)) {
                mmkv.load(input);
            }
        }
        feKitSettings.mmkv = mmkv;

        System.out.println("MMKV:");
        for (var entry : mmkv.entrySet()) {
            System.out.println(" | " + entry.getKey() + " -> " + entry.getValue());
        }
        System.out.println();
        this.settings = feKitSettings;

        emulator = FEKitAndroidEmulatorBuilder.for64Bit()
                .setProcessName("com.tencent.mobileqq")
                // .addBackendFactory(new DynarmicFactory(true))
                .build();
        vm = new KuimiAbstractVM();
        univm = new KuimiUnidbgVM64(vm, emulator);
        univm.verbose = false;

        var soRaw = Files.readAllBytes(nativeIOFiles.soFile.toPath());
        runOnExecutorThread(() -> {
            vm.attachThread(new StackTrace(2048, 2048, 2048));
            executorThread = Thread.currentThread();

            initVM(verbose);
            loadStructFile(structFile);
            loadSoFile(soRaw);
        });
        try {
            holder = runOnExecutorThread(() -> new VMClassHolder(vm));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private <T> T runOnExecutorThread(Callable<T> callable) throws Exception {
        if (Thread.currentThread() == executorThread) {
            return callable.call();
        } else {
            return executorService.submit(callable).get();
        }
    }

    private void runOnExecutorThread(Runnable runnable) {
        if (Thread.currentThread() == executorThread) {
            runnable.run();
        } else {
            try {
                executorService.submit(runnable).get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void syncCmdWhiteList(List<String> cmds) {
        cmdList.addAll(cmds);
    }

    public List<String> getCmdWhiteList() {
        return cmdList.stream().toList();
    }

    void getT544(CompletableFuture<byte[]> completableFuture, String command, byte[] bytes) {
        runOnExecutorThread(() -> {
            try {
                var result = holder.dandelionClazz.getMethodTable()
                        .resolveMethod("energy", "(Ljava/lang/Object;Ljava/lang/Object;)[B", false)
                        .resolveMethodHandle()
                        .invoke(
                                vm, vm.getStackTrace(), holder.dandelionObject,

                                new KuimiString(vm, command),
                                new KuimiArrays.PrimitiveArray<>(vm.resolveClass(Type.BYTE_TYPE).arrayType(), bytes)
                        );
                completableFuture.complete(((KuimiObject<byte[]>) result).getDelegateInstance());
            } catch (Throwable e) {
                completableFuture.completeExceptionally(e);
            }
        });
    }
    //FIXME@highlight
    // 流程：
    // 1. Dtn.initUin("")，获取 channel proxy 发送的 trpc.o3.ecdh_access.EcdhAccess.SsoEstablishShareKey
    //    然后将接收到的包通过 ChannelManager.onNativeReceive 传回 native 让其初始化
    // 2. 现在再调用 getSign 就可以签名了。
    // 3. 在登录后 Dtn.initUin(登录的账号), QQSecuritySign.requestToken() ,获取 channal proxy 发送的 trpc.o3.ecdh_access.SsoSecureA2Access
    //    然后将接收到的包通过 ChannelManager.onNativeReceive 传回 native 让其处理完成 Token 交换操作

    @Override
    public VMSignResult sign(int seqId, String cmdName, byte[] data, Map<String, Object> extArgs) {
        if (cmdName.equals("StatSvc.register")) {
            if (!tokenRequested.get() && tokenRequested.compareAndSet(false, true)) {
                //在登录完成后会请求token
                executorService.execute(() -> requestToken(Long.toString(botUin)));
            }
        }

        if (!cmdList.contains(cmdName)) return null;


        CompletableFuture<VMSignResult> completableFuture = new CompletableFuture<>();
        runOnExecutorThread(() -> {
            try {
                completableFuture.complete(sign0(botUin, seqId, data, cmdName));
            } catch (Throwable e) {
                completableFuture.completeExceptionally(e);
            }
        });
        try {
            return completableFuture.get();
        } catch (InterruptedException | ExecutionException e) {
            throw new RuntimeException(e);
        }
    }

    private VMSignResult sign0(long uin, int seq, byte[] content, String command) throws Throwable {

        // private native com.tencent.mobileqq.sign.QQSecuritySign.getSign(com.tencent.mobileqq.qsec.qsecurity.QSec, java.lang.String, java.lang.String, byte[], byte[], java.lang.String): com.tencent.mobileqq.sign.QQSecuritySign$SignResult

        var qSecuritySignClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/sign/QQSecuritySign"));
        var qSecuritySignObject = (KuimiObject<?>) qSecuritySignClazz.getMethodTable().resolveMethod("getInstance", qSecuritySignClazz, List.of(), true).resolveMethodHandle().invoke(vm, vm.getStackTrace());

        var qSecClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/qsec/qsecurity/QSec"));
        var qSecObject = qSecClazz.getMethodTable().resolveMethod("getInstance", qSecClazz, List.of(), true)
                .resolveMethodHandle().invoke(vm, vm.getStackTrace());


        var signResultClazz = vm.resolveClass(Type.getObjectType("com.tencent.mobileqq.sign.QQSecuritySign$SignResult"));
        var signResult = (KuimiObject<?>) qSecuritySignClazz.getMethodTable()
                .resolveMethod(
                        "getSign",
                        "(Lcom/tencent/mobileqq/qsec/qsecurity/QSec;Ljava/lang/String;Ljava/lang/String;[B[BLjava/lang/String;)Lcom/tencent/mobileqq/sign/QQSecuritySign$SignResult;",
                        false
                )
                .resolveMethodHandle()
                .invoke(
                        vm, vm.getStackTrace(),
                        qSecuritySignObject,
                        qSecObject,
                        new KuimiString(vm, QUA), // extra
                        new KuimiString(vm, command), // command
                        new KuimiArrays.PrimitiveArray<>(vm.resolveClass(Type.BYTE_TYPE).arrayType(), content), // wupBuffer (pkg body data)
                        new KuimiArrays.PrimitiveArray<>(vm.resolveClass(Type.BYTE_TYPE).arrayType(), ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(seq).array()), // request sso seq id
                        new KuimiString(vm, Long.toString(uin)) // uin
                );

        var signResultFieldExtra = signResultClazz.getFieldTable().findField(false, "extra");
        var signResultFieldSign = signResultClazz.getFieldTable().findField(false, "sign");
        var signResultFieldToken = signResultClazz.getFieldTable().findField(false, "token");

        var extra = signResult.memoryView().objects[signResultFieldExtra.getObjectIndex()];
        var sign = signResult.memoryView().objects[signResultFieldSign.getObjectIndex()];
        var token = signResult.memoryView().objects[signResultFieldToken.getObjectIndex()];


        var result = new VMSignResult();
        result.extra = (byte[]) extra.getDelegateInstance();
        result.sign = (byte[]) sign.getDelegateInstance();
        result.token = (byte[]) token.getDelegateInstance();
        System.out.println(result);
        return result;
    }

    public void initVM(boolean verbose) {
        var memory = emulator.getMemory();

        {
            var randomValue = RANDOM.nextInt(256, 1024) * 0x0010_0000L;
            UnidbgHelper.AbstractLoader$mmapBaseAddress.getAndAdd((AbstractLoader<?>) memory, randomValue);
        }

        memory.setLibraryResolver(new AndroidResolver(23));
        emulator.getSyscallHandler().addIOResolver(new FEKitNativeIO(settings, nativeIOFiles));
        emulator.getSyscallHandler().setEnableThreadDispatcher(true);

        vm.attachThread(new StackTrace(2048, 2048, 2048));
        univm.verbose = false;
    }

    public void requestToken(String uinString) {
        try {
            var qSecClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/qsec/qsecurity/QSec"));
            var qSecObject = qSecClazz.getMethodTable().resolveMethod("getInstance", qSecClazz, List.of(), true)
                    .resolveMethodHandle().invoke(vm, vm.getStackTrace());
            qSecClazz.getMethodTable().resolveMethod("updateUserID", "(Ljava/lang/String;)V", false)
                    .resolveMethodHandle().invoke(vm, vm.getStackTrace(), qSecObject, new KuimiString(vm, uinString));
            holder.dtnClazz.getMethodTable()
                    .resolveMethod("initUin", "(Ljava/lang/String;)V", false)
                    .resolveMethodHandle()
                    .invoke(vm, vm.getStackTrace(), holder.dtnObject, new KuimiString(vm, uinString));
            var qSecuritySignClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/sign/QQSecuritySign"));

            var qSecuritySignObject = (KuimiObject<?>) qSecuritySignClazz.getMethodTable()
                    .resolveMethod("getInstance", qSecuritySignClazz, List.of(), true)
                    .resolveMethodHandle()
                    .invoke(vm, vm.getStackTrace());
            qSecuritySignClazz.getMethodTable()
                    .resolveMethod("requestToken", vm.resolveClass(Type.VOID_TYPE), List.of(), false)
                    .resolveMethodHandle()
                    .invoke(vm, vm.getStackTrace(), qSecuritySignObject);
        } catch (Throwable e) {
            throw new RuntimeException("error when requesting token", e);
        }

    }

    @Override
    public byte[] tlv(int type, Map<String, Object> extArgs, byte[] content) {
        if (type == 0x544) {
            Object command = extArgs.get("KEY_COMMAND_STR");
            if (command instanceof String) {
                CompletableFuture<byte[]> completableFuture = new CompletableFuture<>();
                getT544(completableFuture, (String) command, content);
                try {
                    return completableFuture.get();
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return null;
    }

    public void settingsInit(DeviceInfoProto deviceInfo, Map<String, Object> extArgs) {
        this.deviceInfoProto = deviceInfo;
        {
            settings.buildProps.put("ro.build.id", new String(deviceInfo.display, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.build.display.id", settings.getBuildProp("ro.build.id"));
            settings.buildProps.put("ro.product.name", new String(deviceInfo.product, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.product.device", new String(deviceInfo.device, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.product.board", new String(deviceInfo.board, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.product.manufacturer", new String(deviceInfo.brand, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.product.brand", new String(deviceInfo.brand, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.bootloader", new String(deviceInfo.bootloader, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.product.cpu.abilist", "arm64-v8a,armeabi-v7a,armeabi");
            settings.buildProps.put("ro.build.version.incremental", new String(deviceInfo.version.incremental, StandardCharsets.UTF_8));
            settings.buildProps.put("ro.build.version.release", new String(deviceInfo.version.release, StandardCharsets.UTF_8));
            // ro.build.version.base_os = -1
            settings.buildProps.put("ro.build.version.security_patch", "2021-01-01");
            settings.buildProps.put("ro.build.version.preview_sdk", "0");
            settings.buildProps.put("ro.build.version.codename", new String(deviceInfo.version.codename));
            settings.buildProps.put("ro.build.version.all_codenames", new String(deviceInfo.version.codename));

            settings.buildProps.put("ro.treble.enabled", "true");
            settings.buildProps.put("ro.build.date.utc", "1611112990");
            settings.buildProps.put("ro.build.user", "jenkins");
            settings.buildProps.put("ro.build.host", "mirai");
            var fingerprint = new String(deviceInfo.fingerprint, StandardCharsets.UTF_8);
            // ro.boot.container = -1
            settings.buildProps.put("ro.vendor.build.fingerprint", fingerprint);
            // ro.build.expect.bootloader = -1
            // ro.build.expect.baseband = -1
            settings.buildProps.put("net.bt.name", "Android");
            settings.buildProps.put("ro.build.characteristics", "default");
            //Parse fingerprint
            String[] strings = fingerprint.split("/");
            var brand = strings[0];// brand
            var name = strings[1];// name
            var strs2 = strings[2].split(":", 2);//[ro.hardware]:[ro.build.version.release]
            var hardware = strs2[0];
            var release = strs2[1];
            var display = strings[3];
            var strs3 = strings[4].split(":", 2);//
            var incremental = strs3[0];
            var userStr = strs3[1];
            var keysStr = strings[5];
            settings.buildProps.put("ro.build.type", userStr);
            settings.buildProps.put("ro.build.tags", keysStr);
            settings.buildProps.put("ro.hardware", hardware);
            settings.buildProps.put("ro.build.description", name + "-" + userStr + " " + release + " " + display + " " + incremental + " " + keysStr);
            settings.buildProps.put("ro.build.flavor", name + "-" + userStr);
            settings.buildProps.put("ro.product.locale", "zh-CN");
            settings.buildProps.put("persist.sys.timezone", "Asia/Shanghai");
            settings.buildProps.put("ro.config.ringtone", "01_Life_Is_Good.ogg");

            settings.mmkv.put("DeviceToken-oaid-V001", "");
            settings.mmkv.put("DeviceToken-qimei36-V001", (String) extArgs.get("KEY_QIMEI36"));
            settings.mmkv.put("DeviceToken-ANDROID-ID-V001", new String(deviceInfo.androidId, StandardCharsets.UTF_8));
            settings.mmkv.put("DeviceToken-MAC-ADR-V001", "");
            settings.mmkv.put("DeviceToken-MODEL-XX-V001", new String(deviceInfo.model, StandardCharsets.UTF_8));
            settings.mmkv.put("DeviceToken-TuringCache-V001", "");
            settings.mmkv.put("TuringRiskID-TuringCache-20230511", "");
            //randomUUID|versionName
            if (settings.mmkv.get("MQQ_SP_DEVICETOKEN_DID_DEVICEIDUUID_202207072241") == null) {
                settings.mmkv.put("MQQ_SP_DEVICETOKEN_DID_DEVICEIDUUID_202207072241", UUID.randomUUID() + "|8.9.58");
            }
            settings.mmkv.put("DeviceToken-APN-V001", "");
            settings.mmkv.put("DeviceToken-wifissid-V001", new String(deviceInfo.wifiSSID, StandardCharsets.UTF_8));

            settings.sysProps.put("user.locale", "zh-CN");

            settings.sysProps.put("http.agent", "Dalvik/2.1.0" +
                    " (Linux; U; Android " +
                    new String(deviceInfo.version.release, StandardCharsets.UTF_8) +
                    "; " +
                    new String(deviceInfo.device, StandardCharsets.UTF_8) +
                    " Build/" +
                    new String(deviceInfo.display, StandardCharsets.UTF_8) +
                    ")");

            settings.sysProps.put("java.vm.version", "2.1.0");
            settings.sysProps.put("os.version", new String(deviceInfo.procVersion, StandardCharsets.UTF_8).split(" ")[2]);
            settings.sysProps.put("java.runtime.version", "0.9");
            settings.bootId = deviceInfo.bootId;
        }
    }

    @Override
    public void initialize(Map<String, Object> args, DeviceInfoProto deviceInfoProto, IChannelProxy channelProxy) {
        settingsInit(deviceInfoProto, args);
        this.channelProxy = channelProxy;
        this.extArgs = args;

        if (channelProxy == null) {
            throw new IllegalStateException("ChannelProxy not found");
        }

        var ecdhInitCountDown = new CountDownLatch(1);
        runOnExecutorThread((() -> {
            System.out.println("======================= [spi initialize] ========================");
            holder.classInit(ecdhInitCountDown, channelProxy);
            System.out.println("======================= [spi initialize end] ========================");

        }));

        try {
            ecdhInitCountDown.await();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private void loadSoFile(byte[] data) {
        var module = emulator.getMemory().load(new ElfLibraryRawFile("libfekit", data, emulator.is64Bit()), false);

        System.out.println("fekit base: 0x" + Long.toHexString(module.base));

        var onLoad = module.findSymbolByName("JNI_OnLoad", false);
        System.out.println("OOL: " + onLoad);
        if (onLoad != null) {
            System.out.println("RSP: " + onLoad.call(emulator, univm.getJavaVM(), null));
        }

    }

    private void initClassStub(ClassLoader loader, String mainClass) throws Throwable {
        var initClass = loader.loadClass(mainClass);
        MethodHandles.privateLookupIn(initClass, MethodHandles.lookup())
                .findStatic(initClass, "initialize", MethodType.methodType(void.class, KuimiVM.class, KuimiObject.class)).invoke(vm, null);
    }

    private void loadStructFile(StructLoader structLoader) throws IllegalStateException {
        try {
            initKuimiVMEnv();
            initClassStub(structLoader.getClassLoader(), structLoader.getMainClass());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to init class stub!", e);
        }
    }

    private void initKuimiVMEnv() {
        var userDataPath = nativeIOFiles.userData.getPath();
        var javaLangObject = vm.getBaseClass();
        var javaLangString = vm.getStringClass();

        var javaIoFile = new KuimiClass(vm, Type.getObjectType("java/io/File"), Opcodes.ACC_PUBLIC, null, javaLangObject, List.of());
        javaIoFile.getMethodTable().addMethod(new KuimiMethod(javaIoFile, Opcodes.ACC_PUBLIC, "<init>", vm.getPrimitiveClass(Type.VOID_TYPE), List.of(javaLangString)) {
            void execute(KuimiVM vm, StackTrace st, KuimiObject thiz, KuimiObject string) throws IOException {
                Path path = Paths.get(userDataPath, (String) string.getDelegateInstance());
                Files.createDirectories(path);
                thiz.setDelegateInstance(path.toFile());
            }
        });
        javaIoFile.getMethodTable().addMethod(new KuimiMethod(javaIoFile, Opcodes.ACC_PUBLIC, "getAbsolutePath", javaLangString, List.of()) {
            KuimiObject<?> execute(KuimiVM vm, StackTrace st, KuimiObject<File> thiz) {
                return new KuimiString(vm, thiz.getDelegateInstance().getAbsolutePath().split("userdata", 2)[1].replace('\\', '/'));
            }
        });
        javaIoFile.getMethodTable().addMethod(new KuimiMethod(javaIoFile, Opcodes.ACC_PUBLIC, "canRead", vm.getPrimitiveClass(Type.BOOLEAN_TYPE), List.of()) {
            boolean execute(KuimiVM vm, StackTrace st, KuimiObject<File> thiz) {
                File file = thiz.getDelegateInstance();
                // /data/data 目录应该不可读
                if (file.getName().equals("..") && file.getParentFile().getParentFile().toString().replace('\\', '/').equals(userDataPath + "/data/data")) {
                    return false;
                }
                return thiz.getDelegateInstance().canRead();
            }
        });
        vm.getBootstrapPool().put(javaIoFile);

    }

    private class VMClassHolder {
        private final KuimiClass fekitLogImplClazz;
        private KuimiClass dtnClazz;
        private KuimiClass dandelionClazz;
        private Object dandelionObject;
        private KuimiObject dtnObject;

        VMClassHolder(KuimiAbstractVM vm) {
            // private native com.tencent.mobileqq.sign.QQSecuritySign.getSign(com.tencent.mobileqq.qsec.qsecurity.QSec, java.lang.String, java.lang.String, byte[], byte[], java.lang.String): com.tencent.mobileqq.sign.QQSecuritySign$SignResult
            System.out.println("======================= [init fekit encrypt service] ========================");

            // Dtc impl
            {
                var dtc = vm.resolveClass(Type.getObjectType("com.tencent.mobileqq.dt.app.Dtc"));
                dtc.getMethodTable().resolveMethod("mmKVValue", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: MMKV CALL] mmKVValue call: " + key);
                                return new KuimiString(vm, safeString(settings.mmkv(str(key)), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("mmKVSaveValue", "(Ljava/lang/String;Ljava/lang/String;)V", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getPrimitiveClass(Type.VOID_TYPE), List.of(vm.getStringClass(), vm.getStringClass())) {
                            void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key, KuimiObject<?> value) {
                                System.out.println("![DTC: MMKV CALL] mmKVSaveValue call: key=" + key + ", value=" + value);
                                settings.mmkvSet(str(key), str(value));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getPropSafe", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                var result = new KuimiString(vm, safeString(settings.getBuildProp(str(key)), "-1"));
                                System.out.println("![DTC: GetPropSafe] getPropSafe: " + key + "=" + result);
                                return result;
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("systemGetSafe", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                var result = new KuimiString(vm, safeString(settings.getSystemProp(str(key)), "-1"));
                                System.out.println("![DTC: systemGetSafe] systemGetSafe: " + key + "=" + result);
                                return result;
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getAppVersionName", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getAppVersionName] getAppVersionName: " + key);
                                return new KuimiString(vm, safeString(settings.getAppVersionName(str(key)), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getAppVersionCode", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getAppVersionCode] getAppVersionCode: " + key);
                                return new KuimiString(vm, safeString(settings.getAppVersionCode(str(key)), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getAppInstallTime", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getAppInstallTime] getAppInstallTime: " + key);
                                return new KuimiString(vm, safeString(settings.getAppInstallTime(str(key)), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getDensity", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getDensity] getDensity: " + key);
                                return new KuimiString(vm, safeString(settings.getDensity(), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getFontDpi", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getFontDpi] getFontDpi: " + key);
                                return new KuimiString(vm, safeString(settings.getFontDpi(), "-1"));
                            }
                        }
                );

                dtc.getMethodTable().resolveMethod("getScreenSize", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getScreenSize] getScreenSize: " + key);
                                return new KuimiString(vm, safeString(settings.getScreenSize(), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getStorage", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getStorage] getStorage: " + key);
                                return new KuimiString(vm, safeString(settings.getStorage(), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getIME", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getIME] getIME: " + key);
                                return new KuimiString(vm, safeString(settings.getIME(), "-1"));
                            }
                        }
                );
                dtc.getMethodTable().resolveMethod("getAndroidID", "(Ljava/lang/String;)Ljava/lang/String;", true).attachImplementation(
                        new KuimiMethod(dtc, Opcodes.ACC_STATIC, "test", vm.getStringClass(), List.of(vm.getStringClass())) {
                            KuimiObject<?> execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> key) {
                                System.out.println("![DTC: getIME] getAndroidID: " + key);
                                return new KuimiString(vm, safeString(settings.getAndroidID(), "-1"));
                            }
                        }
                );
            }
            // IFEKitLog impl
            {
                var iFEKitLog = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/fe/IFEKitLog"));
                fekitLogImplClazz = new KuimiClass(vm, Type.getObjectType("com/tencent/mobileqq/oicq/SubLog"), 0, null, iFEKitLog, null);
                vm.getBootstrapPool().put(fekitLogImplClazz);
                fekitLogImplClazz.getMethodTable().addMethod(new VMClassHolder.FeKitLogMethod("d", "DEBUG  "));
                fekitLogImplClazz.getMethodTable().addMethod(new VMClassHolder.FeKitLogMethod("e", "ERROR  "));
                fekitLogImplClazz.getMethodTable().addMethod(new VMClassHolder.FeKitLogMethod("i", "INFO   "));
                fekitLogImplClazz.getMethodTable().addMethod(new VMClassHolder.FeKitLogMethod("v", "VERBOSE"));
                fekitLogImplClazz.getMethodTable().addMethod(new VMClassHolder.FeKitLogMethod("w", "WARNING"));

            }
            System.out.println("======================= [init fekit encrypt service end] ========================");
        }

        public static String buf_to_string(byte[] bArr) {
            StringBuilder str = new StringBuilder();
            if (bArr == null) {
                return "";
            }
            for (byte b : bArr) {
                str.append(Integer.toHexString((b >> 4) & 15)).append(Integer.toHexString(b & 15));
            }
            return str.toString();
        }

        private static String safeString(String val, String def) {
            if (val != null) return val;
            return def;
        }

        private static String str(KuimiObject<?> object) {
            if (object == null) return null;
            return object.toString();
        }

        public void classInit(CountDownLatch ecdhInitCountDown, IChannelProxy channelProxySpi) {
            try {
                // t544 init
                dandelionClazz = vm.resolveClass(Type.getObjectType("com.tencent.mobileqq.qsec.qsecdandelionsdk.Dandelion"));
                dandelionObject = dandelionClazz.getMethodTable()
                        .resolveMethod("getInstance", dandelionClazz, List.of(), true)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace());
                // follow sequence of com.tencent.mobileqq.msf.core.a0.a.a(MsfCore, String)
                // region com.tencent.mobileqq.fe.FEKit.init()

                //   Dtc.setQ36();
                //   async Dtc.initOAIDAsync(Context);
                //   checkSafeMode() returns false
                var qSecuritySignClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/sign/QQSecuritySign"));
                var qSecuritySignObject = qSecuritySignClazz.getMethodTable()
                        .resolveMethod("getInstance", qSecuritySignClazz, List.of(), true)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace());
                qSecuritySignClazz.getMethodTable()
                        .resolveMethod("initSafeMode", "(Z)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), qSecuritySignObject, false);

                //   initContext(Context, String uin)
                var context = vm.resolveClass(Type.getObjectType("android/content/Context")).allocateNewObject();

                dtnClazz = vm.resolveClass(Type.getObjectType("com.tencent.mobileqq.dt.Dtn"));
                dtnObject = (KuimiObject<?>) dtnClazz.getMethodTable()
                        .resolveMethod("getInstance", dtnClazz, List.of(), true)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace());

                dtnClazz.getMethodTable()
                        .resolveMethod("initContext", "(Landroid/content/Context;)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), dtnObject, context);
                dtnClazz.getMethodTable()
                        .resolveMethod("initLog", "(Lcom/tencent/mobileqq/fe/IFEKitLog;)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), dtnObject, fekitLogImplClazz.allocateNewObject());
                dtnClazz.getMethodTable()
                        .resolveMethod("initUin", "(Ljava/lang/String;)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), dtnObject, new KuimiString(vm, "0"));

                //FIXME @highlight
                // ↑ 空 account 的话 native 会发送 trpc.o3.ecdh_access.EcdhAccess.SsoEstablishShareKey 到 channel proxy impl
                // 空 account 字符串会发送 trpc.o3.ecdh_access.EcdhAccess.SsoSecureA2Establish
                // 在 channel proxy impl 中需要 send sso packet，将接收到的包传回 ChannelManager.onNativeReceive 让 native 处理
                //   QSec.getInstance().init(Context, String, String, String, String, String)
                var qSecClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/qsec/qsecurity/QSec"));
                var qSecObject = qSecClazz.getMethodTable().resolveMethod("getInstance", qSecClazz, List.of(), true)
                        .resolveMethodHandle().invoke(vm, vm.getStackTrace());
                //QSecConfig.setupBusinessInfo(context, str, str2, "", str3, str4, str5);
                var qSecCnfClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/qsec/qsecurity/QSecConfig"));
                qSecCnfClazz.getMethodTable().resolveMethod("setupBusinessInfo", "(Landroid/content/Context;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", true)
                        .resolveMethodHandle().invoke(vm, vm.getStackTrace(), context, new KuimiString(vm, "0"), new KuimiString(vm, buf_to_string(deviceInfoProto.guid)), new KuimiString(vm, ""), new KuimiString(vm, ""), new KuimiString(vm, (String) extArgs.get("KEY_QIMEI36")), new KuimiString(vm, QUA));
                //StartTaskSystem
                qSecClazz.getMethodTable().resolveMethod("doSomething", "(Landroid/content/Context;I)I", false)
                        .resolveMethodHandle().invoke(vm, vm.getStackTrace(), qSecObject, context, 1);

                //      Dandelion.getInstance().init()
                //      ByteData.getInstance().init(Context)
                var byteDataClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/qsec/qsecprotocol/ByteData"));

                var byteDataObject = byteDataClazz.getMethodTable()
                        .resolveMethod("getInstance", byteDataClazz, List.of(), true)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace());

                var initMethod = byteDataClazz.getMethodTable()
                        .resolveMethod("init", "(Landroid/content/Context;)V", false)
                        .resolveMethodHandle();
                initMethod.invoke(vm, vm.getStackTrace(), byteDataObject, context);
                //   QQSecuritySign.getInstance().init(str8); set extra
                qSecuritySignClazz.getMethodTable()
                        .resolveMethod("init", "(Ljava/lang/String;)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), qSecuritySignObject, new KuimiString(vm, QUA)); // BaseApplication.getContext().getQua(); // locate: assets/qua.ini

                // endregion com.tencent.mobileqq.fe.FEKit.init()

                // FEKit.getInstance().getCmdWhiteList();
                // channel manager init
                var channelManagerClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/channel/ChannelManager"));
                var channelManagerObject = channelManagerClazz.getMethodTable()
                        .resolveMethod("getInstance", channelManagerClazz, List.of(), true)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace());

                // channel manager init - sync whitelist
                var whiteList = channelManagerClazz.getMethodTable()
                        .getDeclaredMethods().stream()
                        .filter(it -> it.getMethodName().equals("getCmdWhiteList"))
                        .filter(it -> Modifier.isNative(it.getModifiers()))
                        .findFirst()
                        .orElseThrow()
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), channelManagerObject);
                syncCmdWhiteList(((List<KuimiString>) ((KuimiObject) whiteList).getDelegateInstance()).stream().map(KuimiString::getDelegateInstance).toList());
                var channelProxyClazz = vm.resolveClass(Type.getObjectType("com/tencent/mobileqq/channel/ChannelProxy"));
                var channelProxyImplClazz = new KuimiClass(vm, Type.getObjectType("tencentlibfekit.EncryptServiceProxy"), 0, null, channelProxyClazz, null);

                channelProxyImplClazz.getMethodTable().addMethod(new KuimiMethod(
                        channelProxyImplClazz, Opcodes.ACC_PUBLIC, "sendMessage",
                        vm.getPrimitiveClass(Type.VOID_TYPE),
                        List.of(vm.getStringClass(), vm.getPrimitiveClass(Type.BYTE_TYPE).arrayType(), vm.getPrimitiveClass(Type.LONG_TYPE))
                ) {
                    void onReceive(IChannelProxy.ChannelResult result, KuimiObject<?> string, long callbackid) throws Throwable {

                        System.out.println("[!!! ChannelProxy] receiveMessage: {" + string + "}, length=" + result.data().length + ", " + HexFormat.ofDelimiter(" ").formatHex(result.data()));

                        channelManagerClazz.getMethodTable()
                                .getDeclaredMethods().stream()
                                .filter(it -> it.getMethodName().equals("onNativeReceive"))
                                .filter(it -> Modifier.isNative(it.getModifiers()))
                                .findFirst()
                                .orElseThrow()
                                .resolveMethodHandle()
                                .invoke(
                                        vm, vm.getStackTrace(),
                                        channelManagerObject,
                                        new KuimiString(vm, result.command()),
                                        new KuimiArrays.PrimitiveArray<>(vm.resolveClass(Type.BYTE_TYPE).arrayType(), result.data()),
                                        true,
                                        callbackid
                                );
                        if (ecdhInitCountDown.getCount() == 1) {
                            ecdhInitCountDown.countDown();
                            System.out.println("Ecdh init complete");
                        }
                        System.out.println("MSG Process complete");
                    }

                    void execute(KuimiVM vm, StackTrace stackTrace, KuimiObject<?> thiz, KuimiObject<?> string, KuimiObject<?> barr, long l) throws Throwable {
                        System.out.println("[!!! ChannelProxy] sendMessage: {" + string + "}[" + l + "] ");
                        assert channelProxySpi != null;
                        executorService.execute(() -> {
                            var result = channelProxySpi.sendMessage(
                                    "mobileqq.msf.security",
                                    string.toString(),
                                    0,
                                    (byte[]) barr.getDelegateInstance(),
                                    new AbstractCoroutine<>(EmptyCoroutineContext.INSTANCE, false, true) {
                                        @Override
                                        protected void onCompleted(IChannelProxy.ChannelResult value) {
                                            runOnExecutorThread(() -> {
                                                System.out.println("LLLLLLLLLLLL RSP: " + value);
                                                try {
                                                    onReceive(value, string, l);
                                                } catch (Throwable throwable) {
                                                    throw new RuntimeException(throwable);
                                                }
                                            });
                                        }

                                        @Override
                                        protected void onCancelled(@NotNull Throwable cause, boolean handled) {
                                            cause.printStackTrace();
                                        }
                                    }
                            );
                            if (result != kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                                try {
                                    onReceive((IChannelProxy.ChannelResult) result, string, l);
                                } catch (Throwable e) {
                                    throw new RuntimeException(e);
                                }
                            }
                        });

                    }
                });

                var channelProxyImplObject = channelProxyImplClazz.allocateNewObject();

                channelManagerClazz.getMethodTable()
                        .resolveMethod("init", "(Lcom/tencent/mobileqq/channel/ChannelProxy;)V", false)
                        .resolveMethodHandle()
                        .invoke(vm, vm.getStackTrace(), channelManagerObject, channelProxyImplObject);
                channelManagerClazz.getMethodTable()
                        .resolveMethod("setChannelProxy", "(Lcom/tencent/mobileqq/channel/ChannelProxy;)V", false)
                        .resolveMethodHandle().invoke(vm, vm.getStackTrace(), channelManagerObject, channelProxyImplObject);
                //String str = QSecConfig.business_qua;
                //String m224148g = C81897c.m224152c().m224148g();//"6.2.221"
                //String str2 = Build.VERSION.RELEASE;
                //initReport(str, m224148g, str2, Build.BRAND + DeviceInfoMonitor.getModel(), QSecConfig.business_q36, QSecConfig.business_guid);
                channelManagerClazz.getMethodTable()
                        .resolveMethod("initReport", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V", false)
                        .resolveMethodHandle().invoke(vm, vm.getStackTrace(),
                                channelManagerObject,
                                new KuimiString(vm, QUA),
                                new KuimiString(vm, "6.2.221"),
                                new KuimiString(vm, new String(deviceInfoProto.version.release, StandardCharsets.UTF_8)),
                                new KuimiString(vm, new String(deviceInfoProto.brand, StandardCharsets.UTF_8) + new String(deviceInfoProto.model))
                                , new KuimiString(vm, (String) extArgs.get("KEY_QIMEI36")),
                                new KuimiString(vm, buf_to_string(deviceInfoProto.guid)));

            } catch (Throwable t) {
                t.printStackTrace(System.out);
            }
        }

        class FeKitLogMethod extends KuimiMethod {
            private final String levelUpper;

            FeKitLogMethod(String level, String levelUpper) {
                super(fekitLogImplClazz, Opcodes.ACC_PUBLIC, level, vm.resolveClass(Type.VOID_TYPE), List.of(
                        vm.getStringClass(), vm.getPrimitiveClass(Type.INT_TYPE), vm.getStringClass()
                ));
                this.levelUpper = levelUpper;
            }

            protected void execute(KuimiVM vm, StackTrace st, KuimiObject<?> thiz, KuimiObject<?> str, int v, KuimiObject<?> str2) {
                System.out.format("[FEKitLog %s] %s %s %s%n", levelUpper, str, v, str2);
            }
        }
    }
}
