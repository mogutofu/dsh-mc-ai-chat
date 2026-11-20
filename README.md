# mcai 1.4.0

在服务端加一条 `/ai` 指令，玩家在游戏里就能和 DeepSeek Harness 上的「小铜」聊天。
mod / 插件本身不跑模型：它把玩家名、UUID、消息 POST 到本机 `dsh-mc-server-ops` 插件
监听的 `http://127.0.0.1:8765/mc-ai`，再把回复贴回聊天栏。

从 1.3.0 开始，Forge / NeoForge / Fabric / Bukkit 的行为是同一套。1.1.0 的截图/文件附件和
1.2.0 的天气投票也都还在。

1.4.0 主要补了一个坑：以前只在模组服上能发截图和文件，Bukkit 插件服不行。现在只要玩家装了
1.4.0 的客户端、服务端把插件上传端口配好，Bukkit / Spigot / Paper 服务器也能用
`/ai screenshot` 和 `/ai file`。字节走 HTTP，不塞进 Minecraft 那个 32KB 的自定义包里；
模组服原来的通道一行没动，行为跟 1.3.0 一样。

服务端还需要装 [dsh-mc-server-ops](https://github.com/mogutofu/dsh-mc-server-ops)（≥ 0.6.3）
并保持 `/mc-ai` 在监听。没有它 mod 也能加载，但 `/ai` 没人回，天气配置也不会有人写。

## 都支持哪些版本

| 平台 | MC | 服务端 jar | 客户端 jar（可选） |
| --- | --- | --- | --- |
| Forge | 1.12.2 | `mc-ai-forge-1_12_2-v1_4_0.jar` | `mc-ai-client-forge-1_12_2-v1_4_0.jar` |
| Forge | 1.20.1 | `mc-ai-forge-1_20_1-v1_4_0.jar` | `mc-ai-client-forge-1_20_1-v1_4_0.jar` |
| NeoForge | 1.21.1 | `mc-ai-neoforge-1_21_1-v1_4_0.jar` | `mc-ai-client-neoforge-1_21_1-v1_4_0.jar` |
| NeoForge | 26.1 / 26.2 / 26.3 | `mc-ai-neoforge-26_{1,2,3}-v1_4_0.jar` | `mc-ai-client-neoforge-26_{1,2,3}-v1_4_0.jar` |
| Fabric | 26.1 / 26.2 / 26.3 | `mc-ai-fabric-26_{1,2,3}-v1_4_0.jar` | `mc-ai-client-fabric-26_{1,2,3}-v1_4_0.jar` |
| Bukkit / Spigot / Paper | 1.20+ / 1.21+ / 26.x | `mc-ai-bukkit-v1_4_0.jar`（一个 jar 通用） | 用上面同版本的客户端 jar |

几点说明：

- 客户端是可选的。不装也能进服，只是 `/ai screenshot`、`/ai file` 会退化成只发文字。
- Forge 1.12.2 的客户端不支持 Bukkit 桥接（Bukkit 插件最低支持 1.20），服务端模组功能正常。
- 服务端 modId 是 `mcai`，客户端是 `mcai_client`，Bukkit 插件名是 `mc-ai`。
- 所有平台版本号统一 1.4.0，jar 名里的 `v1_4_0` 就是版本号（点换成下划线）。
- Fabric 的 API 版本按 `+26.x` 在 CI 里自动取最新稳定版，装的时候用对应 MC 版本的最新 Fabric API 就行。
- NeoForge 26.1 上游是 `26.1.0.19-beta`；26.x 跑在 Java 25 上。

## 命令

| 命令 | 说明 |
| --- | --- |
| `/ai <内容>` | 老写法，等于 `/ai message <内容>` |
| `/ai message <内容>` | 只发文字 |
| `/ai screenshot [内容]` | 截当前画面，连同文字一起发（需要 1.4.0 客户端） |
| `/ai file <路径> <消息>` | 读玩家本机文件发过去（需要 1.4.0 客户端） |
| `/weather clear\|rain\|thunder [持续秒数]` | 改天气，所有玩家都能用 |
| `/vote yes` / `/vote no` / `/vote status` | 天气投票 |

- `/ai` 有每人限流，默认 5 秒一次；回复都是 `[小铜] ` 开头。
- 没装客户端（或装的是 1.3.0）时，截图和文件命令会提示「客户端未安装 mcai-client 1.4.0」
  然后只发文字，不会报错。
- `/ai file` 的路径是玩家自己电脑上的路径。Windows 路径不用转义反斜杠，有空格就用引号包起来。
- 附件只支持专用服务器。单人/内置服务器按「没有客户端模组」处理。
- 截图上限 8MB、文件上限 16MB，一次只能带一个附件。模组通道是 24KB 一块分着发的；
  Bukkit 桥接走 HTTP，上限可以在插件配置里改。

## Bukkit 服务器上的附件（1.4.0 新的）

只装插件、没有服务端模组的服务器上没有 `mcai:net` 这条通道，所以 1.4.0 单独加了一条
raw 插件通道 `mcai:bridge`，只用来传元数据，真正的字节走插件自己起的 HTTP 端口：

```
客户端 1.4.0
  登录 -> minecraft:register（声明监听 mcai:bridge）+ HELLO
  收到 INFO：端口、advertise-host、一次性 token、大小上限
  收到 REQUEST：截图还是文件、transferId
  HTTP POST 原始字节到插件端口
        |
插件
  校验 token（一次性、绑定玩家和 transferId、默认 120 秒过期）、类型、大小
  只取文件名的 basename，不按玩家给的路径落盘
  读完字节切回主线程，还是走原来那套 AiClient，POST 到 127.0.0.1:8765/mc-ai
```

这里踩过一个坑：Paper 的 `sendPluginMessage` 只发给客户端声明过的通道，客户端不先发
`minecraft:register` 的话，插件发的 INFO 和 REQUEST 会被直接丢掉，而且什么日志都没有。
所以客户端登录时会补一条 register。

几种失败情况的处理：

- 客户端 2 秒内没收到 INFO，就当这个服没有桥接，继续走原来的逻辑。
- 15 秒没收到字节、HTTP 报错、客户端发 ABORT、token 过期、超限：这次附件作废，提示原因，
  然后按只发文字处理。
- 插件端口绑不上，或者配置里关了 `enabled`：只禁用桥接，文字 `/ai`、`/weather`、`/vote`
  照常工作，启动日志里会写清楚原因。
- 模组服上不会出现桥接的 INFO，客户端还是走模组通道。

协议细节（帧格式、HTTP 头、错误码）写在 [PROTOCOL.md](PROTOCOL.md) 里。

### `plugins/mc-ai/config.yml`

第一次启动会自动生成。这里只放桥接相关的配置；`/ai` 的地址和天气投票还在服务器根目录的
`config/mcai-common.json`、`config/mcai-weather.json`，没有搬家。

```yaml
attachment:
  enabled: true          # 关掉后 /ai screenshot、/ai file 只发文字
  transport: http        # 目前只支持 http
  port: 25566            # 上传端口，也是下发给客户端的端口
  bind: 0.0.0.0          # 监听地址；只想本机用就填 127.0.0.1
  advertise-host: ""     # 空 = 用客户端自己连的服务器地址；NAT/反代要填公网域名或 IP
  advertise-port: 0      # 0 = 用上面的 port
  token-ttl-seconds: 120
  upload-timeout-seconds: 30
  max-image-mb: 8
  max-file-mb: 16
  max-concurrent: 2
```

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `enabled` | true | 关掉只影响附件，不影响文字和天气 |
| `transport` | http | 只有 http |
| `port` | 25566 | 一台机器开多个服的话每个服给不同的端口 |
| `bind` | 0.0.0.0 | 要让外面的玩家传，就得监听 `0.0.0.0` |
| `advertise-host` | 空 | 空表示用客户端连接地址；服务器在 NAT/代理后面时**必须填公网可达的地址** |
| `advertise-port` | 0 | 0 表示用 `port`；公网映射端口不同时填公网端口 |
| `token-ttl-seconds` | 120 | 10 到 3600 |
| `upload-timeout-seconds` | 30 | 5 到 300 |
| `max-image-mb` / `max-file-mb` | 8 / 16 | 1 到 64 |
| `max-concurrent` | 2 | 1 到 16 |

部署的时候注意这几件事：

- 玩家的客户端得能直接连到 `advertise-host:port`。云服务器安全组和 Windows 防火墙都要放行这个 TCP 端口。
- NAT 或反向代理后面不配 `advertise-host` / `advertise-port`，客户端会把字节 POST 到错的地方，
  表现就是一直退化成文字。服务器日志里有没有「收到附件」是最好判断的。
- 这是明文 HTTP。token 一次性、只够传一个附件，但链路本身不加密，跨公网用要自己权衡，
  或者用防火墙限制来源 IP。
- 文件名只取 basename，字节先放内存再交给 AiClient，不会按玩家给的路径写盘。

## 天气投票

各平台行为一样：

- 在线人数 ≤ `directThreshold`（默认 2）时直接生效，全服提示谁改了天气。
- 3 人及以上发起投票。发起人自动算 1 票，其他人 `/vote yes` / `/vote no`，
  `/vote status` 看进度。
- 同意到 `approveNeed`（默认 2）通过，反对到 `rejectNeed`（默认 1）作废，
  `timeoutSeconds`（默认 60 秒）没结果也作废。
- 同一个人两次发起之间要隔 `cooldownSeconds`（默认 60 秒）。
- 控制台、RCON、OP 执行 `/weather` 不受投票限制，直接生效。
- `/weather` 不带参数会打出用法和当前配置。

配置在服务端的 `config/mcai-weather.json`，每次 `/weather` 都重新读，改完不用重启：

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

这个文件一般是 DSH 插件的设置面板 →「游戏内天气投票」写进去的。手动改也行，
但插件下次保存会覆盖。文件没了或者写坏了就按默认值来。

## 安装

### Forge / NeoForge / Fabric

1. 服务端 `mods/` 放对应平台的服务端 jar。
2. 客户端（可选）`mods/` 放同平台的客户端 jar，不装也能进。
3. 服务端装好 DSH 和 `dsh-mc-server-ops`（≥ 0.6.3），保持 `/mc-ai` 监听。
4. 重启。第一次启动会生成 `/ai` 的配置：Forge 1.20.1 是 `config/mcai-common.toml`，
   其它平台是 `config/mcai-common.json`。
5. 进游戏 `/ai 你好`，或者 `/weather rain` 试试。

### Bukkit / Spigot / Paper

1. `mc-ai-bukkit-v1_4_0.jar` 丢进 `plugins/`。
2. 重启。插件会生成 `plugins/mc-ai/config.yml`（桥接配置），以及服务器根目录的
   `config/mcai-common.json`、`config/mcai-weather.json`。注意后两个在根目录，不在 `plugins/`。
3. 要附件功能就按上面那节配好 `config.yml`，玩家装上同版本的 1.4.0 客户端。
4. 服务端装 DSH 和 `dsh-mc-server-ops`。
5. `/ai 你好`、`/weather rain`、`/vote status` 都能用；装了客户端的玩家还能截图发文件。

Bukkit 版的 `/weather` 是插件拦截玩家命令实现的：玩家打 `/weather` 时插件接管，
控制台和 RCON 还是走原版（本来就有权限）。OP 玩家由插件直接执行。

## `/ai` 的配置

Forge 1.20.1 用 `config/mcai-common.toml`：

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `endpoint` | `http://127.0.0.1:8765/mc-ai` | DSH 插件的地址 |
| `timeoutSeconds` | 60 | 单次请求超时；带附件的至少 90 秒 |
| `enabled` | true | 关掉后 `/ai` 只提示「AI 未开启」 |
| `rateLimitSeconds` | 5 | 同一玩家两次请求的最小间隔，0 表示不限 |

其它平台（包括 Bukkit）是 `config/mcai-common.json`，字段一样：

```json
{
  "endpoint": "http://127.0.0.1:8765/mc-ai",
  "timeoutSeconds": 60,
  "enabled": true,
  "rateLimitSeconds": 5
}
```

## 它是怎么工作的

- 服务端把玩家名、UUID、文字和可选附件（base64）POST 给本机 DSH。HTTP 约定看
  [PROTOCOL.md](PROTOCOL.md)。
- 客户端和服务端之间：Forge 1.20.1/1.12.2 走一条 `mcai:net`，NeoForge 和 Fabric 走各自的
  自定义 payload，附件 24KB 分块。
- Bukkit 服务器没有模组通道，客户端用 `mcai:bridge` 传元数据、HTTP 传字节，
  插件拿到字节后交给同一个 `AiClient`。
- 图片由 DSH 交给模型直接看图；文本类小文件内联进消息，其它文件落盘后把只读路径告诉 AI。
  这部分在 DSH 那边做，mod 不管。
- 天气投票完全在服务端跑，不经过 DSH，也不花 token。DSH 只是把配置写进文件。

## 从源码构建

```
common/                  与加载器无关的核心（Java 8，零依赖）
common/src/bridge/       插件和所有客户端共用的 mcai:bridge 协议层
src/ + client/           Forge 1.20.1 服务端 + 客户端（ForgeGradle 6）
platforms/bukkit/        Bukkit 插件（Spigot API 1.20.1，Java 17）
platforms/forge-1.12.2/  Forge 1.12.2 服务端 + 客户端（ForgeGradle 2.3，Java 8）
platforms/neoforge/      NeoForge 1.21.1 服务端 + 客户端（ModDevGradle）
platforms/neoforge-26.x/ NeoForge 26.x 服务端 + 客户端（ModDevGradle）
platforms/fabric/        Fabric 26.x 服务端 + 客户端（Loom 1.19，Java 25）
tests/                   天气投票、桥接协议的纯逻辑自测
.github/workflows/       各平台的 CI
```

平时都是让 CI 构建：push 对应路径，或者手动 `workflow_dispatch`，artifact 里的文件名
就是发布用的名字。本机只有 Forge 1.20.1 比较方便：JDK 17 + Gradle 8.1.1，
`./gradlew build` 是服务端，`cd client && ./gradlew build` 是客户端。
NeoForge / Fabric 要看 `maven.neoforged.net`、`maven.fabricmc.net` 能不能连上，我这边连不上，
所以它们只在 GitHub Actions 上构建。

顺便把两个纯逻辑自测也记在这，不依赖 Minecraft 和 Bukkit：

```bash
# 天气投票
javac -encoding UTF-8 -d out common/src/main/java/com/mogutofu/mcai/core/*.java tests/WeatherVoteSelfTest.java
java -cp out WeatherVoteSelfTest

# 桥接协议：帧编解码、token、上传辅助
javac -encoding UTF-8 --release 8 -d out2 common/src/bridge/java/com/mogutofu/mcai/bridge/*.java tests/BridgeSelfTest.java
java -cp out2 BridgeSelfTest
```

还有个环境上的小问题：Forge 1.20.1 在 DSH 的 Windows 沙箱里构建会卡在 ForgeGradle 的 AT 阶段，
报 `ReadOnlyFileSystemException`（受限令牌下 JDK zipfs 把文件系统当只读了）。本机的绕法是给
`jdk.zipfs` 打个 `--patch-module` 补丁，普通开发机和 CI 不需要。

## 测到什么程度了

- **Bukkit（Paper 1.20.1）**：用 mineflayer 模拟 1.4.0 客户端，33 项断言全过。包括
  HELLO/INFO、`/ai screenshot` 端到端（DSH mock 收到的 PNG 字节数和 SHA256 都对得上）、
  `/ai file`、错误 token 返回 403、9MB 超限返回 413、15 秒超时降级、纯文字 `/ai`、
  `/weather` 生效，以及没发 HELLO 的旧客户端降级且收不到 REQUEST。另外单独测了端口被占用的情况：
  插件只禁用桥接，文字 `/ai` 和 `/weather` 正常。这些是用发布出去的 jar 本身跑的。
- **桥接协议**：`BridgeSelfTest` 55 项，帧往返、坏帧拒绝、token 的一次性/过期/绑定/撤销都覆盖了。
- **Forge 1.20.1（生产服）**：1.4.0 已经部署，日志里有 `mcai (version 1.3.0 -> 1.4.0)`，
  `/ai`、`/weather`、`/weather clear 120`、`/vote status` 都验过。生产服上还有用 1.3.0
  客户端的玩家连进来，握手正常（协议 1）。
- **CI**：六个 workflow 全绿，19 个 jar 的元数据都是 1.4.0，服务端 jar 里没有 bridge 类
  （避免 split package），客户端 jar 只带 bridge 不带 core。

没测到的部分也说一下：

- 真正的 Minecraft 客户端我这边起不来（没有图形环境），所以 1.4.0 的客户端代码只做到编译级，
  NeoForge / Fabric 客户端也一样。能不能真的收到 INFO、传上文件，得玩家实机试。
- Paper 1.21+ / 26.x 上的桥接没实机跑过，只起了 1.20.1。1.21+ 的通道声明兼容是按 Paper
  源码里还在处理 `minecraft:register` 来实现并确认的。
- 1.3.0 客户端连 1.4.0 模组服的附件传输没实机跑过。模组通道代码只改了版本号，协议没动。

## 已知的限制

- Bukkit 桥接是明文 HTTP，而且要玩家能直连插件端口。NAT / 反向代理要配 `advertise-host`、
  `advertise-port`，防火墙要放行。不配就是一直退化成文字。
- Bukkit 服上如果玩家用 1.3.0 客户端或者没装客户端，截图和文件还是只发文字。
- 一次只能带一个附件。
- Forge 1.12.2 的编译基座是 14.23.5.2847（官方 maven 上 2854 之后的版本没有 `-userdev` 制品），
  产物在 1.12.2 全系都能跑。它用的是旧 API，客户端截图是自己读像素再编码 PNG 的，
  跟新版实现不一样，但协议一致。
- 1.12.2 客户端不支持 Bukkit 桥接。
- Forge 1.20.1 客户端对服务端 `mcai` 的依赖在 1.4.0 改成了可选。以前是必装，装了这个客户端
  再去连只装插件的服务器会被判「缺少服务端模组」。
- 天气投票的提示统一是 `[天气] ` 开头，`/ai` 回复是 `[小铜] ` 开头。Bukkit 版靠
  `PlayerCommandPreprocessEvent` 接管 `/weather`，如果别的插件也把事件取消了，以别的插件为准。
- 只连本机的 DSH（`127.0.0.1:8765`）。这个端口不要往外开。插件上传端口也尽量只开给需要的人
  （token 是一次性的，但传输本身不加密）。

## 几个常见的问法

**客户端没装会怎么样？** 服务端照常跑，`/ai`、`/weather`、`/vote` 都能用。
`/ai screenshot` 和 `/ai file` 会提示一句，然后只发文字。

**客户端装了，但服务器没装服务端模组？** 1.4.0 起可以走 Bukkit 桥接，前提是服务端是 1.4.0
的 Bukkit 插件、上传端口配好且能连到。反过来，模组服上没装 `mcai` 的话，客户端不会收到附件请求。

**Bukkit 服上截图一直变成文字？** 按这个顺序看：玩家客户端是不是 1.4.0；`config.yml` 里
`enabled` 是不是 true；启动日志有没有「附件上传端口已监听」；`advertise-host` 玩家能不能连到；
防火墙和安全组放行没有；`/ai screenshot` 之后服务端日志里有没有「收到附件」。

**截图太大发不出去？** 4K 截图有可能超过 8MB，客户端会放弃并提示。降分辨率或者窗口重试，
Bukkit 服可以把 `max-image-mb` 调大；模组服的 8/16MB 是写在协议里的。

**文件会存到服务器上吗？** 不会。客户端只把字节发过来，服务端直接转给本机 DSH。
Bukkit 桥接里文件名也只取 basename。

**天气的配置在哪改？** DSH 插件的设置面板 →「游戏内天气投票」，或者直接改服务端的
`config/mcai-weather.json`。改完不用重启，下次 `/weather` 就生效。Bukkit 版路径一样。

**为什么 NeoForge 26.x / Fabric 没做实机测试？** 本机连不上 `maven.neoforged.net`，
运行时依赖拉不下来，CI runner 也只做构建。NeoForge 1.21.1 之前起过 `runServer` 用 RCON
验过命令，1.4.0 这次只做了构建。所以这两个平台标的是「编译通过、未实机」。

## License

MIT
