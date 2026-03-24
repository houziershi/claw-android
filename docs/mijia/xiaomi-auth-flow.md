# Xiaomi MIoT Authentication Flow

> 逆向自 `mijiaAPI` v3.0.5 源码 (`apis.py`)

## 概述

小米 MIoT API 使用二维码扫码登录，流程基于小米账号 OAuth。核心认证数据存储在 `auth.json` 中。

## 认证数据结构 (auth.json)

```json
{
  "ua": "Android-15-11.0.701-Xiaomi-...",
  "deviceId": "随机16字符",
  "pass_o": "随机16位hex",
  "psecurity": "...",
  "nonce": "...",
  "ssecurity": "base64编码的密钥，用于请求签名",
  "passToken": "...",
  "userId": "数字用户ID",
  "cUserId": "...",
  "serviceToken": "核心认证token",
  "expireTime": 1234567890000,
  "saveTime": 1234567890000
}
```

**关键字段：**
- `ssecurity` — 用于 RC4 加密和请求签名的密钥
- `serviceToken` — API 请求的认证 cookie
- `cUserId` — 用于 cookie 的用户标识
- `userId` — 数字用户 ID
- `ua` — 伪装的 User-Agent（模拟米家 Android App）

## 登录流程（QR Code）

### Step 1: 获取 serviceLogin 参数

```
GET https://account.xiaomi.com/pass/serviceLogin?_json=true&sid=mijia&_locale=zh_CN

Headers:
  User-Agent: <伪装UA>
  Cookie: deviceId=<deviceId>; pass_o=<pass_o>; uLocale=zh_CN

Response: JSON (前缀 "&&&START&&&" 需要去掉)
{
  "code": 70016,  // 非0表示需要登录
  "location": "https://account.xiaomi.com/...?...",
  ...
}
```

解析 `location` URL 的 query 参数。

**如果 `code == 0` 且已有 passToken**：表示 token 仍有效，直接请求 location URL 刷新 serviceToken。

### Step 2: 获取二维码登录 URL

```
GET https://account.xiaomi.com/longPolling/loginUrl?<Step1的参数>&theme=&bizDeviceType=&_hasLogo=false&_qrsize=240&_dc=<timestamp_ms>

Headers:
  User-Agent: <伪装UA>

Response:
{
  "code": 0,
  "loginUrl": "https://...",  // 二维码内容
  "qr": "https://...",        // 二维码图片URL
  "lp": "https://..."         // 长轮询URL
}
```

### Step 3: 长轮询等待扫码

```
GET <lp_url>
Timeout: 120秒

Response (扫码成功后):
{
  "code": 0,
  "psecurity": "...",
  "nonce": "...",
  "ssecurity": "...",
  "passToken": "...",
  "userId": "...",
  "cUserId": "...",
  "location": "https://..."  // 回调URL
}
```

### Step 4: 获取 serviceToken

```
GET <callback_url>  (来自 Step 3 的 location)

Response: Set-Cookie 中包含 serviceToken
```

### Step 5: 保存认证数据

合并所有数据到 auth.json，设置 `expireTime = now + 30天`。

## Token 刷新流程

当 token 可能过期时（通过 `check_new_msg` API 检测）：

1. 使用已有的 `passToken`/`userId`/`cUserId` 重新请求 `serviceLogin`
2. 如果 `code == 0`，请求返回的 `location` URL
3. 从 response cookies 中获取新的 `serviceToken` 和 `ssecurity`
4. 更新 auth.json

**检测 token 有效性：**
```
POST https://api.mijia.tech/app/v2/message/v2/check_new_msg
Data: {"begin_at": <timestamp_1h_ago>}
```
成功返回 = token 有效，失败 = 需要刷新或重新登录。

## User-Agent 格式

```
Android-15-11.0.701-Xiaomi-23046RP50C-OS2.0.212.0.VMYCNXM-<40位HEX>-<国家码>-<32位HEX>-<32位HEX>-SmartHome-MI_APP_STORE-<40位HEX>|<40位HEX>|<16位hex>-64
```

## Session Headers

API 请求使用的 session headers：

```
User-Agent: <伪装UA>
accept-encoding: identity
Content-Type: application/x-www-form-urlencoded
miot-accept-encoding: GZIP
miot-encrypt-algorithm: ENCRYPT-RC4
x-xiaomi-protocal-flag-cli: PROTOCAL-HTTP2
Cookie: cUserId=<cUserId>;
        yetAnotherServiceToken=<serviceToken>;
        serviceToken=<serviceToken>;
        timezone_id=<时区名>;
        timezone=GMT+08:00;
        is_daylight=0;
        dst_offset=0;
        channel=MI_APP_STORE;
        countryCode=CN;
        PassportDeviceId=<deviceId>;
        locale=zh_CN
```

## Android 适配方案

Android 端无法弹出终端二维码，替代方案：

1. **WebView OAuth**：直接在 WebView 中打开 `https://account.xiaomi.com/pass/serviceLogin?sid=mijia` 完成登录
2. **拦截回调**：通过 `WebViewClient.shouldOverrideUrlLoading()` 拦截 callback URL 获取 cookies
3. **提取 token**：从 WebView CookieManager 中提取 `serviceToken` 等
4. **补全数据**：`ssecurity` 等字段可能需要通过额外 API 获取
