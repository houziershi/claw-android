# MIoT Cloud API Specification

> 逆向自 `mijiaAPI` v3.0.5 源码 (`apis.py`, `miutils.py`, `devices.py`)

## API Base URL

```
https://api.mijia.tech/app
```

所有 API 请求都经过 RC4 加密签名。

## 请求签名机制

### 1. 生成 nonce

```python
millis = int(time.time() * 1000)
b = random_8_bytes + (millis // 60000).to_bytes(...)
nonce = base64_encode(b)
```

### 2. 计算 signed_nonce

```python
signed_nonce = base64(sha256(base64_decode(ssecurity) + base64_decode(nonce)))
```

### 3. 生成加密参数

```python
# 原始参数
params = {"data": json.dumps(data)}

# 1. 计算 rc4_hash__ 签名
signature_string = "POST&{uri}&data={data_value}&{signed_nonce}"
params["rc4_hash__"] = base64(sha1(signature_string))

# 2. RC4 加密每个参数值
for key in params:
    params[key] = rc4_encrypt(signed_nonce, value)

# 3. 添加最终签名
params["signature"] = base64(sha1("POST&{uri}&rc4_hash__={encrypted}&data={encrypted}&{signed_nonce}"))
params["ssecurity"] = ssecurity
params["_nonce"] = nonce
```

### 4. RC4 加密/解密

```python
def encrypt_rc4(password, payload):
    key = base64_decode(password)  # password = signed_nonce
    cipher = ARC4.new(key)
    cipher.encrypt(bytes(1024))     # 丢弃前1024字节（RC4 key schedule）
    return base64_encode(cipher.encrypt(payload.encode()))

def decrypt_rc4(password, payload):
    key = base64_decode(password)
    cipher = ARC4.new(key)
    cipher.encrypt(bytes(1024))
    return cipher.encrypt(base64_decode(payload))
    # 如果解码失败，尝试 gzip 解压
```

---

## API 列表

### 1. 获取家庭列表

```
POST /v2/homeroom/gethome_merged

Data:
{
  "fg": true,
  "fetch_share": true,
  "fetch_share_dev": true,
  "fetch_cariot": true,
  "limit": 300,
  "app_ver": 7,
  "plat_form": 0
}

Response.result.homelist:
[
  {
    "id": "家庭ID",
    "name": "家庭名称",
    "uid": 用户ID(int),
    "address": "地址",
    "roomlist": [...],
    "create_time": timestamp
  }
]
```

### 2. 获取设备列表（按家庭）

```
POST /home/home_device_list

Data:
{
  "home_owner": <uid(int), 从 homelist 获取>,
  "home_id": <home_id(int)>,
  "limit": 200,
  "start_did": "",
  "get_split_device": true,
  "support_smart_home": true,
  "get_cariot_device": true,
  "get_third_device": true
}

Response.result:
{
  "device_info": [
    {
      "did": "设备ID",
      "name": "设备名称",
      "model": "设备型号",
      "isOnline": true/false,
      "roomName": "房间名",
      "uid": 用户ID
    }
  ],
  "has_more": false,
  "max_did": ""
}
```

**分页**：如果 `has_more == true`，用 `max_did` 作为下一页的 `start_did`。

### 3. 获取共享设备列表

```
POST /v2/home/device_list_page

Data:
{
  "ssid": "<unknown ssid>",
  "bssid": "02:00:00:00:00:00",
  "getVirtualModel": true,
  "getHuamiDevices": 1,
  "get_split_device": true,
  "support_smart_home": true,
  "get_cariot_device": true,
  "get_third_device": true,
  "get_phone_device": true,
  "get_miwear_device": true
}

Response.result.list:
[
  {
    "did": "...",
    "name": "...",
    "model": "...",
    "isOnline": true/false,
    "owner": true/false
  }
]
```

过滤 `owner == true` 的设备为共享设备。

### 4. 读取设备属性 (prop/get)

```
POST /miotspec/prop/get

Data:
{
  "params": [
    {"did": "设备ID", "siid": 2, "piid": 1}
  ],
  "datasource": 1
}

Response.result:
[
  {
    "did": "设备ID",
    "siid": 2,
    "piid": 1,
    "value": true,
    "code": 0,
    "updateTime": 1234567890
  }
]
```

支持批量查询（params 数组多个元素）。

### 5. 设置设备属性 (prop/set)

```
POST /miotspec/prop/set

Data:
{
  "params": [
    {"did": "设备ID", "siid": 2, "piid": 1, "value": true}
  ]
}

Response.result:
[
  {
    "did": "设备ID",
    "siid": 2,
    "piid": 1,
    "code": 0  // 0=成功, 1=网关已接收但不确定是否成功
  }
]
```

### 6. 执行设备动作 (action)

```
POST /miotspec/action

Data:
{
  "params": {
    "did": "设备ID",
    "siid": 2,
    "aiid": 1,
    "value": [参数列表]  // 可选
  }
}

Response.result:
{
  "did": "...",
  "siid": 2,
  "aiid": 1,
  "code": 0
}
```

**注意**：action 不支持批量，每个 action 需要独立请求。

### 7. 获取场景列表

```
POST /appgateway/miot/appsceneservice/AppSceneService/GetSimpleSceneList

Data:
{
  "app_version": 12,
  "get_type": 2,
  "home_id": "家庭ID",
  "owner_uid": <uid(int)>
}

Response.result.manual_scene_info_list:
[
  {
    "scene_id": "场景ID",
    "name": "场景名称",
    "create_time": "timestamp"
  }
]
```

### 8. 执行场景

```
POST /appgateway/miot/appsceneservice/AppSceneService/NewRunScene

Data:
{
  "scene_id": "场景ID",
  "scene_type": 2,
  "phone_id": "null",
  "home_id": "家庭ID",
  "owner_uid": <uid(int)>
}
```

### 9. 获取统计数据

```
POST /v2/user/statistics

Data:
{
  "did": "设备ID",
  "key": "7.1",           // siid.piid 格式
  "data_type": "stat_month_v3",  // stat_hour_v3/stat_day_v3/stat_week_v3/stat_month_v3
  "limit": 6,
  "time_start": <timestamp>,
  "time_end": <timestamp>
}
```

### 10. 检查消息（用于验证 token 有效性）

```
POST /v2/message/v2/check_new_msg

Data:
{
  "begin_at": <timestamp_1h_ago>
}
```

---

## 设备规格查询 (MIoT Spec)

**不经过 mijia API**，直接从 miot-spec.org 网页抓取：

```
GET https://home.miot-spec.com/spec/<model>

Headers:
  User-Agent: mijiaAPI/3.0.5

Response: HTML 页面，设备信息在 data-page 属性中：
  <div data-page="...JSON...">

JSON 结构:
{
  "props": {
    "product": {"name": "设备名", "model": "型号"},
    "spec": {
      "services": {
        "<siid>": {
          "name": "service名",
          "properties": {
            "<piid>": {
              "name": "属性名",
              "description": "描述",
              "desc_zh_cn": "中文描述",
              "format": "bool|int8|uint8|int16|uint16|int32|uint32|float|string",
              "access": ["read", "write", "notify"],
              "unit": "percentage|celsius|...|none",
              "value-range": [min, max, step],
              "value-list": [{"value": 0, "description": "Auto"}]
            }
          },
          "actions": {
            "<aiid>": {
              "name": "动作名",
              "description": "描述"
            }
          }
        }
      }
    }
  }
}
```

**属性名冲突处理**：如果不同 service 下有同名属性，加上 `{service_name}-{prop_name}` 前缀。

---

## 错误码

| 错误码 | 含义 |
|--------|------|
| 0 | 成功 |
| 1 | 网关已接收，不确定是否成功 |
| -704042011 | 设备离线 |
| -704040003 | Property不存在 |
| -704040005 | Action不存在 |
| -704220043 | Property值错误 |
| -704053036 | 设备操作超时 |
| -704010000 | 未授权（设备可能被删除）|
| -10007 | 设备离线或不存在 |

完整错误码映射见源码 `errors.py`。

---

## Kotlin 实现要点

1. **RC4 加密**：Android 有 `javax.crypto.Cipher` 支持 `ARCFOUR`（即 RC4）
2. **签名**：`java.security.MessageDigest` 支持 SHA-256 和 SHA-1
3. **Base64**：`android.util.Base64` 或 `java.util.Base64`
4. **GZIP 解压**：`java.util.zip.GZIPInputStream`
5. **HTTP**：OkHttp（项目已引入）
6. **JSON**：kotlinx.serialization（项目已引入）
