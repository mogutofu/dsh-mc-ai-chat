# mcai ↔ DSH 协议（mod 1.4.0 / 插件 dsh-mc-server-ops ≥ 0.6.3）

mod / 插件把游戏内 `/ai` 请求 POST 到 DSH 插件监听的本机地址（默认 `http://127.0.0.1:8765/mc-ai`）。
1.3.0 起所有平台（Forge / NeoForge / Fabric）的 HTTP 请求体完全一致；1.4.0 起 Bukkit 插件也能带 `attachments`
（走 `mcai:bridge` + HTTP 上传端口，见下文「Bukkit 附件桥接」）。

## 请求

`POST /mc-ai`，`Content-Type: application/json`：

```json
{
  "player": "sxh258",
  "uuid": "54c9346b-e5c5-34d6-bdb1-901927a41dbb",
  "text": "你好",
  "attachments": [
    { "kind": "image", "name": "screenshot.png", "mediaType": "image/png", "data": "<canonical base64>" }
  ]
}
```

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `player` | 是 | 玩家名，用于日志和 AI 称呼 |
| `uuid` | 是 | 玩家 UUID；DSH 插件用 `mc-ai:<uuid>` 做独立会话，上下文连续 |
| `text` | 是（可为空串） | 玩家消息 |
| `attachments` | 否 | 附件数组，每次最多 1 个 |

附件对象：

| 字段 | 说明 |
| --- | --- |
| `kind` | `image` 或 `file` |
| `name` | 文件名（≤ 255 字符） |
| `mediaType` | 图片：`image/png` / `image/jpeg` / `image/webp` / `image/gif`；文件：`application/octet-stream` 或原类型 |
| `data` | **规范 base64**（`Base64.getEncoder()` 无换行编码；用 `/`、`+`、`=` 的普通字母表） |

限制（服务端和 DSH 插件都会校验）：

- 图片解码后 ≤ 8MB；文件解码后 ≤ 16MB；单请求附件总量 ≤ 24MB。
- 截图以 PNG 发送（`kind=image`、`mediaType=image/png`、`name=screenshot.png`）。
- 图片扩展名的本地文件（png/jpeg/webp/gif）也按 `kind=image` 发送，让模型直接看图；其它按 `kind=file`。

## 响应

```json
{ "ok": true, "reply": "你好呀。", "elapsedMs": 1234 }
```

失败时 HTTP 仍是 200，但 `ok=false` 并带 `error`：

```json
{ "ok": false, "error": "附件图片类型不支持：image/bmp" }
```

mod 按 `ok` 判断，把 `reply` 或错误提示贴到玩家聊天栏。

## 天气投票配置（插件 → 平台，单向文件）

插件 `dsh-mc-server-ops`（≥ 0.6.3）在设置面板保存「游戏内天气投票」后，把配置写到 **服务端目录** `config/mcai-weather.json`；所有平台的 `/weather` 每次读取该文件（不存在/坏掉回落到默认值）。

```json
{
  "enabled": true,
  "directThreshold": 2,
  "approveNeed": 2,
  "rejectNeed": 1,
  "timeoutSeconds": 60,
  "cooldownSeconds": 60,
  "updatedAt": "2026-11-15T04:55:53.492Z",
  "managedBy": "dsh-mc-server-ops"
}
```

| 字段 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 关掉后 `/weather` 只提示「天气功能已被服主关闭」 |
| `directThreshold` | `2` | 在线 ≤ 这个数时直接改天气；更多人才发起投票 |
| `approveNeed` | `2` | 通过需要几票同意（发起人自动算 1 票） |
| `rejectNeed` | `1` | 几票反对就作废 |
| `timeoutSeconds` | `60` | 投票有效期（秒） |
| `cooldownSeconds` | `60` | 同一玩家两次发起的最小间隔（秒） |

规则：`/weather clear|rain|thunder [持续秒数]`（所有玩家可用，替换原版需要 OP 的 `/weather`）；在线 ≤ `directThreshold` 直接生效，否则发起 `/vote yes|no|status` 投票。控制台 / OP 直接执行。

## 服务端 ↔ 客户端附件协议（Forge / NeoForge / Fabric）

各加载器用各自的 payload 注册方式，但消息语义与字段一致：

| 消息 | 方向 | 字段 |
| --- | --- | --- |
| `ClientHello` | C→S | `int protocolVersion`（=1） |
| `AttachmentRequest` | S→C | `UUID transferId`、`byte kind`(0=截图,1=文件)、`String path`、`int maxBytes` |
| `AttachmentChunk` | C→S | `UUID transferId`、`int chunkIndex`、`int chunkCount`、`String fileName`、`String mediaType`、`boolean image`、`int totalBytes`、`byte[] data` |
| `AttachmentAbort` | C→S | `UUID transferId`、`String reason` |
| `AttachmentTooLarge` | S→C | `UUID transferId`、`String reason`（预留，可空实现） |

- 分块：`CHUNK_SIZE = 24 * 1024` 原始字节（24576）；1.20.1 Forge 单包上限 32767 字节，现代 payload 上限更大，统一 24KB 兼容所有平台。
- 服务端按 `transferId` 收集，校验 `chunkCount`/`totalBytes`，只接受发起玩家；15 秒超时降级。
- 没有收到 `ClientHello` 视为未安装客户端模组：`/ai screenshot`、`/ai file` 降级为只发文字并提示「客户端未安装 mcai-client」。
- 通道 / payload 标识：Forge 1.20.1 与 Forge 1.12.2 用单通道 `mcai:net`（SimpleChannel / SimpleImpl，消息按索引区分）；NeoForge 用 5 个 payload：`mcai:client_hello`、`mcai:attachment_request`、`mcai:attachment_chunk`、`mcai:attachment_abort`、`mcai:attachment_too_large`；Fabric 用 5 个 `Identifier`：`mcai:hello`、`mcai:request`、`mcai:chunk`、`mcai:abort`、`mcai:too_large`。协议版本统一 `"1"`。

## Bukkit 附件桥接（mcai-bridge/1）

服务器只装了 Bukkit/Spigot/Paper 插件、没有 `mcai` 服务端模组时，客户端模组用这条**额外的** raw plugin message
通道握手，再把字节用 HTTP 发到插件自带的上传端口。它和上面的 `mcai:net` / payload 通道互不影响：
模组服照旧走模组通道，桥接只在收到服务端 INFO 时生效。

### 通道

- 通道名固定 `mcai:bridge`，raw bytes（不套任何加载器的 payload 编解码）。
- 登录后客户端先补发一条 `minecraft:register`（载荷 = `mcai:bridge` + `\0`）：Paper 的
  `CraftPlayer#sendPluginMessage` 只发给「客户端声明过」的通道（1.20.1 与 1.21+ 的
  `ServerCommonPacketListenerImpl` 都还认这个兼容包），不发的话服务端下发的 INFO/REQUEST 会被静默丢弃。
- 帧格式：`"MCAI"`（4 字节 ASCII）+ `u8 版本`（=1）+ `u8 类型` + payload。
  所有整数**大端**；字符串 = `int 长度` + UTF-8 字节；UUID = 两个 `long`。

| type | 名称 | 方向 | payload |
| --- | --- | --- | --- |
| 1 | HELLO | C→S | `UTF loader`（forge/neoforge/fabric）、`UTF clientVersion` |
| 2 | INFO | S→C | `int port`、`UTF advertiseHost`、`UTF token`、`int ttlSeconds`、`int maxImageBytes`、`int maxFileBytes` |
| 3 | REQUEST | S→C | `UUID transferId`、`u8 kind`（0=截图，1=文件）、`UTF path`（截图为空串）、`int maxBytes` |
| 4 | ABORT | C→S | `UUID transferId`、`UTF reason` |
| 5 | DONE | C→S | `UUID transferId`、`u8 ok`、`UTF error` |

- HELLO 之后插件回一条 INFO（端口/上限）；每次 `/ai screenshot|file` 前插件**再发一条 INFO**（带绑定本次
  transferId 的一次性 token）+ REQUEST，客户端用最新的 token 上传。令牌一次性、TTL 到期作废。
- 客户端 2 秒内没收到 INFO 视为「服务端没有桥接」，保持原有降级行为；`/ai screenshot` 仍只发文字。

### HTTP 上传

```http
POST http://<advertiseHost>:<port>/mcai/upload
X-Mcai-Token: <一次性 token>
X-Mcai-Transfer: <UUID>
X-Mcai-Kind: image|file
X-Mcai-Name-B64: <文件名 UTF-8 的 base64>
X-Mcai-Media: image/png 等
Content-Length: <字节数>

<body: 原始字节>
```

- `advertiseHost` 为空时客户端用自己连接服务器的地址；`advertise-port=0` 表示用 `port`。
- 成功 `200 {"ok":true}`；失败 4xx/5xx `{"ok":false,"error":"..."}`：
  `400` 缺 token/transfer/类型不符、`403` token 无效或过期、`409` 没有匹配的 pending、
  `411` 缺 Content-Length、`413` 超过上限、`429` 并发超限。
- 插件只接受 `POST /mcai/upload` + `Content-Length`（不支持 chunked/multipart）；只取文件名的 basename，
  **绝不按玩家给的路径落盘**；读完字节切回服务器主线程，再走与模组版完全相同的 `AiClient` → DSH `/mc-ai` 契约。
- 截图 ≤ `max-image-mb`（默认 8）、文件 ≤ `max-file-mb`（默认 16）、同时上传 ≤ `max-concurrent`（默认 2）、
  单次读取超时 `upload-timeout-seconds`（默认 30）；等附件 15 秒没到就降级为只发文字。

### 回退

| 情况 | 行为 |
| --- | --- |
| 客户端没装 / 旧客户端（1.3.0） | 不会发 HELLO；`/ai screenshot`、`/ai file` 降级为只发文字并提示「客户端未安装 mcai-client 1.4.0」 |
| 端口绑不上 / `attachment.enabled=false` | 只禁用桥接；文字 `/ai`、`/weather`、`/vote` 正常，日志明确报错 |
| 上传失败 / token 过期 / 超限 / ABORT | 该次附件作废，提示原因后按只发文字处理 |
| 15 秒没收到字节 | 提示「附件传输超时（15 秒），已改为只发送消息。」并发文字请求 |
| 模组服（有 `mcai` 服务端模组） | 走原有模组通道，桥接 INFO 不出现，行为与 1.3.0 完全一致 |

## 兼容性

| 平台 / 版本 | 插件 | 文字 | 截图/文件附件 | 天气投票 |
| --- | --- | --- | --- | --- |
| Forge 1.20.1 1.4.0 | ≥ 0.6.3 | ✅ | ✅（Forge 通道） | ✅ |
| Forge 1.12.2 1.4.0 | ≥ 0.6.3 | ✅ | ✅（SimpleImpl） | ✅ |
| NeoForge 1.21.1 / 26.x 1.4.0 | ≥ 0.6.3 | ✅ | ✅（NeoForge payload） | ✅ |
| Fabric 26.x 1.4.0 | ≥ 0.6.3 | ✅ | ✅（Fabric networking） | ✅ |
| Bukkit 1.4.0 + 客户端 1.4.0 | ≥ 0.6.3 | ✅ | ✅（mcai:bridge + HTTP 上传） | ✅ |
| Bukkit 1.4.0 + 客户端 1.3.0 / 无客户端 | ≥ 0.6.3 | ✅ | ❌（降级为文字） | ✅ |
| 1.3.0 客户端 + 1.4.0 模组服 | ≥ 0.6.3 | ✅ | ✅（模组通道未变） | ✅ |
| 1.2.0 | ≥ 0.6.3 | ✅ | ✅ | ✅ |
| 1.2.0 | 0.6.0–0.6.2 | ✅ | ✅ | ⚠️ 默认值 |

插件本身不需要为 1.4.0 改代码：`/mc-ai` 契约未变，1.2.0 以来的请求体都兼容。
