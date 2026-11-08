# mcai ↔ DSH 协议（mod 1.1.0 / 插件 dsh-mc-server-ops ≥ 0.6.0）

mod 把游戏内 `/ai` 请求 POST 到 DSH 插件监听的本机地址（默认 `http://127.0.0.1:8765/mc-ai`）。

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
| `attachments` | 否 | 附件数组，1.1.0 每次最多 1 个 |

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

## 兼容性

| mod | 插件 | 文字 | 截图/文件附件 |
| --- | --- | --- | --- |
| 1.0.x | 0.5.x | ✅ | ❌（无附件协议） |
| 1.1.0 | 0.5.x | ✅ | ❌ 插件会忽略 `attachments`（不推荐） |
| 1.1.0 | ≥ 0.6.0 | ✅ | ✅ |

## 客户端 ↔ 服务端（Forge 网络，仅附件）

- 通道：`mcai:net`，协议版本字符串 `"1"`；只在专用服务器（服务端）与客户端（客户端模组）各自注册，避免重复注册。
- 消息：`ClientHello`（C→S）、`AttachmentRequest`（S→C）、`AttachmentChunk`（C→S，24KB/块）、`AttachmentAbort`（C→S）、`AttachmentTooLarge`（S→C）。
- 服务端为每个玩家维护一个待传输请求，15 秒超时；无 `ClientHello` 视为未装客户端模组，降级为只发文字。
