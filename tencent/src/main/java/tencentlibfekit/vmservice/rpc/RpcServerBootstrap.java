package tencentlibfekit.vmservice.rpc;

import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.impl.Utils;
import kfc.vivo50.code45.ServiceSelector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tencentlibfekit.MemoryDumper;
import tencentlibfekit.common.GlobalShared;
import tencentlibfekit.common.ResourceExtract;
import tencentlibfekit.proto.QVersionConst;

import javax.crypto.*;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.lang.reflect.Array;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Function;

public class RpcServerBootstrap {
    public static final Map<String, RpcServerServiceSession> SESSION_CACHE = new ConcurrentHashMap<>();
    private static ByteBuffer authorizationKeyBuf;
    private static final Logger LOGGER = LoggerFactory.getLogger(RpcServerBootstrap.class);

    public static final String HEADER_X_TIME = "X-SEC-Time";
    public static final String HEADER_X_SIGN = "X-SEC-Signature";

    public static <T> Function<T, Future<Void>> process(Consumer<T> processor) {
        return v -> {
            processor.accept(v);
            return Future.succeededFuture();
        };
    }

    public static byte[] rsaEncrypt(KeyPair keypair, byte[] content) {
        return rsaEncrypt(keypair.getPublic(), content);
    }

    public static byte[] rsaEncrypt(PublicKey publicKey, byte[] content) {
        try {
            var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.ENCRYPT_MODE, publicKey);
            return cipher.doFinal(content);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] rsaDecrypt(KeyPair keypair, byte[] content) {
        try {
            var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
            cipher.init(Cipher.DECRYPT_MODE, keypair.getPrivate());
            return cipher.doFinal(content);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | IllegalBlockSizeException |
                 BadPaddingException | InvalidKeyException e) {
            throw new RuntimeException(e);
        }
    }

    public static byte[] aesEncrypt(SecretKey key, byte[] content) {
        try {
            var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            return cipher.doFinal(content);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String sign(KeyPair keypair, byte[] data) {
        try {
            var privateSignature = Signature.getInstance("SHA256withRSA");
            privateSignature.initSign(keypair.getPrivate());
            privateSignature.update(data);
            return Base64.getEncoder().encodeToString(privateSignature.sign());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean verify(KeyPair keypair, byte[] data, String sign) {
        return verify(keypair.getPublic(), data, sign);
    }

    public static boolean verify(PublicKey publicKey, byte[] data, String sign) {
        try {
            Signature publicSignature = Signature.getInstance("SHA256withRSA");
            publicSignature.initVerify(publicKey);
            publicSignature.update(data);

            byte[] signatureBytes = Base64.getDecoder().decode(sign);

            return publicSignature.verify(signatureBytes);
        } catch (Exception e) {
            return false;
        }
    }

    public static byte[] aesDecrypt(SecretKey key, byte[] content) {
        try {
            var cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, key);
            return cipher.doFinal(content);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException | InvalidKeyException | IllegalBlockSizeException |
                 BadPaddingException e) {
            throw new RuntimeException(e + ", " + HexFormat.of().formatHex(content), e);
        }
    }

    public static byte[] sha1(byte[] content) {
        try {
            return MessageDigest.getInstance("SHA1").digest(content);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static RpcServerServiceSession findSessionAndVerifyAndReject(KeyPair keypair, String token, RoutingContext req) {
        String sessionKey;

        tokenFormatCheck:
        {
            label:
            {
                var idx = token.indexOf('.');
                if (idx == -1) break label;

                var raw = token.substring(0, idx);
                var sign = token.substring(idx + 1);

                if (!verify(keypair, raw.getBytes(), sign)) break label;

                sessionKey = raw;
                break tokenFormatCheck; // session key format verify ok
            }

            req.response().setStatusCode(203)
                    .end("Invalid authorization format: Cannot verify authorization format.");

            return null;
        }

        var sessionObj = SESSION_CACHE.get(sessionKey);
        if (sessionObj == null) {
            req.response().setStatusCode(203).end("Session object not found");
            return null;
        }
        if (!sessionObj.verify(req)) {
            return null;
        }
        return sessionObj;
    }

    public static void main(String[] args) throws Throwable {
        {
            var monitor = System.getenv("MEMORY_MONITOR");
            if (monitor != null) {
                GlobalShared.SCHEDULED_EXECUTOR_SERVICE.scheduleAtFixedRate(() -> {
                    MemoryDumper.dump();
                    System.gc();
                }, 1, Long.parseLong(monitor), TimeUnit.SECONDS);
            }
        }

        if (!ServiceSelector.isLocal()) { // packed
            var dataFolder = new File("serverData");
            LOGGER.info("unpacking resources.");
            ResourceExtract.unpackResources(dataFolder);
            LOGGER.info("downloading mobile qq apk");
            GlobalShared.MOBILEQQ_DOWNLOAD_RESULT = ResourceExtract.downloadMobileQQ(dataFolder);
        }

        String envAuthKey = System.getenv("AUTH_KEY");
        if (envAuthKey == null) envAuthKey = HexFormat.of().formatHex(randomBytes(16));

        String envIdentityKey = System.getenv("SERVER_IDENTITY_KEY");
        if (envIdentityKey == null) envIdentityKey = HexFormat.of().formatHex(randomBytes(24));

        authorizationKeyBuf = ByteBuffer.allocateDirect(envAuthKey.length());
        authorizationKeyBuf.put(envAuthKey.getBytes(StandardCharsets.UTF_8));
        authorizationKeyBuf.position(0);

        var vertx = Vertx.vertx();
        Router router = Router.router(vertx);

        final var timeout = TimeUnit.SECONDS.toMillis(120);
        GlobalShared.SCHEDULED_EXECUTOR_SERVICE.schedule(() -> {
            var now = System.currentTimeMillis();
            SESSION_CACHE.values().removeIf(session -> {
                if (now - session.lastAccessTime < timeout) return false;
                if (!session.wsSessions.isEmpty()) return false;

                session.invalidate();
                return true;
            });
        }, 60, TimeUnit.SECONDS);


        KeyPair keypair;
        {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(4096);
            keypair = generator.generateKeyPair();
            LOGGER.info("rsa key pair generated, pubKey = {}", Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded()));
        }

        String serverKeySignature;
        String serverPublicKeyBase64 = Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded());
        {
            var hexFormat = HexFormat.of().withLowerCase();
            // $result = $sha1(
            //      $sha1( ($preKnownKey + $base64ServerPublicKey).getBytes() ).hex() + $preKnownKey
            // ).hex()

            var pkeyRsaKeySha1 = sha1((envIdentityKey + serverPublicKeyBase64).getBytes());

            var result = sha1(
                    (hexFormat.formatHex(pkeyRsaKeySha1) + envIdentityKey).getBytes()
            );
            serverKeySignature = hexFormat.formatHex(result);
        }

        router.get("/").handler(req -> {
            LOGGER.info("[ROUTER] receiving get about page");
            req.json(
                    new JsonObject()
                            .put("main_page", "https://github.com/kiliokuara/magic-signer-guide/issues")
                            .put("server_version", ServiceSelector.commitHash())
                            .put("server_build_time", ServiceSelector.buildTime())
                            .put("supported_protocol_versions", JsonArray.of(QVersionConst.MAIN_VERSION))
            );
        });

        router.get("/service/time").handler(req -> {
            LOGGER.info("[ROUTER] receiving get service current time");
            req.json(System.currentTimeMillis());
        });

        router.get("/service/rpc/handshake/config").handler(req -> {
            LOGGER.info("[ROUTER] receiving get handshake config request");
            req.json(
                    new JsonObject()
                            .put("publicKey", serverPublicKeyBase64)
                            .put("timeout", timeout)
                            .put("keySignature", serverKeySignature)
            );
        });

        router.post("/service/rpc/handshake/handshake").handler(req -> {
            LOGGER.info("[ROUTER] receiving do handshake request");
            var requestContent = req.request().handler(new Handler<>() {
                        final AtomicInteger count = new AtomicInteger(0);

                        @Override
                        public void handle(Buffer buf) {
                            if (count.addAndGet(buf.length()) > 2048) {
                                req.response().setStatusCode(403);
                                req.end("Request body to large");
                            }
                        }
                    }).body()
//                    .map(buf -> rsaDecrypt(keypair, buf.getBytes()))
                    .map(JsonObject::new)
                    .map(sobj -> {
                        var sec = sobj.remove("secret");
                        if (sec != null) {
                            var dbuf = rsaDecrypt(keypair, Base64.getDecoder().decode(sec.toString()));
                            var srcData = new JsonObject(Buffer.buffer(dbuf));
                            for (var entry : srcData) {
                                sobj.put(entry.getKey(), entry.getValue());
                            }
                        }
                        return sobj;
                    })
                    .compose(new Function<JsonObject, Future<JsonObject>>() {
                        String rejected;

                        private void reject(String msg) {
                            req.response().setStatusCode(400);
                            req.json(new JsonObject().put("status", 400).put("reason", msg));
                            rejected = msg;
                        }

                        @Override
                        public Future<JsonObject> apply(JsonObject entries) {
                            process(entries);
                            if (rejected == null) {
                                LOGGER.info("[ROUTER] accepted do handshake request.");
                                return Future.succeededFuture(entries);
                            } else {
                                LOGGER.info("[ROUTER] reject do handshake request: {}", rejected);
                                return Future.failedFuture(rejected);
                            }
                        }

                        void process(JsonObject data) {
                            if (!data.containsKey("authorizationKey")) {
                                reject("authorizationKey not found.");
                                return;
                            }
                            if (!data.containsKey("botid")) {
                                reject("botid not found.");
                                return;
                            }

                            var botid = data.getNumber("botid");

                            var bidn = botid.longValue();
                            if (bidn < 0) {
                                reject("Botid " + bidn + " is negative.");
                                return;
                            }

                            var sharedKey = data.getString("sharedKey");
                            if (sharedKey == null) {
                                reject("sharedKey not found");
                                return;
                            }
                            if (sharedKey.length() != 16) {
                                reject("sharedKey length isn't equals to 16");
                                return;
                            }

                            var duplicatedAuthKeyBuf = authorizationKeyBuf.duplicate();
                            var authKey = (byte[]) Array.newInstance(byte.class, duplicatedAuthKeyBuf.remaining());

                            duplicatedAuthKeyBuf.position(0);
                            duplicatedAuthKeyBuf.get(authKey);

                            if (!Arrays.equals(authKey, data.getString("authorizationKey").getBytes(StandardCharsets.UTF_8))) {
                                reject("Authorization key is invalid.");
                                return;
                            }

                            var clientRsa = data.getString("clientRsa");
                            if (clientRsa == null) {
                                reject("Missing `clientRsa`");
                                return;
                            }
                            try {
                                KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(clientRsa.getBytes())));
                            } catch (Exception e) {
                                reject(e.toString());
                            }
                        }
                    })
                    .compose(process(data -> {
                        var sharedKey = new SecretKeySpec(data.getString("sharedKey").getBytes(), "AES");
                        var clientRsa = data.getString("clientRsa");


                        long botid = data.getLong("botid");

                        String sessionKey;
                        var session = new RpcServerServiceSession();
                        session.lastAccessTime = System.currentTimeMillis();
                        session.sharedKey = sharedKey;
                        session.botid = botid;
                        try {
                            session.clientRsa = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(clientRsa.getBytes())));
                        } catch (InvalidKeySpecException | NoSuchAlgorithmException e) {
                            throw new RuntimeException(e);
                        }

                        while (true) {
                            var tmpKey = UUID.randomUUID().toString();
                            session.sessionKey = tmpKey;
                            if (!SESSION_CACHE.containsKey(tmpKey)) {
                                if (SESSION_CACHE.putIfAbsent(tmpKey, session) == null) {
                                    sessionKey = tmpKey;
                                    break;
                                }
                            }
                        }

                        var token = (sessionKey + '.' + sign(keypair, sessionKey.getBytes()));
                        LOGGER.info("session of bot {} is created: {}", botid, token);

                        req.response().setStatusCode(200);
                        req.json(
                                new JsonObject()
                                        .put("status", 200)
                                        .put("token", Base64.getEncoder().encodeToString(token.getBytes()))
                        );

                    }));


            requestContent.onFailure(error -> {
                if (req.response().headWritten()) return;
                error.printStackTrace();

                req.response().setStatusCode(500).end(error.toString());
            });
        });

        router.delete("/service/rpc/session").handler(req -> {
            var auth = req.request().getHeader("Authorization");
            if (auth == null) {
                req.response().setStatusCode(403).end("Not authorized");
                return;
            }
            LOGGER.info("[ROUTER] client request to invalidate session {}", auth);

            var session = findSessionAndVerifyAndReject(keypair, auth, req);
            if (session == null) {
                return;
            }

            if (!SESSION_CACHE.remove(session.sessionKey, session)) {
                req.response().setStatusCode(404).end("Session not found");
                return;
            }

            req.response().setStatusCode(204).end();

            GlobalShared.SCHEDULED_EXECUTOR_SERVICE.submit(session::invalidate);
        });

        router.get("/service/rpc/session/check").handler(req -> {
            var auth = req.request().getHeader("Authorization");
            if (auth == null) {
                req.response().setStatusCode(403).end("Not authorized");
                return;
            }
            LOGGER.info("[ROUTER] client request to check session state {}", auth);

            var session = findSessionAndVerifyAndReject(keypair, auth, req);
            if (session == null) {
                return;
            }

            req.response().setStatusCode(204).end();
        });

        router.get("/service/rpc/session").handler(req -> {
            if (!Utils.canUpgradeToWebsocket(req.request())) {
                req.response().setStatusCode(400);
                req.response().end("Can \"Upgrade\" only to \"WebSocket\".");
                return;
            }

            var auth = req.request().getHeader("Authorization");
            if (auth == null) {
                req.response().setStatusCode(403).end("Not authorized");
                return;
            }
            LOGGER.info("[ROUTER] client request open ws {}", auth);

            var sessionObj = findSessionAndVerifyAndReject(keypair, auth, req);
            if (sessionObj == null) {
                return;
            }


            req.request().toWebSocket().onSuccess(ws -> {
                sessionObj.wsSessions.add(ws);
                ws.closeHandler($ -> sessionObj.wsSessions.remove(ws));

                ws.binaryMessageHandler(bf -> {
                    var aesDec = aesDecrypt(sessionObj.sharedKey, bf.getBytes());

                    sessionObj.handleFrame(new String(aesDec, StandardCharsets.UTF_8), ws, vertx);
                });
            });
        });

        String serverHost = System.getenv("HOST");
        if (serverHost == null) serverHost = "0.0.0.0";

        Integer serverPort;
        try {
            serverPort = Integer.parseInt(System.getenv("PORT"));
        } catch (NumberFormatException e) {
            serverPort = 8888;
        }

        router.route().handler(context ->
                context.response()
                        .putHeader("context-type", "text/plain")
                        .end("void")
        );

        var msg = new StringBuilder("HTTP server is listening on ");
        msg.append(serverHost);
        msg.append(':');
        msg.append(serverPort);
        msg.append(" with auth key: ");
        msg.append(envAuthKey);
        msg.append("\n Server identity key: ").append(envIdentityKey);
        msg.append("\n QVersion: ").append(QVersionConst.MAIN_VERSION);
        msg.append("\n Server version: ").append(ServiceSelector.commitHash());

        {
            var time = Instant.ofEpochMilli(ServiceSelector.buildTime())
                    .atZone(ZoneId.of("Asia/Shanghai"));

            msg.append("\n Build time: ");
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").formatTo(time, msg);
        }

        vertx.createHttpServer()
                .requestHandler(router)
                .listen(serverPort, serverHost, http -> {
                    if (http.succeeded()) {
                        LOGGER.info(msg.toString());
                        // 至少打印一次
                        MemoryDumper.dump();
                    } else {
                        http.cause().printStackTrace();
                        System.exit(-1);
                    }
                });

    }

    private static byte[] randomBytes(int length) {
        var result = (byte[]) Array.newInstance(byte.class, length);
        var random = new Random();

        random.nextBytes(result);
        return result;
    }

}
