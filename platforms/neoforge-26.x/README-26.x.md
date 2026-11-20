# mcai —— NeoForge 26.x（MC 26.1 / 26.2 / 26.3）双端工程

`platforms/neoforge-26.x/` 是 mcai 1.4.0 的 NeoForge 26.x 实现：一个 Gradle 工程，
用 `-Pmc=` / `-Pneo=` 切版本，一次产出两个 jar（服务端 `mcai`、客户端 `mcai_client`）。

```
platforms/neoforge-26.x/
├── build.gradle            # 根：跑完两个子工程后把 jar 收到 build/libs
├── settings.gradle         # include 'server', 'client'
├── gradle.properties       # mc / neo / mod_version 默认值（CI 用 -P 覆盖）
├── gradlew, gradle/        # Gradle 9.2.1 wrapper（与 MDK 26.3 一致）
├── server/                 # modId=mcai，服务端必装
│   ├── build.gradle
│   └── src/main/java/com/mogutofu/mcai/neoforge/…
├── client/                 # modId=mcai_client，客户端选装
│   ├── build.gradle
│   └── src/main/java/com/mogutofu/mcai/client/…
```

MC 26.x 是去混淆版本（官方名、Java 25），所以这里不用任何 mapping 配置：
ModDevGradle 2.0.148 + NeoForge 版本号即可，代码里直接用 `net.minecraft.*` 官方名。

## 产物命名

| jar | modId | 文件名 |
| --- | --- | --- |
| 服务端 | `mcai` | `mc-ai-neoforge-26_1-v1_4_0.jar` / `…-26_2-…` / `…-26_3-…` |
| 客户端 | `mcai_client` | `mc-ai-client-neoforge-26_1-v1_4_0.jar` / … |

CI 上传 `platforms/neoforge-26.x/build/libs/*.jar`（根工程会把两个子工程的 jar 收进来）。

## 构建

本机 `maven.neoforged.net` 被网络阻断，**不要在本机跑 Gradle**，全部走 GitHub Actions：

```bash
# 本地只做语法检查/看代码，不构建
```

分支 `platform/neoforge-26.x` push 后触发 `.github/workflows/build-neoforge-26.yml`，
矩阵三版本并行（Java 25、ubuntu-latest）：

```bash
./gradlew build --no-daemon -Pmc=26.1 -Pneo=26.1.0.19-beta
./gradlew build --no-daemon -Pmc=26.2 -Pneo=26.2.0.95
./gradlew build --no-daemon -Pmc=26.3 -Pneo=26.3.0.113
```

## 功能（与 1.2.0 行为一致）

### `/ai`

- `/ai <内容>`、`/ai message <内容>`：所有玩家（权限 0），每玩家 `rateLimitSeconds`（默认 5 秒）冷却。
- 请求走 `common` 的 `AiSettings` / `AiClient`：POST `config/mcai-common.json` 里的 endpoint
  （默认 `http://127.0.0.1:8765/mc-ai`），body `{player,uuid,text,attachments?}`。
- 回复 `[小铜] …`（金色 + 青色）；失败 `[小铜] 出错了：…`（红色）。
- 控制台执行提示「这个指令只能在游戏里用。」

### `/ai screenshot [消息]`、`/ai file <路径> <消息>`

- 服务端必装、客户端选装；没有 `ClientHello` 时降级为只发文字并提示
  「客户端未安装 mcai-client」。
- 26.x 用 `CustomPacketPayload`（`mcai:client_hello` / `mcai:attachment_request` /
  `mcai:attachment_chunk` / `mcai:attachment_abort` / `mcai:attachment_too_large`），
  字段与 1.2.0 的 `PROTOCOL.md` 一致：24KB 分块、截图 PNG ≤ 8MB、文件 ≤ 16MB、
  服务端 15 秒超时。
- 客户端登录发 `ClientHello`；收到请求后截图（`Screenshot.takeScreenshot`）或用
  `java.nio` 读本地文件，失败发 `AttachmentAbort`。
- 单机 integrated server 不注册 payload（会与客户端 jar 撞车），按无客户端降级 ——
  与 1.20.1 版本一致。

### `/weather` + `/vote`

- 逻辑全在 `common` 的 `WeatherVoteService`，这里只实现 `Host`：
  `[天气] ` 前缀、`onlineCount`、`applyWeather`（`ServerLevel.setWeatherParameters`）、
  `config/mcai-weather.json` 每次 `/weather` 重读。
- 注册时先 `dispatcher.getRoot().getChildren().removeIf(name 是 weather/vote)`，
  替换原版需要 OP 的 `/weather`。
- 每 tick 调 `service.tick()`，玩家登出调 `service.logout(uuid)` 并清 `/ai` 冷却。
- OP 判定用 26.x 的权限系统：`Commands.LEVEL_GAMEMASTERS.check(player.permissions())`。

## 配置

| 文件 | 谁写 | 说明 |
| --- | --- | --- |
| `config/mcai-common.json` | mod 首次启动补默认值 | `endpoint` / `timeoutSeconds` / `enabled` / `rateLimitSeconds` |
| `config/mcai-weather.json` | DSH 插件设置面板（mod 只补默认值） | 每次 `/weather` 重读 |

配置目录取 `FMLPaths.GAMEDIR.resolve("config")`（= 服务器根目录下的 `config/`）。

## 26.x 与 1.21.x 的差异（本工程踩到的）

| 1.21.x | 26.x |
| --- | --- |
| `net.minecraft.resources.ResourceLocation` | `net.minecraft.resources.Identifier` |
| `FMLEnvironment.dist` | `FMLEnvironment.getDist()`（`Dist.isDedicatedServer()`） |
| `PacketDistributor.sendToServer` | `ClientPacketDistributor.sendToServer`（`net.neoforged.neoforge.client.network`） |
| `player.getGameProfile().getName()` | `player.getGameProfile().name()` |
| `player.hasPermissions(2)` | `Commands.LEVEL_GAMEMASTERS.check(player.permissions())` |
| `@Mod.EventBusSubscriber` | `@EventBusSubscriber`（`net.neoforged.fml.common`，bus 由事件类型决定） |
| `server.getPlayerList().getPlayerCount()` | `getPlayers().size()`（26.x 源码里只用后者） |

## 测试级别

- CI 编译级验证（三版本分别编译 + 打包 + 元数据检查）。
- 未做运行级烟测（本机无法联 NeoForged maven，起不了 dev server）。
