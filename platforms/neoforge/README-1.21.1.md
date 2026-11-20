# mcai — NeoForge 1.21.1 版

`mcai` 1.3.0 的 NeoForge 1.21.1 实现，产出两个互相独立的 mod jar：

| 文件 | modId | 说明 |
| --- | --- | --- |
| `mc-ai-neoforge-1_21_1-v1_3_0.jar` | `mcai` | 服务端必装：`/ai`、`/weather`、`/vote`，以及附件协议的服务端一半 |
| `mc-ai-client-neoforge-1_21_1-v1_3_0.jar` | `mcai_client` | 客户端选装：让 `/ai screenshot`、`/ai file` 把画面/本地文件发给 AI |

## 支持的版本

- Minecraft **1.21.1**
- NeoForge **21.1.267**（`neoforge-21.1.267`）
- Java **21**
- 构建：ModDevGradle **2.0.148** + Gradle **9.2.1**（官方名 official mappings）

## 安装

1. 服务端：把 `mc-ai-neoforge-1_21_1-v1_3_0.jar` 放进 `mods/`，启动一次服务器。
   首次启动会生成 `config/mcai-common.json`（endpoint/timeoutSeconds/enabled/rateLimitSeconds）。
2. 客户端（可选）：把 `mc-ai-client-neoforge-1_21_1-v1_3_0.jar` 放进玩家自己的 `mods/`。
   不装也能进服，只是 `/ai screenshot`、`/ai file` 会降级成只发文字并提示
   `客户端未安装 mcai-client`。
3. 服务端还需要在本机跑 DSH 及 `dsh-mc-server-ops` 插件（监听默认
   `http://127.0.0.1:8765/mc-ai`），mod 只负责把游戏内的请求 POST 过去。

## 指令

| 指令 | 谁可用 | 说明 |
| --- | --- | --- |
| `/ai <内容>`、`/ai message <内容>` | 所有玩家 | 5 秒冷却（`rateLimitSeconds`），回复以 `[小铜] ` 开头 |
| `/ai screenshot [消息]` | 所有玩家 | 需要客户端装 `mcai_client`；截图 PNG ≤ 8MB |
| `/ai file <本地路径> <消息>` | 所有玩家 | 需要客户端装 `mcai_client`；文件 ≤ 16MB；路径有空格用引号 |
| `/weather clear\|rain\|thunder [秒]` | 所有玩家 | 替换原版需要 OP 的同名指令 |
| `/vote yes\|no\|status` | 游戏内玩家 | 天气投票 |

`/weather` 规则（配置 `config/mcai-weather.json`，每次执行重新读，默认值）：

```json
{"enabled":true,"directThreshold":2,"approveNeed":2,"rejectNeed":1,"timeoutSeconds":60,"cooldownSeconds":60}
```

- 在线 ≤ `directThreshold`（2）直接生效；≥3 人发起投票，发起人自动 1 票同意；
- `approveNeed`（2）票通过、`rejectNeed`（1）票反对作废、`timeoutSeconds`（60 秒）超时作废；
- 同一玩家两次发起间隔 `cooldownSeconds`（60 秒）；
- 控制台 / OP 直接执行；
- 该文件由 DSH 插件的设置面板写入，手改也可以。

## 附件协议（与 1.2.0 的 PROTOCOL.md 等价）

自定义 payload（NeoForge `RegisterPayloadHandlersEvent`，通道版本 `1`，所有载荷 optional）：

| payload | 方向 | 字段 |
| --- | --- | --- |
| `mcai:client_hello` | C→S | protocolVersion(int) |
| `mcai:attachment_request` | S→C | transferId(UUID)、kind(byte)、path(String)、maxBytes(int) |
| `mcai:attachment_chunk` | C→S | transferId、chunkIndex、chunkCount、fileName、mediaType、image、totalBytes、data(byte[]) |
| `mcai:attachment_abort` | C→S | transferId、reason |
| `mcai:attachment_too_large` | S→C | transferId、reason（预留） |

- 24KB（24576 字节）一块；每次最多 1 个附件；服务端等附件 15 秒；
- 服务端只在**专用服务器**注册载荷，客户端模组只在**物理客户端**注册，同一 JVM 不会重复注册；
- 客户端登录时先确认服务端注册了同名载荷才发 `ClientHello`，连没装 mcai 的服务器不会白发包。

## 已知限制

- **附件只在专用服务器可用**：单机（integrated server）下服务端模组不注册附件载荷，
  `/ai screenshot`、`/ai file` 会按「客户端未安装 mcai-client」降级为只发文字（与 1.2.0 相同）。
- 客户端模组是纯客户端逻辑，但仍是一个 NeoForge mod；装在没有 `mcai` 的客户端上不会做事。
- 天气投票的聊天文案前缀是 `[天气] `（与公共核心/Bukkit 实现一致）；1.2.0 里冷却提示用的是
  `[小铜] `，这里统一走公共核心的 Host 接口，所以也是 `[天气] `。
- 颜色只是观感差异：公共核心不区分消息类型，`send` 用灰色、`broadcast` 用黄色。

## 构建

本机 `maven.neoforged.net` 被阻断，构建统一走 GitHub Actions：

- workflow：`.github/workflows/build-neoforge-1.21.1.yml`
- 触发：push（`platforms/neoforge/**`、`common/**`、该 workflow）或手动 `workflow_dispatch`
- 动作：Java 21 + Gradle 9.2.1 → 分别在 `platforms/neoforge/server`、`platforms/neoforge/client`
  生成 wrapper 并 `./gradlew build --no-daemon` → 两个 jar 收集到
  `platforms/neoforge/build/libs/` 并以 artifact `mcai-neoforge-1.21.1` 上传。

本地想构建的话（需要能访问 `maven.neoforged.net`）：

```bash
cd platforms/neoforge/server && gradle build
cd platforms/neoforge/client && gradle build
```

工程直接引用仓库里的公共核心：`server/build.gradle` 里
`sourceSets.main.java.srcDirs += file('../../../common/src/main/java')`，
没有改动 `common/`。
