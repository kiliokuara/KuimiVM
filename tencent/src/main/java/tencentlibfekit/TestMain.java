package tencentlibfekit;

import net.mamoe.mirai.Bot;
import net.mamoe.mirai.BotFactory;
import net.mamoe.mirai.event.events.MessageEvent;
import net.mamoe.mirai.event.events.MessageSyncEvent;
import net.mamoe.mirai.internal.QQAndroidBot;
import net.mamoe.mirai.internal.network.components.EncryptServiceHolder;
import net.mamoe.mirai.internal.network.components.PacketCodec;
import net.mamoe.mirai.message.data.*;
import net.mamoe.mirai.utils.BotConfiguration;
import net.mamoe.mirai.utils.ExternalResource;
import org.jetbrains.annotations.NotNull;
import tencentlibfekit.structloader.ExtForceLoadStructLoader;
import tencentlibfekit.vmservice.VMService;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class TestMain {
    private static Bot bot;
    private static Thread botThread;
    public static final Proxy LOCAL_PROXY = new Proxy(Proxy.Type.HTTP, new InetSocketAddress("127.0.0.1", 15872));
    public static final ScheduledExecutorService SCHEDULED_EXECUTOR_SERVICE = Executors.newScheduledThreadPool(2, new ThreadFactory() {
        private final AtomicInteger counter = new AtomicInteger();

        @Override
        public Thread newThread(@NotNull Runnable r) {
            var t = new Thread(r, "thread - " + counter.getAndIncrement());
            t.setDaemon(true);
            return t;
        }
    });

    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .proxy(new ProxySelector() {
                @Override
                public List<Proxy> select(URI uri) {

                    if (uri.getHost().endsWith(".cn")) return List.of(Proxy.NO_PROXY);
                    if (uri.getHost().startsWith("192.168.")) return List.of(Proxy.NO_PROXY);
                    if (uri.getHost().equals("localhost")) return List.of(Proxy.NO_PROXY);


                    return List.of(LOCAL_PROXY);
                }

                @Override
                public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                    System.out.println("Failed to connect " + uri);
                    System.out.println("Addr: " + sa);
                    ioe.printStackTrace(System.out);
                }
            })
            .followRedirects(HttpClient.Redirect.NORMAL)
            .executor(SCHEDULED_EXECUTOR_SERVICE)
            .build();


    public static void main(String[] args) throws Throwable {
        System.setProperty("mirai.network.packet.logger", "true");
        System.setProperty("mirai.event.show.verbose.events", "true");
        System.setProperty("mirai.network.state.observer.logging", "full");
        System.setProperty("mirai.network.handle.selector.logging", "true");
        System.setProperty("mirai.network.handler.selector.logging", "true");
        System.setProperty("mirai.resource.creation.stack.enabled", "true");

        PacketCodec.Companion.getPacketLogger$mirai_core().enable();

        //需要为实际可读写的目录 会自动创建文件夹
        System.setProperty("KuimiAbstractVM.UserPath", BasePath.userDataPath);
        {

            var nativeIOFiles = new FEKitNativeIOFiles();
            nativeIOFiles.linuxFiles = new File("linuxfile");
            nativeIOFiles.apkFile = new File(BasePath.APK_PATH);
            nativeIOFiles.userData = new File(BasePath.userDataPath);
            nativeIOFiles.soFile = new File(BasePath.SO);

            FEKitEncryptService.registerService((context, serviceSubScope) -> new VMService(
                    new File("testbot"),
                    new ExtForceLoadStructLoader(new File(BasePath.STRUCT_DEF), true),
                    nativeIOFiles, true, BasePath.BOT_ACCOUNT
            ));
//            RpcClient.registerService();
        }
        bot = BotFactory.INSTANCE.newBot(BasePath.BOT_ACCOUNT, BasePath.BOT_PASSWORD, config -> {
            config.setWorkingDir(new File("testbot"));
            config.fileBasedDeviceInfo("device.json");
            config.setProtocol(BotConfiguration.MiraiProtocol.ANDROID_PHONE);
        });
        botThread = new Thread(bot::login);
        botThread.start();
        botThread.join();
        /*
        var result = libFekit.sign(
                BasePath.BOT_ACCOUNT,
                114514,
                HexFormat.ofDelimiter(" ").parseHex("11 45 14 19 19 8D 11 45 AA AC 12 87 19 64 48 a7 11 f6 07 ac 60 cc ca 10 41 87 12 45 49 87 98 97 5a 87 49 86 5c 98 78 e5 b8 78 91 25 49 83 55 23 b1 21 3e 23 a6 54 5c 46 54 65 b4 65 46 54 65 4e 65"),
                "MessageSvc.PbSendMsg"
        );
        System.out.println(result);*/

        bot.getEventChannel().subscribeAlways(MessageEvent.class, evt -> {
            if (evt instanceof MessageSyncEvent) return;

            var msgList = evt.getMessage().stream()
                    .filter(it -> it instanceof MessageContent)
                    .filter(it -> !(it instanceof UnsupportedMessage))
                    .collect(Collectors.toCollection(ArrayList::new));


            var subject = evt.getSubject();
            var fullMsgString = evt.getMessage().contentToString().strip();
            var fullMsg = evt.getMessage();


            String commandPrefix;
            Message remaining;
            if (msgList.isEmpty()) {
                commandPrefix = "";
                remaining = MessageUtils.emptyMessageChain();
            } else {
                var firstElm = msgList.get(0);
                if (firstElm instanceof At) {
                    if (msgList.size() > 1) {
                        msgList.remove(0);
                        firstElm = msgList.get(0);
                    }
                }

                if (firstElm instanceof PlainText plainText) {
                    var textContent = plainText.getContent().strip();
                    var spaceIndex = textContent.indexOf(' ');
                    if (spaceIndex == -1) {
                        commandPrefix = textContent;
                        msgList.remove(0);
                        remaining = MessageUtils.newChain(msgList);
                    } else {
                        commandPrefix = textContent.substring(0, spaceIndex);
                        msgList.set(0, new PlainText(textContent.substring(spaceIndex + 1)));
                        remaining = MessageUtils.newChain(msgList);
                    }
                } else {
                    commandPrefix = "";
                    remaining = MessageUtils.emptyMessageChain();
                }
            }

            System.out.println("CMD: " + commandPrefix + ", " + remaining);


            switch (commandPrefix) {
            }
        });
    }
}
