# Minecraft AI Chat (mcai)

在 Minecraft 服务端里加一个 `/ai` 指令：玩家直接和 DeepSeek Harness 上的「小铜」对话。mod 本身不含 AI，只把请求 POST 到本机 DSH 插件 `dsh-mc-server-ops` 提供的 `http://127.0.0.1:8765/mc-ai`，再把回复贴回聊天栏。

从 1.1.0 起拆成**两个模组**：

| 模组 | modid | 装在哪里 | 说明 |
| --- | --- | --- | --- |
| `mcai-server`（本仓库根工程，产物 `mcai-1.1.1.jar`） | `mcai` | **服务端必装** | `/ai` 指令、与 DSH 通信；不装客户端模组时只发文字 |
| `mcai-client`（本仓库 `client/` 工程，产物 `mcai-client-1.1.1.jar`） | `mcai_client` | **客户端选装** | 让 `/ai screenshot`、`/ai file` 能截图/读本地文件发到服务端 |

> 服务端没装 `dsh-mc-server-ops` 插件的话，mod 能加载但 `/ai` 没有 AI 可聊。插件：<https://github.com/mogutofu/dsh-mc-server-ops>

## 命令

| 命令 | 需要客户端模组 | 说明 |
| --- | --- | --- |
| `/ai <内容>` | 否 | 兼容旧写法，等于 `/ai message <内容>` |
| `/ai message <内容>` | 否 | 只发文字给 AI |
| `/ai screenshot [内容]` | 是 | 截取当前游戏画面，和文字一起发给 AI |
| `/ai file <路径> <内容>` | 是 | 读取玩家本机文件，和文字一起发给 AI |

说明：

- 客户端没装 `mcai-client` 时，`/ai screenshot`、`/ai file` 会**降级为只发文字**（聊天栏会提示「客户端未安装 mcai-client」）。
- 附件功能只支持**专用服务器**（dedicated server）；单机/内置服务器会按「没有客户端模组」降级。
- `/ai file` 的路径是**玩家自己电脑上的路径**，由客户端读取后只把文件字节发给服务器。
- 路径写法（1.1.1 起不用再转义反斜杠）：`/ai file "C:\Users\me\笔记.md" 这个文件是什么`；路径有空格时用引号包起来，没有空格也可以不加引号（`/ai file C:/tmp/a.md 看看这个`）。
- 服务端侧有每玩家限流（`rateLimitSeconds`，默认 5 秒）。

## 安装

1. 服务端 `mods/` 放 `mcai-1.1.1.jar`；可选：客户端 `mods/` 放 `mcai-client-1.1.1.jar`。
2. 服务端装好 DSH 与 `dsh-mc-server-ops` 插件（≥ 0.6.0 才支持附件），并保持 `/mc-ai` 监听（默认 `127.0.0.1:8765`）。
3. 重启服务器；首次启动生成 `config/mcai-common.toml`。
4. 游戏内 `/ai 你好`。

## 配置（`config/mcai-common.toml`）

| 配置项 | 默认 | 说明 |
| --- | --- | --- |
| `endpoint` | `http://127.0.0.1:8765/mc-ai` | DSH 插件地址 |
| `timeoutSeconds` | `60` | 单次请求超时；带附件请求至少 90 秒 |
| `enabled` | `true` | 关闭后 `/ai` 只提示「AI 未开启」 |
| `rateLimitSeconds` | `5` | 同一玩家两次请求最小间隔，`0` 不限 |

## 工作方式

- 服务端模组把玩家的 `玩家名`、`UUID`、`文字`、以及可选附件（base64）POST 到本机 DSH。
- 附件协议见 [PROTOCOL.md](PROTOCOL.md)：每次最多 1 个附件；截图 PNG ≤ 8MB，文件 ≤ 16MB。
- 客户端 ↔ 服务端走 Forge `mcai:net` 通道（协议版本 `"1"`），附件按 24KB 分块传输（1.20.1 单包上限 32767 字节）。
- 图片附件由 DSH 交给模型直接看图；文本类小文件会内联进消息，其它文件落盘后把只读路径告诉 AI（由 DSH 负责）。

## 从源码构建

需要 **JDK 17**（Forge 1.20.1 要求）和 Gradle 8.1.1（仓库自带 wrapper）。

```bash
# 服务端模组：产物 build/libs/mcai-1.1.1.jar
./gradlew build

# 客户端模组：产物 client/build/libs/mcai-client-1.1.1.jar
cd client && ./gradlew build
```

> 在 DSH 的 Windows 沙箱里构建会遇到 ForgeGradle 的 AT 阶段 `ReadOnlyFileSystemException`（JDK zipfs 在受限令牌下把文件系统当只读），这台机器用「给 `jdk.zipfs` 打 `--patch-module` 补丁」的方式绕过，具体见内部构建记录（未随仓库发布）。普通开发机不需要这个。

## FAQ

**客户端没装会怎样？** 服务端照常工作，`/ai`、`/ai message` 正常；`/ai screenshot`、`/ai file` 降级为只发文字并提示。

**客户端装了、服务器没装服务端模组？** 客户端模组的依赖声明要求服务器有 `mcai >= 1.1.0`（`side="SERVER"`），缺了会被 Forge 连接检查拦下。

**截图很大怎么办？** 4K 截图 PNG 可能超过 8MB，客户端会放弃发送并提示；请降低分辨率/窗口后重试。

**文件会存到哪里？** 玩家本地文件只在客户端读取并发送字节；服务端只转发给本机 DSH。DSH 侧文本类小文件内联给模型，其余落盘到 DSH 的 attachments 存储（不会自动删除）。

**和 QQ 群有什么关系？** 本 mod 只服务游戏内 `/ai`；QQ 群那边的服务器管理/群服互通是 DSH 插件 `dsh-mc-server-ops` 的功能。

## License

MIT
