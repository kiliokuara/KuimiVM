package kfc.vivo50.code45;

import net.mamoe.mirai.console.data.Value;
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin;
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescriptionBuilder;
import net.mamoe.mirai.internal.spi.EncryptService;
import net.mamoe.mirai.internal.utils.MiraiProtocolInternal;
import net.mamoe.mirai.utils.BotConfiguration;
import tencentlibfekit.FEKitEncryptService;
import tencentlibfekit.common.GlobalShared;
import tencentlibfekit.common.ResourceExtract;
import tencentlibfekit.proto.QVersionConst;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;

// note: classname just for final obf
// build plugin:
//
//      gradle :packer:build
public class Code45 extends JavaPlugin {
    public static final Code45 INSTANCE = new Code45();
    private static final HttpClient HTTP_CLIENT = GlobalShared.HTTP_CLIENT;

    private Code45() {
        super(
                new JvmPluginDescriptionBuilder("kfc.vivo50.code45", "11.45.14")
                        .build()
        );
    }

    public static File unpackResources() {
        var dataFolder = INSTANCE.getDataFolder();

        return ResourceExtract.unpackResources(dataFolder);
    }

    @Override
    public void onEnable() {
        reloadPluginConfig(PluginConfig.INSTANCE);

        try {
            ConsoleServiceFactory serviceFactory;
            if (ServiceSelector.doRpc()) {

                Value<String> host = PluginConfig.INSTANCE.serverHost;
                Value<Integer> port = PluginConfig.INSTANCE.serverPort;
                Value<String> authKey = PluginConfig.INSTANCE.authKey;
                Value<String> serverIdKey = PluginConfig.INSTANCE.serverIdentityKey;

                StringBuilder url = new StringBuilder("http://");
                url.append(host.get());
                url.append(':');
                url.append(port.get());
                url.append('/');

                serviceFactory = new ConsoleServiceFactory.Remote(URI.create(url.toString()), authKey.get(), serverIdKey.get());

            } else {

                var dataFolder = getDataFolder();


                var apkResult = ResourceExtract.downloadMobileQQ(dataFolder);


                serviceFactory = ServiceSelector.get(apkResult.apk(), apkResult.fekit());
            }

            FEKitEncryptService.registerService((context, serviceSubScope) -> {

                var protocol = MiraiProtocolInternal.Companion.get((BotConfiguration.MiraiProtocol) context.getExtraArgs().toMap().get("BOT_PROTOCOL"));
                if (!QVersionConst.MAIN_VERSION.equals(protocol.getVer())) {
                    getLogger().info("Version not match: Found: " + protocol.getVer() + " but expected " + QVersionConst.MAIN_VERSION);
                    throw EncryptService.SignalServiceNotAvailable.INSTANCE;
                }

                return serviceFactory.load(context, serviceSubScope);
            });

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    @Override
    public void onDisable() {
        GlobalShared.SCHEDULED_EXECUTOR_SERVICE.shutdown();
    }
}
