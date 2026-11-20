# mcai —— Forge 1.12.2 实现

`v1.3.0` 多平台移植的 Forge 1.12.2 分支。行为以 `1.2.0` 为准，公共逻辑复用
`common/src/main/java/com/mogutofu/mcai/core/`（Java 8、零依赖，未改动）。

| 项 | 值 |
| --- | --- |
| Minecraft | 1.12.2 |
| Forge（编译基座） | 14.23.5.2847，运行时兼容 1.12.2 各 Forge 版本 |
| 构建 | ForgeGradle 2.3-SNAPSHOT + Gradle 4.10.3 + JDK 8 |
| Mappings | snapshot_20171003 |
| 频道 | `mcai:net`（协议版本 1） |

> **为什么不是 14.23.5.2860**：Forge 官方 maven 上 1.12.2 的 14.23.5.2854 / 2855 / 2859 / 2860
> 都没有 `-userdev` 制品（CI 探针实测 404），ForgeGradle 2.3 的 `extractUserdev` 直接失败。
> 2847 是 1.12.2 最后一个有 userdev 的常用构建，API 与 2860 一致，编译出的 jar 里
> `mcmod.info` / `@Mod` 仍写 1.12.2（`acceptedMinecraftVersions = "[1.12.2]"`），
> 放到 14.23.5.2860 的服务端可直接用。

## 目录

```
platforms/forge-1.12.2/
├── server/          服务端模组 mcai（必装）
│   ├── build.gradle         FG 2.3 工程，源码集里加了 ../../../common/src/main/java
│   ├── settings.gradle
│   ├── gradle.properties
│   └── src/main/
│       ├── java/com/mogutofu/mcai/forge/      指令、事件、天气 Host、附件状态机
│       │   └── net/                            mcai:net 消息（1.12.2 SimpleImpl）
│       └── resources/mcmod.info
└── client/          客户端模组 mcai_client（选装，纯客户端）
    ├── build.gradle
    └── src/main/
        ├── java/com/mogutofu/mcai/forge/client/   ClientHello、截图、分块发送
        │   └── net/                               消息副本（字段与服务端一致）
        └── resources/mcmod.info
```

两个工程各自是独立 Gradle 工程（各有 `settings.gradle`），互不依赖；构建脚本刻意贴着
1.12.2 MDK 的写法，`jar` 由 ForgeGradle 自动追加 `reobfJar`（产物就是重混淆后的可发布 jar）。

## 产物

| 文件 | modId | 说明 |
| --- | --- | --- |
| `mc-ai-forge-1_12_2-v1_3_0.jar` | `mcai` | 服务端模组，丢进服务端 `mods/` |
| `mc-ai-client-forge-1_12_2-v1_3_0.jar` | `mcai_client` | 纯客户端（`clientSideOnly = true`），丢进客户端 `mods/` |

## 功能

### `/ai`（所有玩家，权限 0）

```
/ai <内容>                    旧格式，直接发消息
/ai message <内容>            同上，显式写法
/ai screenshot [消息]         截图 + 消息（需要客户端 mcai-client）
/ai file <本地路径> <消息>    本地文件 + 消息（路径有空格用引号）
```

- 每玩家冷却 `rateLimitSeconds`（默认 5 秒），提示「慢一点，刚问过啦」。
- POST `config/mcai-common.json` 里的 endpoint（默认 `http://127.0.0.1:8765/mc-ai`），
  body `{player,uuid,text,attachments?}`，响应 `{ok,reply,elapsedMs}` / `{ok:false,error}`（契约同 PROTOCOL.md）。
- 回复以 `[小铜] ` 前缀出现在聊天栏（成功金色 + 青色正文，失败红色 `[小铜] 出错了：`）。
- 控制台执行提示「这个指令只能在游戏里用。」
- HTTP 走公共核心 `AiClient`（Java 8 `HttpURLConnection`，`mcai-http` 线程池），回调切回服务端主线程。

### 附件（服务端必装 + 客户端选装）

- 5 个消息，索引与服务端/客户端一一对应：
  `0 ClientHello`(C→S)、`1 AttachmentRequest`(S→C)、`2 AttachmentChunk`(C→S)、
  `3 AttachmentAbort`(C→S)、`4 AttachmentTooLarge`(S→C，可空实现)。
- 分块 24KB（24576 字节），截图 PNG ≤ 8MB、文件 ≤ 16MB，每次最多 1 个附件，服务端 15 秒超时。
- 客户端进服后发 ClientHello（在客户端 tick 里等 `Minecraft.player` 出现再发，1.12.2 的连接事件触发太早）；
  服务端记录 hello，无 hello 即视为未装客户端。
- 未装客户端 → 降级为只发文字并提示 `客户端未安装 mcai-client`；客户端失败 → 回 AttachmentAbort，同样降级。
- 截图：`GL11.glReadPixels` 读主 framebuffer + `ImageIO` 写 PNG（Java 8 自带，不额外打包依赖）。
  1.12.2 的 `ScreenShotHelper` 只会落盘到 `screenshots/`，还得再读文件，所以没用它。
- 客户端只读本机文件、只把字节发给服务器，不联网。

### `/weather` + `/vote`（普通玩家可用）

- 1.12.2 的 `/weather` 是原版指令且要求 OP：在 `FMLServerStartingEvent` 里先从
  `CommandManager` 的 name→ICommand 表删掉原版 `weather`（`getCommands().remove(...)`，
  表不可变时反射扫 Map 字段兜底），再 `registerServerCommand` 注册自己的 `CommandWeather`。
- 状态机全部在公共核心 `WeatherVoteService`（未改动），文案与 1.20.1 / 1.2.0 一致：
  在线 ≤ `directThreshold`（默认 2）直接生效；否则发起投票，发起人自动 1 票，
  `approveNeed`（2）票通过、`rejectNeed`（1）票反对作废、60 秒超时、60 秒发起冷却；
  控制台 / OP 直接执行。
- `ServerTickEvent` 每 tick 调 `service.tick()`；`PlayerLoggedOutEvent` 调 `service.logout(uuid)`。
- 改天气用原版 `WorldInfo` 的四个字段（跟 1.12.2 原版 `/weather` 一样）：
  clear → `cleanWeatherTime`；rain/thunder → `rainTime` + `thunderTime`。

## 配置

| 文件 | 谁写 | 说明 |
| --- | --- | --- |
| `config/mcai-common.json` | mod 首次启动写默认值 | `endpoint` / `timeoutSeconds` / `enabled` / `rateLimitSeconds`，每次 `/ai` 重新读 |
| `config/mcai-weather.json` | DSH 插件 `dsh-mc-server-ops` 写 | 每次 `/weather` 重新读，缺失/损坏回落到默认值 |

日志统一走 logger 名 `mcai` / `mcai_client`，正文带 `[mcai]` / `[mcai-client]` 前缀；
天气相关带 `[天气]` 前缀（与聊天栏文案一致）。

## 构建

本机没有 JDK 8，也没有 Forge/Maven 网络，所以**只用 GitHub Actions 构建**：

`.github/workflows/build-forge-1122.yml`：`ubuntu-latest` + `setup-java` temurin 8 +
`setup-gradle` 指定 `gradle-version: 4.10.3`，两个工程各跑一次
`gradle build --no-daemon`，产物 `build/libs/*.jar` 上传成 artifact `mcai-forge-1.12.2`。

模板里用 `gradle build` 而不是 `./gradlew`：工程里没有放 wrapper jar（二进制不方便提交），
Gradle 版本由 `gradle/actions/setup-gradle@v4` 的 `gradle-version` 指定。

## 与 1.20.1 实现的差异

| 项 | 1.20.1 | 1.12.2 |
| --- | --- | --- |
| 指令 | Brigadier `Commands.literal` | `CommandBase` + 手写参数分支（字面量优先匹配） |
| 事件订阅 | `@Mod.EventBusSubscriber` | 手动 `MinecraftForge.EVENT_BUS.register(...)` |
| 网络 | `NetworkRegistry.newSimpleChannel(name, version, ...)` | `NetworkRegistry.INSTANCE.newSimpleChannel(name)` + SimpleImpl `IMessage`/`IMessageHandler` |
| 版本协商 | SimpleChannel 自动 | 协议版本放在 `ClientHello.protocolVersion`，服务端比对后忽略不一致的 hello |
| 配置 | `ForgeConfigSpec`（toml） | 公共核心的 `AiSettings`（json），每次读文件 |
| 发消息 | `Component` / `sendSystemMessage` | `TextComponentString` / `sendMessage` |
| 玩家 UUID | `player.getUUID()` | `player.getUniqueID()` |
| 客户端 hello 时机 | `ClientPlayerNetworkEvent.LoggingIn` | `ClientTickEvent` 里等 `Minecraft.player` 出现 |
| 截图 | `Screenshot.takeScreenshot` + `NativeImage` | `GL11.glReadPixels` + `ImageIO` |

## 自测（CI 里真跑）

`build-forge-1122.yml` 在编译打包之后会做一次**运行级烟测**：下载 Forge 1.12.2 安装器装一个
临时专用服务器，把两个 jar 丢进 `mods/`，真启动一次，然后校验：

- 服务器起到 `Done (`；
- `[mcai] Minecraft AI Chat 已加载`、`[mcai] 已注册网络通道 mcai:net（协议 1）`；
- `[mcai] 已替换原版 /weather`、`[mcai] 已注册 /ai、/weather、/vote`；
- `[mcai]` 没有 ERROR/FATAL。

判定不过直接红，`smoke/server.log` 作为 artifact `mcai-forge-1.12.2-smoke-log` 上传。

已实测（2026-11-20，ubuntu-latest + temurin 8）：

```
[mcai] Minecraft AI Chat 已加载，/ai 指令准备就绪
[mcai] 已注册网络通道 mcai:net（协议 1）
[FML] Disabling mod mcai_client it is client side only.     ← 客户端 jar 放进服务端会被跳过
[09:52] [Server thread/INFO] [mcai]: [mcai] 已替换原版 /weather（原版需要 OP，现在普通玩家也能发起天气投票）
[mcai] 已注册 /ai、/weather、/vote（游戏内天气投票）
Forge Mod Loader has successfully loaded 5 mods
Done (3.534s)! For help, type "help" or "?"
```

## 已知限制 / 注意

- 附件只在**专用服务器**上启用（与 1.20.1 一致）：单机/局域网里服务端那半跑在客户端 JVM 里，
  `mcai:net` 不注册，`/ai screenshot`、`/ai file` 降级成纯文字。
- 1.12.2 的 SimpleImpl 没有频道版本协商，两个 jar 的 5 个消息索引必须保持一致；
  改协议要同时改服务端 `net/` 与客户端 `net/` 两份 `NetProtocol`。
- `MessageSources` 按「字段类型是 `EntityPlayerMP`」从 `NetHandlerPlayServer` 找发包玩家，
  不写死字段名（开发环境叫 `player`、生产环境是 srg 名），避免重混淆后取不到。
- 截图/文件附件这条链路只做了编译 + 元数据校验，没在真客户端里跑过（CI 起不了图形客户端）；
  服务端侧的加载、指令注册、原版 `/weather` 替换是实跑验证过的。
- 聊天栏里没有补 `assets/mcai/lang/en_us.lang`，专用服务器启动时 FML 会为它打一条
  `Missing English translation for mcai` 的 WARN（无功能影响）。
