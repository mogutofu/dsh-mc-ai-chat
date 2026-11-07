# Minecraft AI Chat（modid: `mcai`）

在 Minecraft 服里直接跟「小铜」聊天：任意玩家敲 `/ai 你好小铜`，回复就出现在聊天栏里。

这个 mod 自己**不含任何 AI 能力**，它只是游戏和 DSH 之间的一个搬运工：

```
玩家: /ai 你好小铜
   │
   ▼
mcai (本 mod，服务端)
   │  HTTP POST http://127.0.0.1:8765/mc-ai
   ▼
dsh-mc-server-ops（DSH 插件，服务端那半）
   │  按玩家 UUID 开独立会话，交给小铜
   ▼
{"ok": true, "reply": "你好呀，今天服务器挺稳的。", "elapsedMs": 3210}
   │
   ▼
聊天栏: [小铜] 你好呀，今天服务器挺稳的。
```

- 版本：1.0.0
- 作者：mogutofu
- 协议：MIT（见 [LICENSE](LICENSE)）
- 环境：Minecraft 1.20.1 / Forge 47.x（实测 47.2.23）/ Java 17

---

## 1. 依赖

| 依赖 | 说明 |
| --- | --- |
| Minecraft 1.20.1 + Forge 47.x | 只在服务端装就行，客户端不装也能进服 |
| Java 17 | 服务端 JVM |
| **dsh-mc-server-ops 插件** | **必须**。它在本机 `127.0.0.1:8765` 提供 `POST /mc-ai`，是真正干活的那一半 |

没有 `dsh-mc-server-ops` 时 mod 能正常加载、指令也能注册，但每次 `/ai` 都会回
`[小铜] 出错了：连不上 AI 服务…`。

## 2. 安装

1. 停服。
2. 把 `mcai-1.0.0.jar` 丢进服务器的 `mods/` 目录（和 Forge 其它 mod 放一起）。
3. 启动服务器。首次启动会生成 `config/mcai-common.toml`。
4. 确认 DSH 侧 `dsh-mc-server-ops` 已经在跑（监听 `127.0.0.1:8765`）。
5. 进游戏敲 `/ai 在吗`，看到 `[小铜] …` 就通了。

客户端**不需要**装这个 mod。`mods.toml` 里 `displayTest="IGNORE_SERVER_VERSION"`，
没装 mod 的客户端连服不会弹红字。

## 3. 用法

```
/ai <内容>
```

例：

```
/ai 今天服务器卡不卡
[小铜] 挺稳的，TPS 20，就是内存有点满。
```

各种情况的提示：

| 情况 | 玩家看到 |
| --- | --- |
| 正常 | `[小铜] <回复内容>`（青色，前缀金色） |
| 刚问过 | `慢一点，刚问过啦`（灰色） |
| 功能关了 | `[小铜] AI 未开启`（红色） |
| 出错 | `[小铜] 出错了：<原因>`（红色） |
| 等回复中 | `[小铜] 正在输入…`（深灰，可选提示） |
| 控制台执行 | `这个指令只能在游戏里用。` |

**权限：等级 0，任何玩家都能用，op 和普通玩家完全一样。**
DSH 侧是按「普通群友」判定权限的：玩家只能读文件、查状态，写文件 / 执行命令 / 开服
这类高危操作需要服主授权或群友投票。op 不会因为身份多出任何能力。

**冷却**：同一玩家默认 5 秒一次，超了就是「慢一点，刚问过啦」。

## 4. 配置文件

路径：`config/mcai-common.toml`（Forge COMMON 配置，改完要重启服务端生效）

```toml
endpoint = "http://127.0.0.1:8765/mc-ai"
timeoutSeconds = 60
enabled = true
rateLimitSeconds = 5
```

| 配置项 | 类型 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `endpoint` | string | `http://127.0.0.1:8765/mc-ai` | AI 服务地址。DSH 插件换端口就改这里 |
| `timeoutSeconds` | int（1–600） | `60` | 单次请求最多等多少秒，等不到就提示「等 AI 回复超时了」 |
| `enabled` | bool | `true` | `false` 时 `/ai` 直接回 `[小铜] AI 未开启`，不发请求 |
| `rateLimitSeconds` | int（0–3600） | `5` | 同一玩家的冷却秒数，`0` 表示不限 |

配置文件缺失或写坏了不会崩服：读不到值时自动用上面的默认值。

## 5. 协议

完整契约见 `D:\tmp\mcai-contract.md`，这里是要点。

### 请求（游戏 → DSH）

```http
POST http://127.0.0.1:8765/mc-ai
Content-Type: application/json

{"player": "Steve", "uuid": "069a79f4-44e9-4726-a5be-fca90e38aaf5", "text": "你好小铜"}
```

- 只在服务端本机回环（`127.0.0.1`），不对外网开放
- `player` 是游戏名，`uuid` 是玩家 UUID（字符串形式），`text` 是 `/ai` 后面的原文

### 成功

```json
{"ok": true, "reply": "你好呀，今天服务器挺稳的。", "elapsedMs": 3210}
```

→ 聊天栏 `[小铜] 你好呀，今天服务器挺稳的。`

### 失败

```json
{"ok": false, "error": "AI 忙不过来，稍后再试"}
```

→ 聊天栏 `[小铜] 出错了：AI 忙不过来，稍后再试`

### 判定规则（严格按契约）

1. **只看 `ok` 字段，不看 HTTP 状态码**：HTTP 200 也可能是 `{"ok": false, ...}`。
2. `ok == true` 时取 `reply`；为空按失败处理。
3. `ok == false`（或缺失）时取 `error`；为空就用 `AI 服务返回失败（HTTP <code>）`。
4. 网络层问题一律按失败处理，给玩家看的原因分别是：
   - 连不上：`连不上 AI 服务（<endpoint>），dsh-mc-server-ops 开了吗？`
   - 超时：`等 AI 回复超时了（<timeoutSeconds> 秒）`
   - 返回不是 JSON / 空内容 / 解析失败：各自的说明
5. 玩家在等待期间下线，回复直接丢弃，不会报错。

### DSH 侧权限模型

- 每个玩家一个独立会话，`sessionId = mc-ai:<玩家uuid>`
- 这不是本机会话，`dsh-mc-server-ops` 的权限守卫按「群友」处理
- 不传 MC 的 op 状态；op 和普通玩家能力相同

## 6. 构建

要求：JDK 17、能访问 `maven.minecraftforge.net` 和 `services.gradle.org`。

```powershell
$env:JAVA_HOME = 'D:\server\java'
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"
cd D:\mcai-mod
.\gradlew.bat build
```

产物：`D:\mcai-mod\build\libs\mcai-1.0.0.jar`，再复制一份到 `D:\mcai-mod\dist\mcai-1.0.0.jar`：

```powershell
New-Item -ItemType Directory -Force dist | Out-Null
Copy-Item build\libs\mcai-1.0.0.jar dist\mcai-1.0.0.jar -Force
```

第一次构建要下载 Gradle 8.1.1 和 Forge 的依赖（会反编译 Minecraft），大概 10–20 分钟；
之后有缓存就快很多。构建脚本 `build.ps1` 会把这两步一次做完。

## 7. 目录结构

```
D:\mcai-mod
├─ build.gradle                     ForgeGradle 6 构建脚本（MDK 原样）
├─ gradle.properties                mod_id / 版本 / 作者 / 许可证
├─ settings.gradle
├─ gradlew.bat, gradle/             Gradle Wrapper（8.1.1）
├─ src/main/java/com/mogutofu/mcai/
│  ├─ McaiMod.java                  @Mod 入口，注册 COMMON 配置
│  ├─ McaiConfig.java               mcai-common.toml 四个配置项
│  ├─ AiCommandHandler.java         /ai 指令注册与执行（权限等级 0）
│  ├─ RateLimiter.java              按玩家 5 秒冷却
│  └─ AiHttpClient.java             HttpClient + 异步回调 + 聊天栏输出
├─ src/main/resources/
│  ├─ META-INF/mods.toml            displayTest=IGNORE_SERVER_VERSION
│  └─ pack.mcmeta
├─ dist/mcai-1.0.0.jar              构建产物副本
├─ README.md
├─ LICENSE                          MIT
└─ .gitignore
```

## 8. 实现要点（为什么不会卡服）

- 指令处理只在主线程做三件事：查开关、查冷却、把 `name/uuid/text` 抄出来。
- HTTP 请求用 JDK 17 自带的 `java.net.http.HttpClient`，跑在
  `CompletableFuture.supplyAsync(..., Executors.newCachedThreadPool())` 的
  `mcai-http` 守护线程池里，主线程不阻塞。
- 拿到响应后用 `server.execute(...)` 回到服务端线程，再
  `player.sendSystemMessage(...)`；跨线程只传 `UUID`，不碰 `ServerPlayer` 对象。
- 任何异常（连不上、超时、JSON 坏掉、玩家下线、服务端关服）都只变成一条聊天栏消息或调试日志，
  不会把服务端带崩。
- 没有用 mixin，没有引入第三方库；JSON 用的是 Minecraft 自带的 Gson。

## 9. 常见问题

**`[小铜] 出错了：连不上 AI 服务…`**
DSH 侧没起，或者 `endpoint` 端口不对。先确认 `127.0.0.1:8765` 有服务在听，再核对
`config/mcai-common.toml`。

**`[小铜] 出错了：等 AI 回复超时了（60 秒）`**
模型那边确实排队了。可以让 DSH 侧加快，或把 `timeoutSeconds` 调大。

**`[小铜] AI 未开启`**
`mcai-common.toml` 里 `enabled = false`。改成 `true` 并重启。

**「慢一点，刚问过啦」一直出现**
`rateLimitSeconds` 被调大了。默认 5 秒，改成 0 就不限制。

**客户端要不要装？**
不用。纯服务端功能，没装也能连服，不会弹版本红字。

**能改配置不重启吗？**
不能。Forge 的 COMMON 配置在启动时读取，改完重启服务端。
