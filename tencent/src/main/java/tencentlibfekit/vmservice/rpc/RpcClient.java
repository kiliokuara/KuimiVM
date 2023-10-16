package tencentlibfekit.vmservice.rpc;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonWriter;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.http.WebSocketConnectOptions;
import io.vertx.core.impl.NoStackTraceThrowable;
import io.vertx.core.json.Json;
import kotlin.Result;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.Job;
import net.mamoe.mirai.internal.spi.EncryptServiceContext;
import net.mamoe.mirai.internal.utils.MiraiProtocolInternal;
import net.mamoe.mirai.utils.BotConfiguration;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tencentlibfekit.ConsumerCatch;
import tencentlibfekit.FEKitEncryptService;
import tencentlibfekit.common.GlobalShared;
import tencentlibfekit.proto.DeviceInfoProto;
import tencentlibfekit.vmservice.IChannelProxy;
import tencentlibfekit.vmservice.IVMService;
import tencentlibfekit.vmservice.VMSignResult;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static tencentlibfekit.common.GlobalShared.*;
import static tencentlibfekit.vmservice.rpc.RpcServerBootstrap.sha1;

public class RpcClient implements IVMService {
    private final long botid;
    private final String sharedKey;
    private final SecretKey sharedAESKey;
    private final KeyPair clientRsaKeypair;
    private final CoroutineScope serviceScope;
    private final String serverIdentityKey;

    private String wsToken;

    private final URI mBasePath;
    private final String mAuthKey;

    private IChannelProxy channelProxy;
    private final Map<String, CompletableFuture<JsonObject>> awaits = new ConcurrentHashMap<>();
    private final Map<String, Long> processedCommands = new ConcurrentHashMap<>();
    private final Vertx vertx;
    private final HttpClient httpClient;
    private WebSocket webSocket;

    private final HashMap<String, Boolean> commandWhiteList = new HashMap<>(87);

    private static final Logger LOGGER = LoggerFactory.getLogger(RpcClient.class);

    private void processRemoteResp(String resp) {
        var decj = GSON.fromJson(resp, JsonObject.class);
        var pkgid = decj.getAsJsonPrimitive("packetId");
        var pkgType = decj.getAsJsonPrimitive("packetType").getAsString();

        LOGGER.info("[HTTP WEBSOCKET] receiving packet: id = {}, type = {}", pkgid, pkgType);
        LOGGER.debug("[HTTP WEBSOCKET] receiving packet: id = {}, type = {}, content = {}", pkgid, pkgType, decj);

        if (pkgid != null) {
            var rsp = awaits.remove(pkgid.getAsString());
            if (rsp != null) {
                rsp.complete(decj);
                return;
            }
        }
        if ("rpc.service.send".equals(pkgType)) {
            sendMsgImpl(decj);
        }
    }

    private void sendMsgImpl(JsonObject decj) {
        var pkid = decj.get("packetId").getAsString();
        var prevTime = processedCommands.put(pkid, System.currentTimeMillis());
        if (prevTime != null) {
            // processed
            LOGGER.info("[HTTP WEBSOCKET] skipped sending rpc.service.send packet {}, processed at {}", pkid, prevTime);
            return;
        }

        var cont = new Continuation<IChannelProxy.ChannelResult>() {
            @Override
            public void resumeWith(@NotNull Object o) {
                if (o instanceof kotlin.Result.Failure) {
                    onCancelled(((Result.Failure) o).exception);
                } else {
                    onCompleted((IChannelProxy.ChannelResult) o);
                }
            }

            @NotNull
            @Override
            public CoroutineContext getContext() {
                return EmptyCoroutineContext.INSTANCE;
            }

            void onCancelled(@NotNull Throwable cause) {
                cause.printStackTrace();
            }

            void onCompleted(IChannelProxy.ChannelResult value) {
                var baos = new ByteArrayOutputStream();
                var data = HexFormat.of().formatHex(value.data());

                try (var writer = GSON.newJsonWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
                    writer.beginObject();

                    writer.name("packetId").value(pkid);
                    writer.name("packetType").value("rpc.service.send");
                    writer.name("command").value(value.command());
                    writer.name("data").value(data);
                    writer.endObject();
                } catch (Exception e) {
                    e.printStackTrace();
                }


                GlobalShared.SCHEDULED_EXECUTOR_SERVICE.execute(() -> {
                    var packet = baos.toByteArray();
                    LOGGER.info("[HTTP WEBSOCKET] sending packet: id = {}, type = rpc.service.send", pkid);
                    LOGGER.debug("[HTTP WEBSOCKET] sending packet: id = {}, type = rpc.service.send, content = {}", pkid, data);
                    sendAndExcept(null, packet);
                });

            }
        };

        SCHEDULED_EXECUTOR_SERVICE.execute(() -> {
            var rsp = channelProxy.sendMessage(
                    decj.getAsJsonPrimitive("remark").getAsString(),
                    decj.getAsJsonPrimitive("command").getAsString(),
                    decj.getAsJsonPrimitive("botUin").getAsLong(),
                    HexFormat.of().parseHex(decj.getAsJsonPrimitive("data").getAsString()),
                    cont
            );
            if (rsp != IChannelProxy.SUSPENDED) {
                cont.onCompleted((IChannelProxy.ChannelResult) rsp);
            }
        });

    }

    public RpcClient(URI basePath, EncryptServiceContext context, CoroutineScope serviceSubScope, String authKey, String serverIdentityKey) {
        this.mBasePath = basePath;
        this.botid = context.getId();
        this.serviceScope = serviceSubScope;

        sharedKey = UUID.randomUUID().toString().substring(0, 16);
        sharedAESKey = new SecretKeySpec(sharedKey.getBytes(), "AES");
        mAuthKey = authKey;
        this.serverIdentityKey = serverIdentityKey;

        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(4096);
            this.clientRsaKeypair = generator.generateKeyPair();
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }

        this.vertx = Vertx.vertx();
        this.httpClient = vertx.createHttpClient();
        initBuiltinCommandWhiteList();

        //noinspection unchecked
        var job = (Job) serviceSubScope.getCoroutineContext().get((CoroutineContext.Key<? extends CoroutineContext.Element>) Job.Key);
        assert job != null;


        var emptyBuf = Buffer.buffer();
        var timerKeep = GlobalShared.SCHEDULED_EXECUTOR_SERVICE.scheduleWithFixedDelay(() -> {
            var now = System.currentTimeMillis();
            processedCommands.entrySet().removeIf(it -> now - it.getValue() > 60_000L);

            try {
                webSocket.writePing(emptyBuf);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, 5, 15, TimeUnit.SECONDS);

        job.invokeOnCompletion(err -> {
            timerKeep.cancel(false);
            invalidate();
            return Unit.INSTANCE;
        });
//
    }

    public static void registerService(String serverIdentityKey) {
        FEKitEncryptService.registerService((context, serviceSubScope) -> {
            return new RpcClient(new URI("http://localhost:8888/"), context, serviceSubScope, "1234567890", serverIdentityKey);
        });
    }

    @Override
    public VMSignResult sign(int seqId, String cmdName, byte[] data, Map<String, Object> extArgs) {
        if (!commandWhiteList.getOrDefault(cmdName, false)) return null;

        try {
            var hexData = HexFormat.of().formatHex(data);

            LOGGER.info("[HTTP WEBSOCKET] sending packet: type = rpc.sign, seqId = {}, command = {}", seqId, cmdName);
            LOGGER.debug("[HTTP WEBSOCKET] sending packet: type = rpc.sign, seqId = {}, command = {}, content = {}, extras = {}", seqId, cmdName, hexData, extArgs);

            var rsp = assertNoError(sendAndExcept(writer -> {
                writer.name("packetType").value("rpc.sign");
                writer.name("seqId").value(seqId);
                writer.name("command").value(cmdName);
                writer.name("extArgs");
                writeExtArgs(writer, extArgs);

                writer.name("content").value(hexData);
            }).get()).get("response");

            if (rsp == null || rsp instanceof JsonNull)
                return null;

            var response = rsp.getAsJsonObject();

            var result = new VMSignResult();

            result.sign = GSON.fromJson(response.get("sign"), byte[].class);
            result.extra = GSON.fromJson(response.get("extra"), byte[].class);
            result.token = GSON.fromJson(response.get("token"), byte[].class);

            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] tlv(int type, Map<String, Object> extArgs, byte[] content) {
        try {
            var hexData = HexFormat.of().formatHex(content);
            LOGGER.info("[HTTP WEBSOCKET] sending packet: type = rpc.tlv, type = {}", type);
            LOGGER.debug("[HTTP WEBSOCKET] sending packet: type = rpc.tlv, type = {}, content = {}, extras = {}", type, hexData, extArgs);

            var rsp = assertNoError(sendAndExcept(writer -> {
                writer.name("packetType").value("rpc.tlv");
                writer.name("tlvType").value(type);
                writer.name("extArgs");
                writeExtArgs(writer, extArgs);

                writer.name("content").value(hexData);
            }).get()).get("response");

            if (rsp == null || rsp instanceof JsonNull)
                return null;

            return HexFormat.of().parseHex(rsp.getAsString());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void invalidate() {
        LOGGER.info("[HTTP WEBSOCKET] current websocket {} is invalidated.", webSocket);
        if (webSocket != null) {
            webSocket.close();
        }
        vertx.close();
        awaits.values().removeIf(it -> {
            it.completeExceptionally(new NoStackTraceThrowable("Invalidated"));
            return true;
        });
        channelProxy = null;
    }

    private String timeSign(String current) {
        return RpcServerBootstrap.sign(clientRsaKeypair, current.getBytes());
    }

    private void fetchToken() throws Exception {
        if (wsToken != null) {
            var current = String.valueOf(System.currentTimeMillis());

            LOGGER.info("[HTTP GET] checking current session.");
            var statusCheck = HTTP_CLIENT.send(
                    HttpRequest.newBuilder()
                            .GET().uri(mBasePath.resolve("service/rpc/session/check"))
                            .header("Authorization", wsToken)
                            .header(RpcServerBootstrap.HEADER_X_TIME, current)
                            .header(RpcServerBootstrap.HEADER_X_SIGN, timeSign(current))
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray()
            );
            if (statusCheck.statusCode() / 100 == 2) return;
            wsToken = null;
        }

        LOGGER.info("[HTTP GET] retrieving handshake config.");
        var configRsp = HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                        .GET().uri(mBasePath.resolve("service/rpc/handshake/config"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        if (configRsp.statusCode() != 200) {
            throw new IllegalStateException("Failed to fetch handshake config: " + configRsp);
        }


        String pubKeyStr;
        {
            var jo = GSON.fromJson(new String(configRsp.body(), StandardCharsets.UTF_8), JsonObject.class);
            pubKeyStr = jo.getAsJsonPrimitive("publicKey").getAsString();

            if (this.serverIdentityKey != null && !this.serverIdentityKey.isEmpty()) {
                var keySignature = jo.getAsJsonPrimitive("keySignature").getAsString();

                var hexFormat = HexFormat.of().withLowerCase();
                // $result = $sha1(
                //      $sha1( ($preKnownKey + $base64ServerPublicKey).getBytes() ).hex() + $preKnownKey
                // ).hex()
                var envIdentityKey = serverIdentityKey;

                var pkeyRsaKeySha1 = sha1((envIdentityKey + pubKeyStr).getBytes());

                var result = sha1(
                        (hexFormat.formatHex(pkeyRsaKeySha1) + envIdentityKey).getBytes()
                );
                var clientCalcSign = hexFormat.formatHex(result);

                if (!clientCalcSign.equals(keySignature)) {
                    throw new IllegalStateException("Not match: Client calc: " + clientCalcSign + ", server: " + keySignature);
                }
            }
        }

        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyStr.getBytes())));
        LOGGER.info("rsa public key is constructed: {}", publicKey);

        var toolkit = new Object() {
            byte[] encode(byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
                var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                return cipher.doFinal(data);
            }
        };


        LOGGER.info("[HTTP POST] doing handshake with server.");
        var handshakeRsp = HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                        .uri(mBasePath.resolve("service/rpc/handshake/handshake"))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(
                                Json.encodeToBuffer(
                                        new io.vertx.core.json.JsonObject()
                                                .put("secret", Base64.getEncoder().encodeToString(toolkit.encode(Json.encodeToBuffer(
                                                        new io.vertx.core.json.JsonObject()
                                                                .put("authorizationKey", mAuthKey)
                                                                .put("sharedKey", sharedKey)
                                                                .put("botid", botid)
                                                ).getBytes())))
                                                .put("clientRsa", Base64.getEncoder().encodeToString(clientRsaKeypair.getPublic().getEncoded()))
                                ).getBytes()
                        ))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        var handshakeResponse = new String(handshakeRsp.body(), StandardCharsets.UTF_8);
        if (handshakeRsp.statusCode() != 200) {
            throw new IllegalStateException("Failed to handshake: " + handshakeRsp + ", " + handshakeResponse);
        }

        var tok = GSON.fromJson(handshakeResponse, JsonObject.class).getAsJsonPrimitive("token").getAsString();
        this.wsToken = new String(Base64.getDecoder().decode(tok));
    }

    private void loadWS() throws Exception {
        fetchToken();

        if (!(webSocket == null || webSocket.isClosed())) {
            return;
        }
        var current = String.valueOf(System.currentTimeMillis());

        var wsOptions = new WebSocketConnectOptions();
        wsOptions.setURI("/service/rpc/session");
        wsOptions.setHost(mBasePath.getHost());
        wsOptions.setPort(mBasePath.getPort());
        wsOptions.setSsl(mBasePath.getScheme().equals("https"));
        wsOptions.addHeader("Authorization", wsToken);
        wsOptions.addHeader(RpcServerBootstrap.HEADER_X_TIME, current);
        wsOptions.addHeader(RpcServerBootstrap.HEADER_X_SIGN, timeSign(current));

        this.webSocket = httpClient.webSocket(wsOptions).toCompletionStage().toCompletableFuture().get();
        LOGGER.info("[HTTP WEBSOCKET] websocket session is established.");

        webSocket.closeHandler($ -> {
            webSocket = null;
            reloadWS();
        });
        webSocket.binaryMessageHandler(msg -> {
            var dec = RpcServerBootstrap.aesDecrypt(sharedAESKey, msg.getBytes());
            processRemoteResp(new String(dec, StandardCharsets.UTF_8));
        });
    }

    private void reloadWS() {
        try {
            loadWS();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private CompletableFuture<JsonObject> sendAndExcept(ConsumerCatch<JsonWriter> action) {
        return sendAndExcept(action, 10000);
    }

    private CompletableFuture<JsonObject> sendAndExcept(ConsumerCatch<JsonWriter> action, int timeout) {
        var baos = new ByteArrayOutputStream();
        var pseq = UUID.randomUUID().toString();

        try (var writer = GSON.newJsonWriter(new OutputStreamWriter(baos, StandardCharsets.UTF_8))) {
            writer.beginObject();
            writer.name("packetId").value(pseq);

            action.consume(writer);

            writer.endObject();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return sendAndExcept(pseq, baos.toByteArray(), timeout);
    }

    private CompletableFuture<JsonObject> sendAndExcept(String seq, byte[] data) {
        return sendAndExcept(seq, data, 10000);
    }

    private CompletableFuture<JsonObject> sendAndExcept(String seq, byte[] data, int timeout) {
        var rsp = seq == null ? null : new CompletableFuture<JsonObject>();


        var enced = RpcServerBootstrap.aesEncrypt(sharedAESKey, data);

        try {
            loadWS();
        } catch (Exception e) {
            if (seq == null) throw new RuntimeException(e);

            rsp.completeExceptionally(e);
            return rsp;
        }

        if (seq != null) {
            awaits.put(seq, rsp);
            if (timeout != 0) {
                SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
                    if (!rsp.isDone()) {
                        rsp.completeExceptionally(new TimeoutException("Timed out sending " + seq));
                    }
                }, timeout, TimeUnit.MILLISECONDS);
            }
        }

        webSocket.writeFinalBinaryFrame(Buffer.buffer(enced));

        return rsp;
    }

    private static JsonObject assertNoError(JsonObject rsp) {
        if (rsp.getAsJsonPrimitive("packetType").getAsString().equals("service.error")) {
            throw new IllegalStateException(rsp.toString());
        }
        return rsp;
    }

    @Override
    public void initialize(Map<String, Object> args, DeviceInfoProto deviceInfoProto, IChannelProxy channelProxy) {
        try {
            this.channelProxy = channelProxy;
            LOGGER.info("initializing encrypt service.");

            loadWS();

            assertNoError(sendAndExcept(writer -> {
                writer.name("packetType").value("rpc.initialize");
                writer.name("device");
                GSON.getAdapter(DeviceInfoProto.class).write(writer, FEKitEncryptService.convertDeviceInfo(args.get("KEY_DEVICE_INFO")));

                writer.name("extArgs");

                writeExtArgs(writer, args);
            }, 120_000).get());

            var cmdWhiteListResp = assertNoError(sendAndExcept(writer -> {
                writer.name("packetType").value("rpc.get_cmd_white_list");
            }).get()).get("response");

            if (cmdWhiteListResp == null || cmdWhiteListResp.isJsonNull()) {
                LOGGER.warn("cannot get command white list, response is null");
                return;
            }

            try {
                var list = GSON.fromJson(cmdWhiteListResp, String[].class);
                if (list.length > 0) refreshCommandWhiteList(list);
                LOGGER.info("get cmd white list success, size = {}", list.length);
            } catch (Exception e) {
                LOGGER.warn("cannot get command white list", e);
            }

        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    private static void writeExtArgs(JsonWriter writer, Map<String, Object> args) throws IOException {
        writer.beginObject();

        for (var entry : args.entrySet()) {
            if (entry.getValue() instanceof Boolean) {
                writer.name(entry.getKey()).value((Boolean) entry.getValue());
            }

            if (entry.getValue() instanceof String) {
                writer.name(entry.getKey()).value((String) entry.getValue());
            }

            if (entry.getValue() instanceof Number) {
                writer.name(entry.getKey()).value((Number) entry.getValue());
            }

            if (entry.getValue() instanceof BotConfiguration.MiraiProtocol) {
                writer.name(entry.getKey()).beginObject();

                var mprotocol = (BotConfiguration.MiraiProtocol) entry.getValue();
                writer.name("protocolName").value(mprotocol.name());
                var internal = MiraiProtocolInternal.Companion.get(mprotocol);
                writer.name("protocolValue");

                GSON.toJson(internal, MiraiProtocolInternal.class, writer);

                writer.endObject();
            }
        }

        writer.endObject();

    }

    private void refreshCommandWhiteList(String[] cmds) {
        commandWhiteList.clear();
        for (String cmd : cmds) {
            commandWhiteList.put(cmd, true);
        }
    }

    private void initBuiltinCommandWhiteList() {
        refreshCommandWhiteList(new String[]{
                "ConnAuthSvr.fast_qq_login",
                "ConnAuthSvr.sdk_auth_api",
                "ConnAuthSvr.sdk_auth_api_emp",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoBarrage",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoComment",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoFollow",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoLike",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoPush",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.DoReply",
                "FeedCloudSvr.trpc.feedcloud.commwriter.ComWriter.PublishFeed",
                "FeedCloudSvr.trpc.videocircle.circleprofile.CircleProfile.SetProfile",
                "friendlist.addFriend",
                "friendlist.AddFriendReq",
                "friendlist.ModifyGroupInfoReq",
                "MessageSvc.PbSendMsg",
                "MsgProxy.SendMsg",
                "OidbSvc.0x4ff_9",
                "OidbSvc.0x4ff_9_IMCore",
                "OidbSvc.0x56c_6",
                "OidbSvc.0x6d9_4",
                "OidbSvc.0x758",
                "OidbSvc.0x758_0",
                "OidbSvc.0x758_1",
                "OidbSvc.0x88d_0",
                "OidbSvc.0x89a_0",
                "OidbSvc.0x89b_1",
                "OidbSvc.0x8a1_0",
                "OidbSvc.0x8a1_7",
                "OidbSvc.0x8ba",
                "OidbSvc.0x9fa",
                "OidbSvc.oidb_0x758",
                "OidbSvcTrpcTcp.0x101e_1",
                "OidbSvcTrpcTcp.0x101e_2",
                "OidbSvcTrpcTcp.0x1100_1",
                "OidbSvcTrpcTcp.0x1105_1",
                "OidbSvcTrpcTcp.0x1107_1",
                "OidbSvcTrpcTcp.0x55f_0",
                "OidbSvcTrpcTcp.0x6d9_4",
                "OidbSvcTrpcTcp.0xf55_1",
                "OidbSvcTrpcTcp.0xf57_1",
                "OidbSvcTrpcTcp.0xf57_106",
                "OidbSvcTrpcTcp.0xf57_9",
                "OidbSvcTrpcTcp.0xf65_1",
                "OidbSvcTrpcTcp.0xf65_10",
                "OidbSvcTrpcTcp.0xf67_1",
                "OidbSvcTrpcTcp.0xf67_5",
                "OidbSvcTrpcTcp.0xf6e_1",
                "OidbSvcTrpcTcp.0xf88_1",
                "OidbSvcTrpcTcp.0xf89_1",
                "OidbSvcTrpcTcp.0xfa5_1",
                "ProfileService.getGroupInfoReq",
                "ProfileService.GroupMngReq",
                "QChannelSvr.trpc.qchannel.commwriter.ComWriter.DoComment",
                "QChannelSvr.trpc.qchannel.commwriter.ComWriter.DoReply",
                "QChannelSvr.trpc.qchannel.commwriter.ComWriter.PublishFeed",
                "qidianservice.135",
                "qidianservice.207",
                "qidianservice.269",
                "qidianservice.290",
                "SQQzoneSvc.addComment",
                "SQQzoneSvc.addReply",
                "SQQzoneSvc.forward",
                "SQQzoneSvc.like",
                "SQQzoneSvc.publishmood",
                "SQQzoneSvc.shuoshuo",
                "trpc.group_pro.msgproxy.sendmsg",
                "trpc.login.ecdh.EcdhService.SsoNTLoginPasswordLoginUnusualDevice",
                "trpc.o3.ecdh_access.EcdhAccess.SsoEstablishShareKey",
                "trpc.o3.ecdh_access.EcdhAccess.SsoSecureA2Access",
                "trpc.o3.ecdh_access.EcdhAccess.SsoSecureA2Establish",
                "trpc.o3.ecdh_access.EcdhAccess.SsoSecureAccess",
                "trpc.o3.report.Report.SsoReport",
                "trpc.passwd.manager.PasswdManager.SetPasswd",
                "trpc.passwd.manager.PasswdManager.VerifyPasswd",
                "trpc.qlive.relationchain_svr.RelationchainSvr.Follow",
                "trpc.qlive.word_svr.WordSvr.NewPublicChat",
                "trpc.qqhb.qqhb_proxy.Handler.sso_handle",
                "trpc.springfestival.redpacket.LuckyBag.SsoSubmitGrade",
                "wtlogin.device_lock",
                "wtlogin.exchange_emp",
                "wtlogin.login",
                "wtlogin.name2uin",
                "wtlogin.qrlogin",
                "wtlogin.register",
                "wtlogin.trans_emp",
                "wtlogin_device.login",
                "wtlogin_device.tran_sim_emp",
        });
    }
}
