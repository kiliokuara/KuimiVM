package tencentlibfekit.vmservice.rpc;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import kfc.vivo50.code45.ServiceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tencentlibfekit.BasePath;
import tencentlibfekit.FEKitNativeIOFiles;
import tencentlibfekit.common.GlobalShared;
import tencentlibfekit.common.HexByteArraySerializer;
import tencentlibfekit.proto.DeviceInfoProto;
import tencentlibfekit.proto.QVersionConst;
import tencentlibfekit.structloader.ExtForceLoadStructLoader;
import tencentlibfekit.structloader.StructLoader;
import tencentlibfekit.structloader.StubTransformStructLoader;
import tencentlibfekit.vmservice.IChannelProxy;
import tencentlibfekit.vmservice.VMService;

import javax.crypto.SecretKey;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.security.PublicKey;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class RpcServerServiceSession {
    public volatile long lastAccessTime;
    public String sessionKey;
    public SecretKey sharedKey;
    public long botid;
    public ConcurrentLinkedQueue<ServerWebSocket> wsSessions = new ConcurrentLinkedQueue<>();
    public PublicKey clientRsa;
    public VMService vmService;

    private final Map<String, Promise<JsonObject>> commandResultPromise = new ConcurrentHashMap<>();
    private final AtomicBoolean vmInitialized = new AtomicBoolean();
    private final AtomicBoolean vmClosed = new AtomicBoolean(false);

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServerServiceSession.class);

    public void invalidate() {
        LOGGER.info("session of bot {} is invalidated.", botid);

        wsSessions.removeIf(it -> {
            it.close();
            return true;
        });
        commandResultPromise.values().removeIf(p -> {
            p.fail("Cancelled");
            lastAccessTime = -1;
            return true;
        });

        if (vmClosed.compareAndSet(false, true)) {

            try {
                vmService.emulator.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void bootVM() {
        if (vmService != null) return;
        synchronized (this) {
            if (vmService != null) return;

            try {

                var nativeIOFiles = new FEKitNativeIOFiles();

                var isLocal = ServiceSelector.isLocal();
                File botWork;
                StructLoader structLoader;

                if (isLocal) {
                    nativeIOFiles.linuxFiles = new File("linuxfile");
                    nativeIOFiles.apkFile = new File(BasePath.APK_PATH);
                    nativeIOFiles.userData = new File(BasePath.userDataPath);
                    nativeIOFiles.soFile = new File(BasePath.SO);
                    botWork = new File("testbot/" + botid);
                    botWork.mkdirs();

                    structLoader = new ExtForceLoadStructLoader(new File(BasePath.STRUCT_DEF), true);
                } else {
                    nativeIOFiles.linuxFiles = new File("serverData/resources/linuxfile");
                    nativeIOFiles.soFile = GlobalShared.MOBILEQQ_DOWNLOAD_RESULT.fekit();
                    nativeIOFiles.apkFile = GlobalShared.MOBILEQQ_DOWNLOAD_RESULT.apk();
                    botWork = new File("testbot/" + botid);
                    nativeIOFiles.userData = new File(botWork, "userData");
                    nativeIOFiles.userData.mkdirs();

                    structLoader = new StubTransformStructLoader();
                }

                vmService = new VMService(
                        botWork,
                        structLoader,
                        nativeIOFiles, true, botid
                );

                LOGGER.info("starting vm service of bot {}, local debug = {}", botid, isLocal);
            } catch (Throwable throwable) {
                throw new RuntimeException(throwable);
            }
        }
    }

    private byte[] encodeCommand(JsonObject jsonObject) {

        var baos = new ByteArrayOutputStream();
        try (var writer = new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
            GlobalShared.GSON.toJson(jsonObject, writer);
        } catch (IOException ioException) {
            ioException.printStackTrace();
        }
        return RpcServerBootstrap.aesEncrypt(sharedKey, baos.toByteArray());
    }

    public void handleFrame(String txt, ServerWebSocket ws, Vertx vertx) {
        LOGGER.info("[WEBSOCKET] receiving packet from {}: {}", ws.remoteAddress(), txt);

        var json = GlobalShared.GSON.fromJson(txt, JsonObject.class);
        var response = new JsonObject();
        response.add("packetId", json.get("packetId"));
        response.add("packetType", json.get("packetType"));


        this.lastAccessTime = System.currentTimeMillis();

        {
            var pid = json.get("packetId");
            if (pid instanceof JsonPrimitive) {
                var jpx = pid.getAsString();
                var ftp = commandResultPromise.get(jpx);
                if (ftp != null) {
                    ftp.complete(json);
                    return;
                }
            }
        }

        var promise = Promise.promise();
        GlobalShared.SCHEDULED_EXECUTOR_SERVICE.submit(() -> {
            try {
                var pkgType = json.getAsJsonPrimitive("packetType").getAsString();

                switch (pkgType) {
                    case "rpc.initialize" -> {
                        if (vmInitialized.compareAndSet(false, true)) {
                            try {
                                var deviceInf = GlobalShared.GSON.fromJson(
                                        json.get("device"),
                                        DeviceInfoProto.class
                                );

                                {// check protocol version
                                    var botProtocol = json.getAsJsonObject("extArgs").getAsJsonObject("BOT_PROTOCOL");
                                    if (botProtocol == null)
                                        throw new NullPointerException("Missing `BOT_PROTOCOL` in `extArgs`");

                                    var protocolValue = botProtocol.getAsJsonObject("protocolValue");
                                    if (protocolValue == null) {
                                        throw new NullPointerException("$.extArgs.BOT_PROTOCOL.protocolValue");
                                    }
                                    var clientQVersion = protocolValue.getAsJsonPrimitive("ver").getAsString();
                                    if (!QVersionConst.MAIN_VERSION.equals(clientQVersion)) {
                                        throw new IllegalStateException("Protocol version not match: Server support: " + QVersionConst.MAIN_VERSION + ", client request: " + clientQVersion);
                                    }
                                }

                                bootVM();

                                //noinspection unchecked
                                vmService.initialize(
                                        GlobalShared.GSON.fromJson(
                                                json.get("extArgs"),
                                                Map.class
                                        ),
                                        deviceInf,
                                        createChannelProxy(vertx, ws.remoteAddress())
                                );
                            } catch (Throwable throwable) {
                                vmInitialized.set(false);
                                throw throwable;
                            }
                        }
                    }

                    case "rpc.get_cmd_white_list" -> {
                        if (!vmInitialized.get()) {
                            throw new IllegalStateException("vm service is not initialized.");
                        }

                        var list = vmService.getCmdWhiteList();
                        var cmdArray = new JsonArray();

                        list.forEach(cmdArray::add);
                        cmdArray.add("StatSvc.register");
                        response.add("response", cmdArray);
                    }

                    case "rpc.tlv" -> {
                        var rsp = vmService.tlv(
                                json.get("tlvType").getAsInt(),
                                GlobalShared.GSON.fromJson(json.get("extArgs"), Map.class),
                                GlobalShared.GSON.fromJson(json.get("content"), byte[].class)
                        );

                        if (rsp != null) {
                            response.addProperty("response", HexFormat.of().formatHex(rsp));
                        }
                    }
                    case "rpc.sign" -> {
                        var rsp = vmService.sign(
                                json.get("seqId").getAsInt(),
                                json.get("command").getAsString(),
                                GlobalShared.GSON.fromJson(json.get("content"), byte[].class),
                                GlobalShared.GSON.fromJson(json.get("extArgs"), Map.class)
                        );

                        if (rsp != null) {
                            var result = new JsonObject();
                            response.add("response", result);

                            result.addProperty("sign", HexByteArraySerializer.hex(rsp.sign));
                            result.addProperty("extra", HexByteArraySerializer.hex(rsp.extra));
                            result.addProperty("token", HexByteArraySerializer.hex(rsp.token));
                        }
                    }

                    default -> throw new NoStackTraceThrowable("Unknown package " + pkgType);
                }

                promise.complete();
            } catch (Throwable throwable) {
                promise.fail(throwable);
            }
        });

        promise.future().onComplete(result -> {
            if (result.failed()) {
                LOGGER.error("Exception when processing packet", result.cause());
                response.addProperty("packetType", "service.error");
                response.addProperty("message", result.cause().toString());
            }

            var wt = encodeCommand(response);
            LOGGER.info("[WEBSOCKET] respond packet to {}: {}", ws.remoteAddress(), response);
            GlobalShared.SINGLE_EXECUTOR.execute(() -> {
                // ws.writeTextMessage(response.toString());
                ws.writeFinalBinaryFrame(Buffer.buffer(wt));
            });
        });
    }

    private IChannelProxy createChannelProxy(Vertx vertx, SocketAddress clientAddr) {
        return (remark, command, botUin, data, continuation) -> {
            var seqId = randomID();

            LOGGER.info("[WEBSOCKET] sending command {} with seq {} of bot {} to {} by channel proxy: {}",
                    command, seqId, botUin, clientAddr, HexFormat.of().formatHex(data));

            var baos = new ByteArrayOutputStream();
            try (var writer = GlobalShared.GSON.newJsonWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                writer.beginObject();

                writer.name("packetId").value(seqId);
                writer.name("packetType").value("rpc.service.send");
                writer.name("remark").value(remark);
                writer.name("command").value(command);
                writer.name("botUin").value(botUin);
                writer.name("data");
                HexByteArraySerializer.INSTANCE.write(writer, data);


                writer.endObject();
            } catch (Exception e) {
                e.printStackTrace();
            }


            broadcastCommand(baos.toByteArray(), vertx, seqId).onComplete(result -> {
                if (result.failed()) {
                    LOGGER.warn("[WEBSOCKET] error receiving command result with seq {}.", seqId, result.cause());
                    continuation.resumeWith(kotlin.ResultKt.createFailure(result.cause()));
                } else {
                    var jsonObject = result.result();
                    var cmdResult = jsonObject.getAsJsonPrimitive("data").getAsString();
                    LOGGER.info("[WEBSOCKET] receiving command result with seq {}: {}", seqId, cmdResult);

                    GlobalShared.SCHEDULED_EXECUTOR_SERVICE.execute(() -> {
                        continuation.resumeWith(new IChannelProxy.ChannelResult(
                                jsonObject.getAsJsonPrimitive("command").getAsString(),
                                HexFormat.of().parseHex(cmdResult)
                        ));
                    });
                }
            });

            return kotlin.coroutines.intrinsics.IntrinsicsKt.getCOROUTINE_SUSPENDED();
        };
    }

    private String randomID() {
        return "server-" + UUID.randomUUID() + "-" + UUID.randomUUID();
    }

    private Future<JsonObject> broadcastCommand(byte[] command, Vertx vertx, String packetId) {
        var enc = RpcServerBootstrap.aesEncrypt(sharedKey, command);

        return vertx.executeBlocking(promise -> {
            var tsk = GlobalShared.SINGLE_EXECUTOR.scheduleWithFixedDelay(() -> {
                for (var ws : wsSessions) ws.writeFinalBinaryFrame(Buffer.buffer(enc));
            }, 1, 10, TimeUnit.SECONDS);


            var callbackPromise = Promise.<JsonObject>promise();
            commandResultPromise.put(packetId, callbackPromise);

            callbackPromise.future().onComplete(result -> {
                tsk.cancel(true);
                commandResultPromise.remove(packetId, callbackPromise);
                promise.handle(result);
            });
        });
    }

    public boolean verify(RoutingContext req) {
        var headers = req.request().headers();

        var timeStr = headers.get(RpcServerBootstrap.HEADER_X_TIME);
        var signStr = headers.get(RpcServerBootstrap.HEADER_X_SIGN);
        try {
            var crtTime = System.currentTimeMillis();
            var time = Long.parseLong(timeStr);

            if (Math.abs(crtTime - time) > TimeUnit.SECONDS.toMillis(15)) {
                req.response().setStatusCode(403).end("Client request time not match with server time: client: " + time + ", server: " + crtTime + ", diff: " + (time - crtTime));

                return false;
            }

            if (RpcServerBootstrap.verify(clientRsa, timeStr.getBytes(), signStr)) {
                return true;
            }

            req.response().setStatusCode(403).end("Failed to verify client rsa key");
            return false;

        } catch (Throwable throwable) {
            req.response().setStatusCode(403).end(throwable.getLocalizedMessage());
            return false;
        }
    }
}
