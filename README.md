# magic-signer-guide

此项目用于解决各种 QQ 机器人框架的 sso sign 和 tlv 加密问题。

该项目为 RPC 服务后端，并提供 HTTP API，这意味着你可以根据框架的需求实现不同 RPC 客户端。

由于该项目扮演的角色比较特殊，为了保证客户端与服务端的通信安全，需要先进行认证，才可执行业务操作。

本项目（docker 镜像 `kiliokuara/vivo50`，以下相同）在可控范围内<sup>(1)</sup>不会持久化 QQ 机器人的以下信息：
* 登录凭证（token，cookie 等）
* 需要加密的 tlv 数据
* 需要签名的 sso 包数据

为优化业务逻辑，本项目会持久化 QQ 机器人的以下信息：
* 设备信息
* 由 libfekit 产生的账号相关的 Key-Value 键值对。

强烈建议自行部署 RPC 服务端，避免使用他人部署的开放的 RPC 服务端。

> (1) 可控范围指 RPC 服务端的所有代码。由于项目使用到了外置库 libfekit，我们无法得知 libfekit 会持久化的信息。

## 支持的 QQ 版本

* `Android 8.9.58.11170`

## 使用方法

通过 docker 部署 RPC 服务端

```shell
$ docker pull kiliokuara/vivo50:latest
$ docker run -d --restart=always \
  -e SERVER_IDENTITY_KEY=vivo50 \
  -e AUTH_KEY=kfc \
  -e PORT=8888 \
  -p 8888:8888 \
  --log-opt mode=non-blocking --log-opt max-buffer-size=4m \
  -v /home/vivo50/serverData:/app/serverData \
  -v /home/vivo50/testbot:/app/testbot \
  --name vivo50 \
  kiliokuara/vivo50
```

环境变量说明：

* `SERVER_IDENTITY_KEY`：RPC 服务端身份密钥，用于客户端确认服务端身份。
* `AUTH_KEY`：RPC 客户端验证密钥，用于服务端确认客户端身份。
* `PORT`：服务端口，默认 `8888`。

## 认证流程

### 1. 获取 RPC 服务端信息，并验证服务端的身份

首先调用 API `GET /service/rpc/handshake/config`，获取回应如下：
```json5
{
  "publicKey": "", // RSA 公钥，用于 客户端验证服务端身份 和下一步的 加密握手信息。 
  "timeout": 10000, // 会话过期时间（单位：毫秒）
  "keySignature": "" // 服务端公钥签名，用于 客户端验证服务端身份
}
```

为了防止 MITM Attack（中间人攻击），客户端需要验证服务端的身份。通过如下计算：

```
$clientKeySignature = $sha1(
    $sha1( ($SERVER_IDENTITY_KEY + $publicKey).getBytes() ).hex() + $SERVER_IDENTITY_KEY
).hex()
```

将 `clientKeySignature` 与 API 返回的 `keySignature` 比对即可验证服务端身份。

以 Kotlin 为例：

```kotlin

fun ByteArray.sha1(): ByteArray {
    return MessageDigest.getInstance("SHA1").digest(this)
}
fun ByteArray.hex(): String {
    return HexFormat.of().withLowerCase().formatHex(this)
}

val serverIdentityKey: String = ""; // 服务端 SERVER_IDENTITY_KEY
val publicKey: String = ""; // API 返回的 publicKey 字符串，该字符串是 base64 编码的 RSA 公钥。
val serverKeySignature: String = ""; // API 返回的 keySignature 字符串，该字符串是服务端计算签名。

val pKeyRsaSha1 = (serverIdentityKey + publicKey).toByteArray().sha1()
val clientKeySignature = (pKeyRsaSha1.hex() + serverIdentityKey).toByteArray().sha1().hex()

if (!clientKeySignature.equals(serverKeySignature)) {
    throw IllegalStateException("client calculated key signature doesn't match the server provides.")
}

```

### 2. 与服务端握手

在与服务端握手之前，需要客户端生成一个 16-byte AES 密钥和 4096-bit RSA 密钥对。

* AES 密钥用于加解密握手成功之后的 WebSocket 业务通信。
* RSA 密钥对用于防止使用 Replay Attacks（重放攻击）再次建立相同的 WebSocket 连接。


生成密钥后，调用 API `POST /service/rpc/handshake/handshake`，请求体如下：

```json5
{
  "clientRsa": "", // 客户端生成的 RSA 密钥对中的公钥，使用 base64 编码。
  "secret": "....", // 握手信息，使用上一步 “获取 RPC 服务端信息” 中的 publicKey，采用 RSA/ECB/PKCS1Padding 套件加密。
}

// 握手信息如下
{
  "authorizationKey": "", // 服务端的 AUTH_KEY
  "sharedKey": "", // AES 密钥
  "botid": 1234567890, // Bot QQ 号
}
```

回应如下：

```json5
{
  "status": 200, // 200 = 握手成功，403 = 握手失败
  "reason": "Authorization code is invalid.", // 握手失败的原因，仅握手失败会有此属性。
  "token": "", // WebSocket 通信 token，使用 base64 编码，仅握手成功会有此属性。
}
```

将 `token` 进行 base64 解码，至此握手过程已结束，接下来进行 WebSocket 通信。

### 3. 开启 WebSocket 会话

访问 API `WEBSOCKET /service/rpc/session`，请求需要添加以下 headers：

```properties
Authorization: $token_decoded     <--- base64 解码后的 token
X-SEC-Time: $timestamp_millis     <--- 当前时间戳，毫秒
X-SEC-Signature: $timestamp_sign  <--- 时间戳签名，使用客户端 RSA 密钥，采用 SHA256withRSA 算法签名，使用 base64 编码。
```

WebSocket 会话开启后，即可进行业务通信。

### 4. 查询 WebSocket 会话状态

WebSocket 每次发送 C2S 包之前，建议验证当前 WebSocket 会话的状态。

访问 API `GET service/rpc/session/check`，请求添加同 [3. 开启 WebSocket 会话](#3-开启-websocket-会话) 的 headers。

响应状态码如下：
* 204：会话有效
* 403：验证失败，需要检查 headers。
* 404：会话不存在

### 4. 中断 WebSocket 会话

在任务已经完成后（例如机器人下线，机器人框架关闭等），需要主动中断 WebSocket 会话。

访问 API `DELETE /service/rpc/session`，请求添加同 [3. 开启 WebSocket 会话](#3-开启-websocket-会话) 的 headers。

响应状态码如下：
* 204：会话已中断
* 403：验证失败，需要检查 headers。
* 404：会话不存在

## WebSocket 通信格式

### 通用规范

* C2S（client to server） 和 S2C（server to client）的所有的包均使用客户端 AES 密钥加密为 byte array。

* S2C 包通用格式如下：

```json5
{
  "packetId": "", // 独一无二的包 ID
  "packetType": "", // 包类型，对应为业务操作。
  ..., // 具体包类型的其他属性
}
```
> `packetId` 有如下两种情况：
> 
> 1. C2S 包包含 `packetId`，此 C2S 包需要服务端回应，则该 S2C 包为此 C2S 包的回应包，`packetId` 为此 C2S 包的 `packetId`。
> 2. 若该 S2C 包的 `packetType` 为 `rpc.service.send`，表示此 S2C 包需要客户端回应，需要 C2S 包包含该 S2C 包的 `packetId`。

* 业务遇到错误的 S2C 包格式如下：

```json5
{
    "packetId": .......,
    "packetType": "service.error",
    "message": "",
}
```

服务端不会主动发送业务错误的包，该 `packetId` 一定与 S2C 包的 `packetId` 对应。

### 业务场景

#### 会话中断

S2C

```json5
{
    "packetType": "service.interrupt",
    "reason": "Interrupted by session invalidate",
}
```

客户端收到此包意味着当前 WebSocket session 已失效，需要重新握手获取新的 session。

#### 初始化签名和加密服务

C2S

```json5
{
  "packetId": "",
  "packetType": "rpc.initialize",
  "extArgs": {
    "KEY_QIMEI36": "", // qimei 36
    "BOT_PROTOCOL": {
      "protocolValue": {
        "ver": "8.9.58",
      }
    }
  },
  "device": { // 除特殊标记，参数均为 value.toByteArray().hexString()
    "display": "",
    "product": "",
    "device": "",
    "board": "",
    "brand": "",
    "model": "",
    "bootloader": "",
    "fingerprint": "",
    "bootId": "", // raw string
    "procVersion": "",
    "baseBand": "",
    "version": {
      "incremental": "",
      "release": "",
      "codename": "",
      "sdk": 0 // int
    },
    "simInfo": "",
    "osType": "",
    "macAddress": "",
    "wifiBSSID": "",
    "wifiSSID": "",
    "imsiMd5": "",
    "imei": "", // raw string
    "apn": "",
    "androidId": "",
    "guid": ""
  },
},
```

S2C response

```json5
{
  "packetId": "",
  "packetType": "rpc.initialize"
}
```

初始化服务后才能进行 tlv 加密。

初始化过程中服务端会发送 `rpc.service.send` 包，详见[服务端需要通过机器人框架发送包](#服务端需要通过机器人框架发送包)。

#### 获取 sso 签名白名单

C2S

```json5
{
  "packetId": "",
  "packetType": "rpc.get_cmd_white_list"
}
```

S2C response

```json5
{
  "packetId": "",
  "packetType": "rpc.get_cmd_white_list",
  "response": [
    "wtlogin.login",
    ...,
  ]
}
```

获取需要进行 sso 签名的包名单，帮助机器人框架判断机器人框架的网络包是否需要签名。



#### 服务端需要通过机器人框架发送包

S2C

```json5
{
  "packetId": "server-...",
  "packetType": "rpc.service.send",
  "remark": "msf.security", // sso 包标记，可忽略
  "command": "trpc.o3.ecdh_access.EcdhAccess.SsoEstablishShareKey", // sso 包指令
  "botUin": 1234567890, // bot id
  "data": "" // RPC 服务端需要发送的包内容 bytes.hexString()
}
```

C2S response

```json5
{
  "packetId": "server-...",
  "packetType": "rpc.service.send",
  "command": "trpc.o3.ecdh_access.EcdhAccess.SsoEstablishShareKey",
  "data": "" // QQ 服务器包响应的内容 bytes.hexString()
}
```


客户端收到 `rpc.service.send` 后，需要将 `data` 包进行 sso 包装，通过机器人框架的网络层发送到 QQ 服务器。

QQ 服务器返回后，只需简单解析包的 command 等信息，将剩余内容传入 C2S response 包的 `data`。

需要注意的是，服务端需要通过机器人框架发送包全部需要 sso 签名，所以请收到 `rpc.service.send` 包后调用 sso 签名对包装后的网络包进行签名。

#### sso 签名

C2S

```json5
{
  "packetId": "",
  "packetType": "rpc.sign",
  "seqId": 33782, // sso 包的 sequence id
  "command": "wtlogin.login", // sso 包指令
  "extArgs": {}, // 额外参数，为空
  "content": "" // sso 包内容 bytes.hexString()
}
```

S2C response

```json5
{
  "packetId": "",
  "packetType": "rpc.sign",
  "response": {
    "sign": "",
    "extra": "",
    "token": ""
  }
}
```

#### tlv 加密

C2S

```json5
{
  "packetId": "",
  "packetType": "rpc.tlv",
  "tlvType": 1348, // 0x544
  "extArgs": {
    "KEY_COMMAND_STR": "810_a"
  },
  "content": "" // t544 内容 bytes.hexString()
}
```

S2C response

```json5
{
  "packetId": "",
  "packetType": "rpc.tlv",
  "response": "" // 加密结果 bytes.hexString()
}
```
