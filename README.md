# Minecraft AI Chat (mcai) 1.4.0

在 Minecraft 服务端里加一个 `/ai` 指令：玩家直接和 DeepSeek Harness 上的「小铜」对话。
mod / 插件本身不含 AI，只把请求 POST 到本机 DSH 插件 `dsh-mc-server-ops` 提供的
`http://127.0.0.1:8765/mc-ai`，再把回复贴回聊天栏。

1.3.0 起同一套行为覆盖 **Forge / NeoForge / Fabric / Bukkit**，并保留 1.1.0 的客户端附件
（`/ai screenshot`、`/ai file`）与 1.2.0 的游戏内天气投票（`/weather` + `/vote`）。

**1.4.0 新增 Bukkit 附件桥接**：服务器只装 Bukkit/Spigot/Paper 插件、没有 `mcai` 服务端模组时，
装了 **mcai-client 1.4.0** 的玩家也能用 `/ai screenshot`、`/ai file`。截图/文件字节走插件自带的
HTTP 上传端口（默认 `25566`），不塞 Minecraft 的 32KB 自定义包；服务端只需要在
`plugins/mc-ai/config.yml` 里配置端口等参数。原有的模组通道一行没动，模组服行为与 1.3.0 完全一致。

> 服务端需要安装 [dsh-mc-server-ops](https://github.com/mogutofu/dsh-mc-server-ops) ≥ 0.6.3
> 并保持 `/mc-ai` 监听；没有它 mod 能加载，但 `/ai` 没有 AI 可聊、天气配置也无人写入。

## 支持矩阵（1.4.0 实际产物）

| 平台 | Minecraft | 加载器版本 | 服务端 jar（必装） | 客户端 jar（选装） | 客户端必须？ | 截图/文件附件 | 天气投票 | 测试级别 |
| --- | --- | --- | --- | --- | --- | --- | --- | --- |
| Forge | 1.12.2 | Forge 14.23.5.2847+（编译基座 2847，运行时兼容 1.12.2 全系） | `mc-ai-forge-1_12_2-v1_4_0.jar` | `mc-ai-client-forge-1_12_2-v1_4_0.jar` | 否 | ✅ | ✅ | CI（专用服务器加载 + 命令注册） |
| Forge | 1.20.1 | Forge 47.2.23 | `mc-ai-forge-1_20_1-v1_4_0.jar` | `mc-ai-client-forge-1_20_1-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | **实机**（生产服） |
| NeoForge | 1.21.1 | NeoForge 21.1.267 | `mc-ai-neoforge-1_21_1-v1_4_0.jar` | `mc-ai-client-neoforge-1_21_1-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | CI（`runServer` + RCON）；客户端桥接编译级 |
| NeoForge | 26.1 | NeoForge 26.1.0.19-beta | `mc-ai-neoforge-26_1-v1_4_0.jar` | `mc-ai-client-neoforge-26_1-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| NeoForge | 26.2 | NeoForge 26.2.0.95 | `mc-ai-neoforge-26_2-v1_4_0.jar` | `mc-ai-client-neoforge-26_2-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| NeoForge | 26.3 | NeoForge 26.3.0.113 | `mc-ai-neoforge-26_3-v1_4_0.jar` | `mc-ai-client-neoforge-26_3-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| Fabric | 26.1 | Loader 0.20.1 + Fabric API `0.145.1+26.1` | `mc-ai-fabric-26_1-v1_4_0.jar` | `mc-ai-client-fabric-26_1-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| Fabric | 26.2 | Loader 0.20.1 + Fabric API `0.161.0+26.2` | `mc-ai-fabric-26_2-v1_4_0.jar` | `mc-ai-client-fabric-26_2-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| Fabric | 26.3 | Loader 0.20.1 + Fabric API `0.166.2+26.3` | `mc-ai-fabric-26_3-v1_4_0.jar` | `mc-ai-client-fabric-26_3-v1_4_0.jar` | 否 | ✅ / ✅ Bukkit 桥接 | ✅ | 编译 + jar 元数据 |
| Bukkit / Spigot / Paper | 1.20+ / 1.21+ / 26.x | Spigot API 1.20.1（单 jar 通用） | `mc-ai-bukkit-v1_4_0.jar` | 用同版本 `mc-ai-client-<loader>-<mc>-v1_4_0.jar` | 否 | ✅（1.4.0 客户端 + 桥接） | ✅ | **实机**（Paper 1.20.1，33 项集成测试） |

> 「✅ / ✅ Bukkit 桥接」= 装在模组服上走原模组通道；装在只装 Bukkit 插件的服务器上走 1.4.0 的
> `mcai:bridge` + HTTP 上传。
>
> Fabric API 的精确版本在 CI 里按 `+26.x` 从 Fabric Maven 自动解析，构建日志会打印；
> 安装时用对应 MC 版本的最新 Fabric API 即可。
>
> modId：服务端 `mcai`，客户端 `mcai_client`；Bukkit 插件名 `mc-ai`。
> 所有平台版本号统一 `1.4.0`，jar 文件名中的 `v1_4_0` 即版本（点替换成下划线）。

## 命令

| 命令 | 平台 | 需要客户端模组 | 说明 |
| --- | --- | --- | --- |
| `/ai <内容>` | 全部 | 否 | 兼容旧写法，等于 `/ai message <内容>` |
| `/ai message <内容>` | 全部 | 否 | 只发文字给 AI |
| `/ai screenshot [内容]` | Forge / NeoForge / Fabric / Bukkit | 是（1.4.0） | 截取当前游戏画面，和文字一起发给 AI |
| `/ai file <路径> <消息>` | Forge / NeoForge / Fabric / Bukkit | 是（1.4.0） | 读取玩家本机文件，和文字一起发给 AI |
| `/weather clear\|rain\|thunder [持续秒数]` | 全部 | 否 | 改天气（所有玩家可用，替换原版需要 OP 的 `/weather`） |
| `/vote yes` / `/vote no` / `/vote status` | 全部 | 否 | 天气投票 |

说明：

- `/ai` 每玩家限流 `rateLimitSeconds`（默认 5 秒）；回复以 `[小铜] ` 前缀出现在聊天栏。
- 客户端没装 `mcai-client` 1.4.0 时，`/ai screenshot`、`/ai file` **降级为只发文字**，并提示
  「客户端未安装 mcai-client 1.4.0」。附件只支持**专用服务器**（dedicated server）。
- `/ai file` 的路径是**玩家自己电脑上的路径**，客户端读取后只把文件字节发给服务器；
  Windows 路径不用转义反斜杠，有空格时用引号包起来。
- Bukkit 版：文字 `/ai`、`/weather`、`/vote` 与模组版一致；附件命令在 1.4.0 客户端 + 桥接可用时
  走桥接，否则降级为只发文字。
- 截图 PNG ≤ 8MB、文件 ≤ 16MB；模组通道按 24KB 分块传输，每次最多 1 个附件；
  Bukkit 桥接走 HTTP，大小上限可在 `plugins/mc-ai/config.yml` 调。

## Bukkit 附件桥接（1.4.0 新增）

只装 Bukkit/Spigot/Paper 插件的服务器，没有 `mcai` 服务端模组，所以没有 `mcai:net` 通道。
1.4.0 增加一条独立的 raw 插件通道 `mcai:bridge` 只传元数据，字节走 HTTP：

```
mcai-client 1.4.0（Forge 1.20.1 / NeoForge 1.21.1 / 26.x / Fabric 26.x）
  1) 登录：minecraft:register（声明监听 mcai:bridge）+ HELLO
  2) 收 INFO（端口 / advertise-host / 一次性 token / 上限）
  3) 收 REQUEST（截图或文件 + transferId）
  4) HTTP POST 原始字节到插件端口（8/16MB 走 TCP，不经过游戏线程）
        ↓
mc-ai 1.4.0 Bukkit 插件
  - 校验 token（一次性、绑定玩家 + transferId、TTL 120 秒）/ 大小 / 类型
  - 文件名只取 basename；绝不按玩家给的路径落盘
  - 读完字节切回服务器主线程，复用 AiClient → POST 127.0.0.1:8765/mc-ai（附件 base64）
```

- 桥接只在收到客户端 HELLO 后生效；客户端 2 秒内没收到 INFO 就按「服务端没有桥接」处理。
- 15 秒没收到字节、HTTP 失败、ABORT、token 过期、超限 → 该次附件作废，提示原因后按只发文字处理。
- 插件端口绑不上 / `attachment.enabled=false`：只禁用桥接，文字 `/ai`、`/weather`、`/vote` 正常。
- 模组服（装了 `mcai` 服务端模组）不会出现桥接 INFO，客户端继续走原来的模组通道。

协议细节（帧格式、HTTP 头、返回码）见 [PROTOCOL.md](PROTOCOL.md) 的「Bukkit 附件桥接」章节。

### `plugins/mc-ai/config.yml`

首次启动自动生成；只放桥接参数（`/ai` 的地址和天气投票仍在服务器根目录的
`config/mcai-common.json`、`config/mcai-weather.json`）：

```yaml
attachment:
  enabled: true          # 关掉后 /ai screenshot、/ai file 降级为只发文字
  transport: http        # 目前只支持 http
  port: 25566            # 上传端口（也是 INFO 下发给客户端的端口）
  bind: 0.0.0.0          # 监听地址；只给本机用可以填 127.0.0.1
  advertise-host: ""     # 空 = 客户端用自己连接服务器的地址；NAT/反代时填公网域名/IP
  advertise-port: 0      # 0 = 用 port；公网映射端口不同则填公网端口
  token-ttl-seconds: 120 # 一次性 token 有效期
  upload-timeout-seconds: 30
  max-image-mb: 8
  max-file-mb: 16
  max-concurrent: 2
```

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | `true` | 关掉只影响附件，不影响文字 `/ai`、天气 |
| `transport` | `http` | 只支持 `http` |
| `port` | `25566` | 上传端口；一台机器多个服要各给一个 |
| `bind` | `0.0.0.0` | 监听网卡；`0.0.0.0` 才能让外部玩家上传 |
| `advertise-host` | 空 | 下发给客户端的地址；空 = 客户端用连接地址。**NAT/反向代理后面必须填公网域名或 IP** |
| `advertise-port` | `0` | 下发给客户端的端口；`0` = 用 `port` |
| `token-ttl-seconds` | `120` | 一次性 token 有效期（10–3600） |
| `upload-timeout-seconds` | `30` | 单次上传读取超时（5–300） |
| `max-image-mb` | `8` | 截图上限（1–64MB） |
| `max-file-mb` | `16` | 普通文件上限（1–64MB） |
| `max-concurrent` | `2` | 同时上传数（1–16） |

### 部署注意

- 玩家的客户端要能连到 `advertise-host:port`。云服务器安全组 / Windows 防火墙要放行该 TCP 端口。
- 服务器在 NAT 或反向代理后面时，`advertise-host` / `advertise-port` 要填**公网可达**的地址和端口，
  否则客户端会把字节 POST 到错误的地方（插件日志会打出 `收到附件` 或没有）。
- 这是**明文 HTTP**：token 单次有效、只够传一个附件，但链路本身不加密。跨公网对安全敏感的话，
  建议只在信任的网络里用，或用防火墙限制来源 IP。
- 插件不会把玩家给的路径落盘：文件名只取 basename，字节先放内存再交给 AiClient。

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

1. 服务端 `mods/` 放入对应平台的服务端 jar（`mc-ai-<loader>-<mc>-v1_4_0.jar`）。
2. 客户端（可选）`mods/` 放入同一平台的客户端 jar
   （`mc-ai-client-<loader>-<mc>-v1_4_0.jar`）；不装也能进服，只是附件命令降级。
3. 服务端装好 DSH 与 `dsh-mc-server-ops`（≥ 0.6.3），保持 `/mc-ai` 监听。
4. 重启服务器；首次启动生成 `/ai` 配置（Forge 1.20.1 是 `config/mcai-common.toml`，
   其它平台是 `config/mcai-common.json`）。
5. 游戏内 `/ai 你好`，或 `/weather rain` 试试天气。

### Bukkit / Spigot / Paper

1. 把 `mc-ai-bukkit-v1_4_0.jar` 放进服务端 `plugins/`（一个 jar 同时支持 1.20+、1.21+、26.x）。
2. 重启服务器；插件会生成：
   - `plugins/mc-ai/config.yml` —— 附件桥接（端口等）
   - `config/mcai-common.json` 与 `config/mcai-weather.json`（都在**服务器根目录**的 `config/` 下）
3. 需要附件功能的话，按上面的「Bukkit 附件桥接」配好 `plugins/mc-ai/config.yml`，
   并让玩家装同版本的 `mcai-client` 1.4.0（Forge 1.20.1 / NeoForge 1.21.1 / 26.x / Fabric 26.x 对应版本）。
4. 服务端装好 DSH 与 `dsh-mc-server-ops`（同上）。
5. 游戏内 `/ai 你好`、`/weather rain`、`/vote status`；装了客户端的玩家还能 `/ai screenshot`、`/ai file`。

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
- Bukkit 服务器没有模组通道，客户端改用 `mcai:bridge`（raw plugin message，只传元数据）+
  HTTP 上传端口传字节；插件再把字节交给同一个 `AiClient`。
- 图片附件由 DSH 交给模型直接看图；文本类小文件内联进消息，其它文件落盘后把只读路径
  告诉 AI（由 DSH 负责）。
- 天气投票完全在服务端生效，不经过 DSH、不消耗 token；只有配置从 DSH 插件写文件过来。

## 从源码构建

仓库结构：

```
common/                 与加载器无关的核心（Java 8 语法，零依赖）
common/src/bridge/      插件与所有客户端共用的 mcai:bridge 协议层（帧/ token / 上传）
src/ + client/          Forge 1.20.1 服务端 + 客户端（ForgeGradle 6）
platforms/bukkit/       Bukkit 通用插件（Spigot API 1.20.1，Java 17）
platforms/forge-1.12.2/ Forge 1.12.2 服务端 + 客户端（ForgeGradle 2.3，Java 8）
platforms/neoforge/     NeoForge 1.21.1 服务端 + 客户端（ModDevGradle）
platforms/neoforge-26.x/ NeoForge 26.1/26.2/26.3 服务端 + 客户端（ModDevGradle）
platforms/fabric/       Fabric 26.x 服务端 + 客户端（Loom 1.19，Java 25）
tests/                  WeatherVoteService / BridgeSelfTest 纯逻辑自测
.github/workflows/      分平台 CI（产物名即发布名）
```

- **CI 构建（推荐）**：每个平台一个 workflow，push 对应路径或手动 `workflow_dispatch`；
  产物在 Actions 的 artifact 里，文件名已经是发布名。
- **本机 Forge 1.20.1**：JDK 17 + Gradle 8.1.1，`./gradlew build`（服务端）和
  `cd client && ./gradlew build`（客户端）。
- **纯逻辑自测**（不依赖 Minecraft / Bukkit）：
  ```bash
  # 天气投票
  javac -encoding UTF-8 -d out common/src/main/java/com/mogutofu/mcai/core/*.java tests/WeatherVoteSelfTest.java
  java -cp out WeatherVoteSelfTest
  # 附件桥接协议（帧编解码 / token / 上传辅助，55 项）
  javac -encoding UTF-8 --release 8 -d out2 common/src/bridge/java/com/mogutofu/mcai/bridge/*.java tests/BridgeSelfTest.java
  java -cp out2 BridgeSelfTest
  ```
- NeoForge / Fabric 的 Gradle 依赖在部分网络环境不可直连，仓库的 CI 在 GitHub Actions
  上跑；本机能否构建取决于 `maven.neoforged.net` / `maven.fabricmc.net` 的连通性。

> Forge 1.20.1 在 DSH 的 Windows 沙箱里构建会遇到 ForgeGradle AT 阶段
> `ReadOnlyFileSystemException`（JDK zipfs 在受限令牌下把文件系统当只读），
> 本机用「给 `jdk.zipfs` 打 `--patch-module` 补丁」绕过；普通开发机/CI 不需要。

## 测试状态（1.4.0）

- **Bukkit 附件桥接（Paper 1.20.1 实机）**：用 mineflayer 1.20.1 模拟 1.4.0 客户端，33 项断言全过：
  `minecraft:register` + HELLO → 收到 INFO（端口 25566 / 空 advertise-host / 8MB+16MB 上限 / token）；
  `/ai screenshot` → REQUEST(kind=0, path 空) → HTTP 200 → DSH mock 收到 `image/png` 的
  `screenshot.png`（字节数、SHA256 一致）；`/ai file` → REQUEST(kind=1, 带本地路径) → DSH 收到
  `sample.txt`（字节一致）；错误 token → 403 且 ABORT 后降级纯文字；9MB 截图 → 413；
  15 秒不上传 → 超时提示 + 纯文字；纯文字 `/ai`、`/weather` 用法与生效正常；
  没发 HELLO 的旧客户端 → 提示「客户端未安装 mcai-client 1.4.0」并降级纯文字，且收不到 REQUEST。
- **附件桥接纯逻辑**：`BridgeSelfTest` 55 项（5 种帧往返、magic/版本/截断/超长拒绝、
  token 一次性/过期/绑定玩家/绑定 transfer/撤销/purge、上传 URL/IPv6/文件名清洗），全过。
- **Forge 1.20.1（生产服实机）**：1.4.0 服务端 jar 部署到生产服，重启后 Forge 日志显示
  `mcai (version 1.3.0 -> 1.4.0)`、`mcai:net` 通道注册、`/ai`、`/weather`、`/vote` 注册；
  控制台 `/ai` 返回用法、`/weather` 返回用法与当前配置、`/weather clear 120` 生效、
  `/vote status` 返回「现在没有正在进行的天气投票」。服务端网络代码与 1.3.0 逐字节等价（只改版本号）。
- **CI 全绿**：`build-forge-1.20.1`、`build-forge-1122`、`build-neoforge-1.21.1`、`build-neoforge-26`
  （26.1/26.2/26.3 矩阵）、`build-fabric`（26.1/26.2/26.3 矩阵）、`build-bukkit` 全部 success；
  19 个 jar 的 `mods.toml` / `mcmod.info` / `fabric.mod.json` / `plugin.yml` 版本均为 1.4.0，
  服务端 jar 不含 `com/mogutofu/mcai/bridge`（避免 split package），客户端 jar 只带 bridge 不带 core。
- **未实机验证**（明确标注，不虚报）：
  - 1.4.0 客户端的**真实 Minecraft 客户端**无法在本机启动（无图形环境），桥接客户端代码只到
    **编译级**；NeoForge 26.x / Fabric 26.x 与 1.21.1 客户端同理。
  - 1.3.0 客户端连 1.4.0 模组服：模组通道代码未改（只改版本字符串），但**没有实机**跑过；
    协议层面兼容，见 PROTOCOL 兼容表。
  - Paper 1.21+/26.x 服务器上的桥接：本机只起了 Paper 1.20.1 做集成测试；1.21+ 的通道声明
    兼容路径按 Paper 源码实现（`ServerCommonPacketListenerImpl` 仍处理 `minecraft:register`），
    但**未在 1.21+ 服务器实机**验证。
  - Forge 1.12.2：CI 里用官方 installer 起专用服务器确认 mod 加载、`mcai:net` 注册、
    `/weather` 替换原版、命令注册、服务器 `Done`；未做玩家交互。

## 已知限制

- **Bukkit 桥接是明文 HTTP，且要求玩家能直连插件端口**。NAT/反向代理要配 `advertise-host`/`advertise-port`，
  防火墙要放行 TCP 端口；不配的话客户端上传会失败并降级为纯文字。
- Bukkit 版 1.3.0 客户端、或没装客户端的玩家：`/ai screenshot`、`/ai file` 仍然降级为只发文字。
- 一次只处理一个附件；`/ai` 与 `/weather`、`/vote` 的行为与模组版一致。
- Forge 1.12.2 编译基座是 14.23.5.2847（官方 maven 上 2854+ 没有 `-userdev` 制品）；产物运行在 1.12.2 全系（含 2860）。旧 API（SimpleImpl、旧命令注册、Java 8）；客户端截图走 GL 读像素 +
  PNG 编码，与新版实现不同但协议一致。1.12.2 客户端**不支持** Bukkit 桥接（Bukkit 插件最低 1.20）。
- NeoForge 26.1 上游是 `26.1.0.19-beta`；26.x 使用 Java 25，MC 26.x 已去混淆。
- Forge 1.20.1 客户端的 `mcai` 服务端依赖已改为**可选**（1.4.0），否则连只装 Bukkit 插件的服务器
  会被判定「缺少服务端模组」；模组服上缺少 `mcai` 时附件请求不会发生，功能自然降级。
- 附件只支持专用服务器；单机/内置服务器按「没有客户端模组」降级。
- 天气投票提示统一 `[天气] ` 前缀；`/ai` 回复统一 `[小铜] ` 前缀。Bukkit 版通过
  `PlayerCommandPreprocessEvent` 接管 `/weather`，如果别的插件也取消该事件，以其它插件为准。
- 只连本机 DSH（`127.0.0.1:8765`）；不要把这个端口暴露到公网。插件上传端口也不要暴露到
  不需要的地方（token 单次有效，但仍是明文）。

## FAQ

**客户端没装会怎样？** 服务端照常工作，`/ai`、`/ai message`、`/weather`、`/vote` 正常；
`/ai screenshot`、`/ai file` 降级为只发文字并提示。

**客户端装了、服务器没装服务端模组？** 1.4.0 起可以：客户端会走 `mcai:bridge` 桥接，
但需要服务端是 1.4.0 的 Bukkit 插件并开好上传端口。模组服没装 `mcai` 时不会收到附件请求。

**Bukkit 服上截图/文件一直降级？** 依次检查：玩家是否装了 1.4.0 客户端；`plugins/mc-ai/config.yml`
的 `attachment.enabled` 是否为 true、`port` 是否监听成功（启动日志有「附件上传端口已监听」）；
`advertise-host` 是否是玩家能连到的地址；防火墙/安全组是否放行；`/ai screenshot` 后看服务器日志
有没有「收到附件」。

**截图很大怎么办？** 4K PNG 可能超过 8MB，客户端会放弃发送并提示；降低分辨率/窗口重试，
或在 `plugins/mc-ai/config.yml` 调大 `max-image-mb`（模组版是 `PROTOCOL` 里的固定上限）。

**文件会存到哪里？** 玩家本地文件只在客户端读取并发送字节；服务端只转发给本机 DSH。
Bukkit 桥接的文件名只取 basename，不按玩家给的路径落盘。

**天气配置在哪改？** DSH 插件设置面板 →「游戏内天气投票」；或直接改服务端
`config/mcai-weather.json`（每次 `/weather` 重读）。Bukkit 版路径相同。

**为什么 NeoForge 26.x / Fabric 没有实机测试？** 本机 `maven.neoforged.net` 被网络阻断，
NeoForge 26.x / Fabric 的运行时依赖拉不下来；CI runner 只做构建，没有为这些版本起完整测试服。
NeoForge 1.21.1 已经在 CI 里起过 `runServer` 并用 RCON 验证了命令。因此 26.x / Fabric 标注
「编译通过、未实机验证」。

## License

MIT
