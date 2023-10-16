package tencentlibfekit.vmservice.rpc;

import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import net.mamoe.mirai.utils.DeviceInfo;
import tencentlibfekit.FEKitEncryptService;
import tencentlibfekit.proto.DeviceInfoProto;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.locks.LockSupport;

import static tencentlibfekit.common.GlobalShared.GSON;
import static tencentlibfekit.common.GlobalShared.HTTP_CLIENT;

public class RpcClientTest {
    public static void main(String[] args) throws Throwable {
        var rsp = HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                        .GET().uri(URI.create("http://localhost:8888/service/rpc/handshake/config"))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        var pubKeyStr = new JsonObject(Buffer.buffer(rsp.body())).getString("publicKey");
        System.out.println(pubKeyStr);

        var publicKey = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pubKeyStr.getBytes())));

        var sharedKey = UUID.randomUUID().toString().substring(0, 16);
        var sharedAESKey = new SecretKeySpec(sharedKey.getBytes(), "AES");

        var toolkit = new Object() {
            byte[] encode(byte[] data) throws NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException {
                var cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");
                cipher.init(Cipher.ENCRYPT_MODE, publicKey);
                return cipher.doFinal(data);
            }
        };

        var handshakeRsp = HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:8888/service/rpc/handshake/handshake"))
                        .POST(HttpRequest.BodyPublishers.ofByteArray(
                                toolkit.encode(Json.encodeToBuffer(
                                        new JsonObject()
                                                .put("token", "1141561230148965")
                                                .put("sharedKey", sharedKey)
                                                .put("botid", 12345678904144L)
                                                .put("timeout", 114514L)
                                ).getBytes())
                        ))
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );

        System.out.println(handshakeRsp.statusCode());
        System.out.write(handshakeRsp.body());
        System.out.println();

        var tok = new JsonObject(Buffer.buffer(handshakeRsp.body())).getString("token");
        var realToken = new String(RpcServerBootstrap.aesDecrypt(sharedAESKey, Base64.getDecoder().decode(tok)));

        System.out.println(realToken);

        /*var delrsp = HTTP_CLIENT.send(
                HttpRequest.newBuilder()
                        .DELETE()
                        .uri(URI.create("http://localhost:8888/service/rpc/session"))
                        .header("Authorization", realToken)
                        .build(),
                HttpResponse.BodyHandlers.ofByteArray()
        );
        System.out.println(delrsp.statusCode());
        System.out.write(delrsp.body());
        System.out.println();*/

        var vertx = Vertx.vertx();
        vertx.createHttpClient();
        vertx.close();

        var ws = HTTP_CLIENT.newWebSocketBuilder()
                .header("Authorization", realToken)
                .buildAsync(URI.create("ws://localhost:8888/service/rpc/session"), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {

                        var rdata = new byte[data.remaining()];
                        data.get(rdata);

                        var npx = RpcServerBootstrap.aesDecrypt(sharedAESKey, rdata);
                        System.out.println(new String(npx, StandardCharsets.UTF_8));

                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        System.out.println("WS Closed: " + statusCode + ", " + reason);
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        error.printStackTrace();
                    }
                })
                .get();

        {
            var initPkg = new InitPacket();
            initPkg.packetType = "rpc.initialize";
            initPkg.device = FEKitEncryptService.convertDeviceInfo(DeviceInfo.random());
            initPkg.extArgs = Map.of(
                    "KEY_QIMEI36", "114514"
            );
            ws.sendBinary(ByteBuffer.wrap(RpcServerBootstrap.aesEncrypt(sharedAESKey,
                    GSON.toJson(initPkg).getBytes()
            )), true);
        }

        LockSupport.park();
    }

    static class InitPacket {
        String packetType;
        Map<String, Object> extArgs;
        DeviceInfoProto device;
    }
}
