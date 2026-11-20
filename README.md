# Minecraft AI Chat (mcai) 1.3.0

在 Minecraft 服务端里加一个 `/ai` 指令：玩家直接和 DeepSeek Harness 上的「小铜」对话。
mod / 插件本身不含 AI，只把请求 POST 到本机 DSH 插件 `dsh-mc-server-ops` 提供的
`http://127.0.0.1:8765/mc-ai`，再把回复贴回聊天栏。

1.3.0 起同一套行为覆盖 **Forge / NeoForge / Fabric / Bukkit**，并保留 1.1.0 的客户端附件
（`/ai screenshot`、`/ai file`）与 1.2.0 的游戏内天气投票（`/weather` + `/vote`）。

> 服务端需要安装 [dsh-mc-server-ops](https://github.com/mogutofu/dsh-mc-server-ops) ≥ 0.6.3
> 并保持 `/mc-ai` 监听；没有它 mod 能加载，但 `/ai` 没有 AI 可聊、天气配置也无人写入。

## 支持矩阵（1.3.0 实际产物）

| 平台 | Minecraft | 加载器版本 | 服务端 jar（必装） | 客户端 jar（选装） | 客户端必须？ | 截图/文件附件 | 天气投票 | 测试级别 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Forge | 1.12.2 | Forge 14.23.5.2847+（编译基座 2847，运行时兼容 1.12.2 全系） | `mc-ai-forge-1_12_2-v1_3_0.jar` | `mc-ai-client-forge-1_12_2-v1_3_0.jar` | 否 | ✅ | ✅ | **实机**（专用服务器加载） |
| Forge | 1.20.1 | Forge 47.2.23 | `mc-ai-forge-1_20_1-v1_3_0.jar` | `mc-ai-client-forge-1_20_1-v1_3_0.jar` | 否 | ✅ | ✅ | **实机**（生产服） |
| NeoForge | 1.21.1 | NeoForge 21.1.267 | `mc-ai-neoforge-1_21_1-v1_3_0.jar` | `mc-ai-client-neoforge-1_21_1-v1_3_0.jar` | 否 | ✅ | ✅ | **实机**（专用服务器 + RCON） |
| NeoForge | 26.1 | NeoForge 26.1.0.19-beta | `mc-ai-neoforge-26_1-v1_3_0.jar` | `mc-ai-client-neoforge-26_1-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| NeoForge | 26.2 | NeoForge 26.2.0.95 | `mc-ai-neoforge-26_2-v1_3_0.jar` | `mc-ai-client-neoforge-26_2-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| NeoForge | 26.3 | NeoForge 26.3.0.113 | `mc-ai-neoforge-26_3-v1_3_0.jar` | `mc-ai-client-neoforge-26_3-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| Fabric | 26.1 | Loader 0.20.1 + Fabric API `0.145.1+26.1` | `mc-ai-fabric-26_1-v1_3_0.jar` | `mc-ai-client-fabric-26_1-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| Fabric | 26.2 | Loader 0.20.1 + Fabric API `0.161.0+26.2` | `mc-ai-fabric-26_2-v1_3_0.jar` | `mc-ai-client-fabric-26_2-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| Fabric | 26.3 | Loader 0.20.1 + Fabric API `0.166.2+26.3` | `mc-ai-fabric-26_3-v1_3_0.jar` | `mc-ai-client-fabric-26_3-v1_3_0.jar` | 否 | ✅ | ✅ | 编译 + jar 元数据 |
| Bukkit / Spigot / Paper | 1.20+ / 1.21+ / 26.x | Spigot API 1.20.1（单 jar 通用） | `mc-ai-bukkit-v1_3_0.jar` | —（无客户端附件） | 否 | ❌ | ✅ | **实机**（Paper 1.20.1） |

> Fabric API 的精确版本在 CI 里按 `+26.x` 从 Fabric Maven 自动解析，构建日志会打印；
> 安装时用对应 MC 版本的最新 Fabric API 即可。
>
> modId：服务端 `mcai`，客户端 `mcai_client`；Bukkit 插件名 `mc-ai`。
> 所有平台版本号统一 `1.3.0`，jar 文件名中的 `v1_3_0` 即版本（点替换成下划线）。

## 命令

| 命令 | 平台 | 需要客户端模组 | 说明 |
| --- | --- | --- | --- |
| `/ai <内容>` | 全部 | 否 | 兼容旧写法，等于 `/ai message <内容>` |
| `/ai message <内容>` | 全部 | 否 | 只发文字给 AI |
| `/ai screenshot [内容]` | Forge / NeoForge / Fabric | 是 | 截取当前游戏画面，和文字一起发给 AI |
| `/ai file <路径> <内容>` | Forge / NeoForge / Fabric | 是 | 读取玩家本机文件，和文字一起发给 AI |
| `/weather clear\|rain\|thunder [持续秒数]` | 全部 | 否 | 改天气（所有玩家可用，替换原版需要 OP 的 `/weather`） |
| `/vote yes` / `/vote no` / `/vote status` | 全部 | 否 | 天气投票 |

说明：

- `/ai` 每玩家限流 `rateLimitSeconds`（默认 5 秒）；回复以 `[小铜] ` 前缀出现在聊天栏。
- 客户端没装 `mcai-client` 时，`/ai screenshot`、`/ai file` **降级为只发文字**，并提示
  「客户端未安装 mcai-client」。附件只支持**专用服务器**（dedicated server）。
- `/ai file` 的路径是**玩家自己电脑上的路径**，客户端读取后只把文件字节发给服务器；
  Windows 路径不用转义反斜杠，有空格时用引号包起来。
- Bukkit 版只有文字 `/ai`；`/ai screenshot`、`/ai file` 会提示不支持并降级为只发文字。
- 截图 PNG ≤ 8MB、文件 ≤ 16MB；附件按 24KB 分块传输，每次最多 1 个附件。

## 天气投票

所有平台语义一致：

- 在线玩家 **≤ `directThreshold`（默认 2）**：直接生效，全服提示「XX 把天气改成了下雨」。
- **3 人及以上**：发起投票，发起人自动算 1 票同意；其他人用 `/vote yes` 同意、
  `/vote no` 反对、`/vote status` 查看。
- 同意达到 `approveNeed`（默认 2）通过；反对达到 `rejectNeed`（默认 1）作废；
  `timeoutSeconds`（默认 60 秒）没结果也会作废。
- 同一玩家两次发起间隔 `cooldownSeconds`（默认 60 秒）。
- 控制台 / RCON / OP 执行 `/weather` 不受投票限制，直接生效。
- `/weather` 不带参数显示用法与当前配置。

配置在服务端目录 `config/mcai-weather.json`，**每次 `/weather` 都重新读**，改完即时生效：

```json
{
  "enabled": true,
  "directThreshold": 2,
  "approveNeed": 2,
  "rejectNeed": 1,
  "timeoutSeconds": 60,
  "cooldownSeconds": 60
}
```

该文件通常由 DSH 插件设置面板 →「游戏内天气投票」写入；手动改也可以，但插件下次保存会覆盖。
文件缺失/损坏时回落到默认值。

## 安装

### Forge / NeoForge / Fabric

1. 服务端 `mods/` 放入对应平台的服务端 jar（`mc-ai-<loader>-<mc>-v1_3_0.jar`）。
2. 客户端（可选）`mods/` 放入同一平台的客户端 jar
   （`mc-ai-client-<loader>-<mc>-v1_3_0.jar`）；不装也能进服，只是附件命令降级。
3. 服务端装好 DSH 与 `dsh-mc-server-ops`（≥ 0.6.3），保持 `/mc-ai` 监听。
4. 重启服务器；首次启动生成 `/ai` 配置（Forge 1.20.1 是 `config/mcai-common.toml`，
   其它平台是 `config/mcai-common.json`）。
5. 游戏内 `/ai 你好`，或 `/weather rain` 试试天气。

### Bukkit / Spigot / Paper

1. 把 `mc-ai-bukkit-v1_3_0.jar` 放进服务端 `plugins/`（一个 jar 同时支持 1.20+、1.21+、26.x）。
2. 重启服务器；插件会生成 `config/mcai-common.json` 与 `config/mcai-weather.json`
   （都在**服务器根目录**的 `config/` 下，不是 `plugins/mc-ai/`）。
3. 服务端装好 DSH 与 `dsh-mc-server-ops`（同上）。
4. 游戏内 `/ai 你好`、`/weather rain`、`/vote status`。

Bukkit 版的 `/weather` 通过拦截玩家命令实现：玩家输入 `/weather` 时插件接管；
控制台/RCON 仍走原版 `/weather`（本来就有权限，直接生效）。OP 玩家由插件直接执行。

## 配置（`/ai`）

Forge 1.20.1 沿用 `config/mcai-common.toml`：

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `endpoint` | `http://127.0.0.1:8765/mc-ai` | DSH 插件地址 |
| `timeoutSeconds` | `60` | 单次请求超时；带附件请求至少 90 秒 |
| `enabled` | `true` | 关闭后 `/ai` 只提示「AI 未开启」 |
| `rateLimitSeconds` | `5` | 同一玩家两次请求最小间隔，`0` 不限 |

其它平台（含 Bukkit）用 `config/mcai-common.json`，字段相同：

```json
{
  "endpoint": "http://127.0.0.1:8765/mc-ai",
  "timeoutSeconds": 60,
  "enabled": true,
  "rateLimitSeconds": 5
}
```

## 工作方式

- 服务端把玩家名、UUID、文字、可选附件（base64）POST 到本机 DSH；HTTP 契约见
  [PROTOCOL.md](PROTOCOL.md)。
- 客户端 ↔ 服务端：Forge 1.20.1/1.12.2 用 `mcai:net` 单通道；NeoForge/Fabric 用 5 个
  自定义 payload；附件 24KB 分块。详见 [PROTOCOL.md](PROTOCOL.md)。
- 图片附件由 DSH 交给模型直接看图；文本类小文件内联进消息，其它文件落盘后把只读路径
  告诉 AI（由 DSH 负责）。
- 天气投票完全在服务端生效，不经过 DSH、不消耗 token；只有配置从 DSH 插件写文件过来。

## 从源码构建

仓库结构：

```
common/                 与加载器无关的核心（Java 8 语法，零依赖）
src/ + client/          Forge 1.20.1 服务端 + 客户端（ForgeGradle 6）
platforms/bukkit/       Bukkit 通用插件（Spigot API 1.20.1，Java 17）
platforms/forge-1.12.2/ Forge 1.12.2 服务端 + 客户端（ForgeGradle 2.3，Java 8）
platforms/neoforge/     NeoForge 1.21.1 服务端 + 客户端（ModDevGradle）
platforms/neoforge-26.x/ NeoForge 26.1/26.2/26.3 服务端 + 客户端（ModDevGradle）
platforms/fabric/       Fabric 26.x 服务端 + 客户端（Loom 1.19，Java 25）
tests/                  WeatherVoteService 纯逻辑自测
.github/workflows/      分平台 CI（产物名即发布名）
```

- **CI 构建（推荐）**：每个平台一个 workflow，push 对应路径或手动 `workflow_dispatch`；
  产物在 Actions 的 artifact 里，文件名已经是发布名。
- **本机 Forge 1.20.1**：JDK 17 + Gradle 8.1.1，`./gradlew build`（服务端）和
  `cd client && ./gradlew build`（客户端）。
- **天气逻辑自测**：`tests/WeatherVoteSelfTest.java`，不依赖 Minecraft：
  ```bash
  javac -encoding UTF-8 -d out common/src/main/java/com/mogutofu/mcai/core/*.java tests/WeatherVoteSelfTest.java
  java -cp out WeatherVoteSelfTest
  ```
- NeoForge / Fabric 的 Gradle 依赖在部分网络环境不可直连，仓库的 CI 在 GitHub Actions
  上跑；本机能否构建取决于 `maven.neoforged.net` / `maven.fabricmc.net` 的连通性。

> Forge 1.20.1 在 DSH 的 Windows 沙箱里构建会遇到 ForgeGradle AT 阶段
> `ReadOnlyFileSystemException`（JDK zipfs 在受限令牌下把文件系统当只读），
> 本机用「给 `jdk.zipfs` 打 `--patch-module` 补丁」绕过；普通开发机/CI 不需要。

## 测试状态（1.3.0）

- **Forge 1.20.1**：部署到生产服实测。`/ai` 中文请求 → DSH `/mc-ai` HTTP 200 →
  `[小铜]` 回复；客户端 1.2.0 握手成功（协议 1，附件协议未变）；`/weather rain 120`
  在 1 人在线时直接生效；服务端日志确认 `/weather`、`/vote` 注册成功。
- **Bukkit（Paper 1.20.1）**：临时服 + 2 个 mineflayer 机器人实测。`/weather` 用法、
  2 人在线直接改雨、`/ai` HTTP 200 并回复、把 `directThreshold` 改 0 后
  `/weather thunder` 发起投票、机器人 `/vote yes` 后 2/2 通过并生效。
- **天气状态机**：`WeatherVoteSelfTest` 覆盖直接生效、投票通过、否决、OP/控制台直接、
  冷却、超时，全部通过。
- **Forge 1.12.2**：CI 里用官方 installer 起了一次专用服务器，日志确认 `mcai` 加载、
  `mcai:net` 通道注册、`/weather` 替换原版、`/ai`/`/weather`/`/vote` 注册、服务器 `Done`；
  未做玩家交互。
- **NeoForge 1.21.1**：CI 里用 `runServer` 起了专用服务器，日志确认 `mcai 1.3.0 (mcai)` 加载、
  `/ai`/`/weather`/`/vote` 注册、RCON 执行 `weather` 返回用法、`ai` 返回用法、
  `vote status` 返回「只有游戏内玩家能投票」，随后 RCON 正常停服。
- **NeoForge 26.x / Fabric**：CI 编译通过 + `jar tf` 元数据校验；**未做实机运行测试**
  （本机网络无法拉取 NeoForge/Fabric 运行时依赖，内存也只有 8GB 且生产服在跑）。
  这些平台的 jar 作为「可用但未实机验证」发布。

## 已知限制

- Bukkit 版没有客户端附件（`/ai screenshot`、`/ai file` 会降级），其余行为与 mod 版一致。
- Forge 1.12.2 编译基座是 14.23.5.2847（官方 maven 上 2854+ 没有 `-userdev` 制品）；产物运行在 1.12.2 全系（含 2860）。旧 API（SimpleImpl、旧命令注册、Java 8）；客户端截图走 GL 读像素 +
  PNG 编码，与新版实现不同但协议一致。
- NeoForge 26.1 上游是 `26.1.0.19-beta`；26.x 使用 Java 25，MC 26.x 已去混淆。
- 附件只支持专用服务器；单机/内置服务器按「没有客户端模组」降级。
- 天气投票提示统一 `[天气] ` 前缀；`/ai` 回复统一 `[小铜] ` 前缀。Bukkit 版通过
  `PlayerCommandPreprocessEvent` 接管 `/weather`，如果别的插件也取消该事件，以其它插件为准。
- 只连本机 DSH（`127.0.0.1:8765`）；不要把这个端口暴露到公网。

## FAQ

**客户端没装会怎样？** 服务端照常工作，`/ai`、`/ai message`、`/weather`、`/vote` 正常；
`/ai screenshot`、`/ai file` 降级为只发文字并提示。

**客户端装了、服务器没装服务端模组？** Forge/NeoForge/Fabric 客户端模组都声明了服务端
依赖 `mcai`，服务器没装时不会收到附件请求；Bukkit 没有客户端模组。

**截图很大怎么办？** 4K PNG 可能超过 8MB，客户端会放弃发送并提示；降低分辨率/窗口重试。

**文件会存到哪里？** 玩家本地文件只在客户端读取并发送字节；服务端只转发给本机 DSH。

**天气配置在哪改？** DSH 插件设置面板 →「游戏内天气投票」；或直接改服务端
`config/mcai-weather.json`（每次 `/weather` 重读）。Bukkit 版路径相同。

**为什么 NeoForge 26.x / Fabric 没有实机测试？** 本机 `maven.neoforged.net` 被网络阻断，
NeoForge 26.x / Fabric 的运行时依赖拉不下来；CI runner 只做构建，没有为这些版本起完整测试服。
NeoForge 1.21.1 已经在 CI 里起过 `runServer` 并用 RCON 验证了命令。因此 26.x / Fabric 标注
「编译通过、未实机验证」。

## License

MIT
