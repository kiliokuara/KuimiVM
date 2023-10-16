package tencentlibfekit;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import net.mamoe.mirai.internal.spi.EncryptService;
import net.mamoe.mirai.internal.spi.EncryptServiceContext;
import net.mamoe.mirai.utils.AtomicBoolean;
import net.mamoe.mirai.utils.DeviceInfo;
import net.mamoe.mirai.utils.Services;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import tencentlibfekit.proto.DeviceInfoProto;
import tencentlibfekit.vmservice.IChannelProxy;
import tencentlibfekit.vmservice.IVMService;

import java.util.Map;

public class FEKitEncryptService implements EncryptService {
    final IVMService vmService;
    AtomicBoolean initialized = new AtomicBoolean(false);

    public FEKitEncryptService(IVMService service) {
        this.vmService = service;
    }

    public interface ServiceLoader {
        IVMService load(@NotNull EncryptServiceContext context, @NotNull CoroutineScope serviceSubScope) throws Throwable;
    }

    public static void registerService(ServiceLoader serviceLoader) {
        Services.INSTANCE.register(
                "net.mamoe.mirai.internal.spi.EncryptService.Factory",
                "tencentlibfekit.FEKitEncryptServiceFactory",
                () -> (EncryptService.Factory) (context, serviceSubScope) -> {
                    try {
                        return new FEKitEncryptService(serviceLoader.load(context, serviceSubScope));
                    } catch (Throwable e) {
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    @Nullable
    @Override
    public SignResult qSecurityGetSign(@NotNull EncryptServiceContext encryptServiceContext, int sequenceId, @NotNull String commandName, @NotNull byte[] body) {
        var result = vmService.sign(sequenceId, commandName, body, convertArgs(encryptServiceContext.getExtraArgs().toMap())); // unused now
        if (result == null) return null;
        return new SignResult(result.sign, result.token, result.extra);
    }

    @Override
    public void initialize(@NotNull EncryptServiceContext encryptServiceContext) {
        Thread.dumpStack();
        if (initialized.getValue()) {
            return;
        }
        if (!initialized.compareAndSet(false, true)) return;

        vmService.initialize(
                convertArgs(encryptServiceContext.getExtraArgs().toMap()),
                convertDeviceInfo(encryptServiceContext.getExtraArgs().toMap().get("KEY_DEVICE_INFO")),
                convertChannelSpi(encryptServiceContext.getExtraArgs().toMap().get("KEY_CHANNEL_PROXY"))
        );

    }

    private IChannelProxy convertChannelSpi(Object keyChannelProxy) {
        EncryptService.ChannelProxy delegate = (EncryptService.ChannelProxy) keyChannelProxy;

        return new IChannelProxy() {
            @Override
            public Object sendMessage(String remark, String command, long botUin, byte[] data, Continuation<? extends ChannelResult> continuation) {
                var rsp = delegate.sendMessage(remark, command, botUin, data, new Continuation<>() {
                    @Override
                    public void resumeWith(@NotNull Object o) {
                        if (o instanceof EncryptService.ChannelResult) {
                            continuation.resumeWith(convert((EncryptService.ChannelResult) o));
                        } else {
                            continuation.resumeWith(o);
                        }
                    }

                    @NotNull
                    @Override
                    public CoroutineContext getContext() {
                        return continuation.getContext();
                    }
                });
                if (rsp == kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED()) {
                    return rsp;
                }

                return convert((EncryptService.ChannelResult) rsp);
            }

            private ChannelResult convert(EncryptService.ChannelResult result) {
                if (result == null) return null;
                return new ChannelResult(result.getCmd(), result.getData());
            }
        };
    }

    public static DeviceInfoProto convertDeviceInfo(Object keyDeviceInfo) {
        DeviceInfo miraiDeviceInfo = (DeviceInfo) keyDeviceInfo;
        DeviceInfoProto deviceInfoProto = new DeviceInfoProto();
        deviceInfoProto.display = miraiDeviceInfo.getDisplay();
        deviceInfoProto.product = miraiDeviceInfo.getProduct();
        deviceInfoProto.device = miraiDeviceInfo.getDevice();
        deviceInfoProto.board = miraiDeviceInfo.getBoard();
        deviceInfoProto.brand = miraiDeviceInfo.getBrand();
        deviceInfoProto.model = miraiDeviceInfo.getModel();
        deviceInfoProto.bootloader = miraiDeviceInfo.getBootloader();
        deviceInfoProto.fingerprint = miraiDeviceInfo.getFingerprint();
        deviceInfoProto.bootId = miraiDeviceInfo.getBootId();
        deviceInfoProto.procVersion = miraiDeviceInfo.getProcVersion();
        deviceInfoProto.baseBand = miraiDeviceInfo.getBaseBand();
        {
            DeviceInfoProto.Version version = deviceInfoProto.version = new DeviceInfoProto.Version();
            DeviceInfo.Version miraiVersion = miraiDeviceInfo.getVersion();
            version.codename = miraiVersion.getCodename();
            version.incremental = miraiVersion.getIncremental();
            version.sdk = miraiVersion.getSdk();
            version.release = miraiVersion.getRelease();
        }
        deviceInfoProto.simInfo = miraiDeviceInfo.getSimInfo();
        deviceInfoProto.osType = miraiDeviceInfo.getOsType();
        deviceInfoProto.macAddress = miraiDeviceInfo.getMacAddress();
        deviceInfoProto.wifiBSSID = miraiDeviceInfo.getWifiBSSID();
        deviceInfoProto.wifiSSID = miraiDeviceInfo.getWifiSSID();
        deviceInfoProto.imsiMd5 = miraiDeviceInfo.getImsiMd5();
        deviceInfoProto.imei = miraiDeviceInfo.getImei();
        deviceInfoProto.apn = miraiDeviceInfo.getApn();
        deviceInfoProto.androidId = miraiDeviceInfo.getAndroidId();
        deviceInfoProto.guid = miraiDeviceInfo.getGuid();
        return deviceInfoProto;
    }

    @Override
    public byte @Nullable [] encryptTlv(@NotNull EncryptServiceContext encryptServiceContext, int i, @NotNull byte[] bytes) {
        return vmService.tlv(i, convertArgs(encryptServiceContext.getExtraArgs().toMap()), bytes);
    }

    private Map<String, Object> convertArgs(Map<String, Object> map) {
        return map; // TODO
    }

}
