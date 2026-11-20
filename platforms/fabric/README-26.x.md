# mc-ai · Fabric 26.x 移植说明（1.3.0）

本目录是 mcai 的 Fabric 26.x 实现，**一个 Gradle 工程产出两个 jar**：服务端 `mcai` +
可选客户端 `mcai_client`，MC 26.1 / 26.2 / 26.3 各一对（矩阵在 CI 里跑）。

行为对齐 1.2.0（Forge 1.20.1）/ 1.3.0 移植规范 `docs/PORTING-1.3.0.md`，公共逻辑直接用
`common/src/main/java/com/mogutofu/mcai/core/`（`AiSettings` / `AiClient` / `Attachment` /
`Cooldown` / `WeatherSettings` / `WeatherVoteService` / `Actor` / `Json`），没有改 common。

## 产物

| 版本 | 服务端 | 客户端 |
| --- | --- | --- |
| MC 26.1 | `mc-ai-fabric-26_1-v1_3_0.jar` | `mc-ai-client-fabric-26_1-v1_3_0.jar` |
| MC 26.2 | `mc-ai-fabric-26_2-v1_3_0.jar` | `mc-ai-client-fabric-26_2-v1_3_0.jar` |
| MC 26.3 | `mc-ai-fabric-26_3-v1_3_0.jar` | `mc-ai-client-fabric-26_3-v1_3_0.jar` |

- 服务端 jar：mod id `mcai`，`environment: "server"`，入口 `com.mogutofu.mcai.fabric.McaiFabric`。
- 客户端 jar：mod id `mcai_client`，`environment: "client"`，入口 `com.mogutofu.mcai.client.McaiClientFabric`。
- 两个 jar 都要求 Fabric Loader ≥ 0.20.1、Fabric API、Java ≥ 25。
- 服务端模组不装进客户端（`environment: server`），所以**单机/局域网内置服务端不支持附件**
  （和 Forge 1.2.0 只在 dedicated server 注册通道的限制一致）；专用服务器 + 客户端模组可以。

## 构建

技术栈：Fabric Loader 0.20.1、Fabric Loom 1.19-SNAPSHOT、Java 25、Gradle 9.8.2、
26.x 是 non-remapping（官方 Mojang 名，不需要 mappings）。

CI：`.github/workflows/build-fabric.yml`（push 限定 `platforms/fabric/**`、`common/**`、
该 workflow；也支持 `workflow_dispatch`）。矩阵三版本，关键点：

1. `actions/setup-java@v4` temurin 25；
2. `gradle/actions/setup-gradle@v4` 指定 Gradle 9.8.2；
3. 构建前先查
   `https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml`，
   筛出 `<version>…+<mc></version>`、去掉 alpha/beta/pre/rc/snapshot、`sort -V` 取最后一个，
   用 `-Pfabric_api=<version>` 传给 Gradle（**不猜版本**；查不到直接失败）；
4. `gradle build --no-daemon -Pmc=26.x -Pfabric_api=…`；
5. 只上传上面两个最终名字的 jar。

本地构建（需要能访问 maven.fabricmc.net / Mojang）：

```bash
cd platforms/fabric
gradle build --no-daemon -Pmc=26.3 -Pfabric_api=0.166.2+26.3
```

`gradle.properties` 里三个版本都有默认 fabric-api（26.1 = `0.145.1+26.1`、
26.2 = `0.161.0+26.2`、26.3 = `0.166.2+26.3`，都来自 CI 实际解析结果），本地可以不带
`-Pfabric_api`；CI 仍会重新查 maven-metadata.xml 取各分支最新稳定版。

> 说明：仓库里没有提交 `gradle/wrapper/gradle-wrapper.jar`（本机沙箱里拿不到二进制），
> CI 用 setup-gradle 安装的 Gradle 9.8.2 构建；需要 wrapper 时可以跑一次
> `gradle wrapper --gradle-version 9.8.2` 生成。

## 配置

| 文件 | 内容 |
| --- | --- |
| `config/mcai-common.json` | `/ai` 的 endpoint / timeoutSeconds / enabled / rateLimitSeconds（`AiSettings`，首次启动写默认值） |
| `config/mcai-weather.json` | 天气投票 `enabled/directThreshold/approveNeed/rejectNeed/timeoutSeconds/cooldownSeconds`，**每次 `/weather` 重新读**（DSH 插件设置面板写入，缺失/损坏回落默认值） |

默认天气配置：`{"enabled":true,"directThreshold":2,"approveNeed":2,"rejectNeed":1,"timeoutSeconds":60,"cooldownSeconds":60}`。

## 功能

### `/ai`

- `/ai <内容>`（旧格式）、`/ai message <内容>`：所有玩家（权限 0），每玩家
  `rateLimitSeconds`（默认 5 秒）冷却，`[小铜] 正在输入…` → POST
  `config/mcai-common.json` 的 endpoint（默认 `http://127.0.0.1:8765/mc-ai`），
  回复 `[小铜] <reply>`（金色前缀 + 青色正文），失败 `[小铜] 出错了：…`。
- 控制台执行提示「这个指令只能在游戏里用。」

### `/ai screenshot [消息]`、`/ai file <本地路径> <消息>`

- 服务端必装、客户端选装。服务端没收到该玩家的 `ClientHello` 时降级为只发文字，并提示
  `客户端未安装 mcai-client`（消息为空时只提示用法）。
- 截图 PNG ≤ 8MB、文件 ≤ 16MB，每次最多 1 个附件，24KB（24576 字节）分块；
  服务端 15 秒收不到就作废并降级为只发文字；客户端失败发 `AttachmentAbort`。
- 文件读取在客户端做；路径支持 `"C:\Users\me\a.md"` 引号写法与无空格裸路径。

### `/weather` + `/vote`

- 注册时先 `dispatcher.getRoot().getChildren().removeIf(n -> "weather".equals(n.getName()) || "vote".equals(n.getName()))`
  再注册自己的 `weather` / `vote`，覆盖原版需要 OP 的 `/weather`。
- `WeatherVoteService` 完整状态机：在线 ≤ `directThreshold` 直接生效；≥3 人发起投票，
  发起人自动 1 票同意；`approveNeed` 通过、`rejectNeed` 作废、超时作废、发起冷却；
  控制台 / OP 直接执行；`ServerTickEvents.END_SERVER_TICK` 每 tick `tick()`；
  玩家断开调 `logout(uuid)`。
- 文案前缀 `[天气] `（由 Host 加，与 Forge 1.2.0 一致）。

## Fabric 与 Forge 1.2.0 的实现差异

| 点 | Forge 1.2.0 | Fabric 26.x |
| --- | --- | --- |
| 通道 | 单通道 `mcai:net` + 消息索引 | 5 个 payload：`mcai:hello` / `mcai:request` / `mcai:chunk` / `mcai:abort` / `mcai:too_large`，字段、方向、24KB 分块、上限完全一致 |
| 序列化 | `FriendlyByteBuf`（SimpleChannel 注册 encode/decode） | `StreamCodec<FriendlyByteBuf, T>`（`CustomPacketPayload`），读写同样的字段顺序 |
| 服务端判定客户端 | `ClientPresence` + hello | 同左；发请求前额外用 `ServerPlayNetworking.canSend` 兜底 |
| 客户端判断服务器支持 | SimpleChannel 版本协商 | `ClientPlayNetworking.canSend`，服务器没装就跳过 hello |
| 事件 | Forge 事件总线 | `CommandRegistrationCallback` / `ServerTickEvents` / `ServerPlayConnectionEvents.DISCONNECT` / `ServerLifecycleEvents` |
| 配置 | `config/mcai-common.toml`（ForgeConfigSpec） | `config/mcai-common.json`（公共核心 `AiSettings`） |

## 已知限制 / 风险

- **本机无法联网跑 Gradle**：`maven.fabricmc.net` 在本机被阻断，构建全部走 GitHub Actions；
  26.x 的 API 面（尤其 `Screenshot`、`CommandSourceStack` 权限、`PayloadTypeRegistry` /
  `ServerPlayNetworking` 的 26.x 签名）以 CI 编译结果为准，如有差异按报错逐版本修。
- **OP 判定**用反射调 `CommandSourceStack.hasPermission(int)`：26.x 权限 API 改过几轮，
  拿不到就当普通玩家（OP 会多走一次投票，不会崩）。
- **无内置服务端附件支持**（见上）。客户端 `environment: client`，服务端 `environment: server`，
  两个 jar 不能同时装在同一客户端（服务端 jar 在客户端本来也不会加载）。
- 客户端截图 API 只按 1.2.0 的 `Screenshot.takeScreenshot(getMainRenderTarget())` 写，
  如果 26.x 改名，改一个方法即可（`ClientAttachmentTransfer#captureScreenshot`）。
