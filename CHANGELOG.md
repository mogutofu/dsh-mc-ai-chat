# Changelog

版本号对应 git tag `vX.Y.Z`。

## 1.3.0 — 2026-11-20

- feat: 多平台发布，同一套行为覆盖 Forge / NeoForge / Fabric / Bukkit：
  - Forge 1.20.1（原有工程）、Forge 1.12.2（旧 SimpleImpl 网络重写，Java 8）
  - NeoForge 1.21.1、26.1、26.2、26.3
  - Fabric 26.1、26.2、26.3（Fabric Loader 0.20.1，非 remap）
  - Bukkit / Spigot / Paper 通用插件（1.20+ / 1.21+ / 26.x，单 jar）
- feat: 统一产物命名 `mc-ai-<loader>-<mc>-v1_3_0.jar` / `mc-ai-client-<loader>-<mc>-v1_3_0.jar`；Bukkit 为 `mc-ai-bukkit-v1_3_0.jar`
- feat: 抽出与加载器无关的 `common` 核心（JSON、配置、天气投票状态机、HTTP 客户端、附件模型），所有新平台复用；新增 `tests/WeatherVoteSelfTest.java` 覆盖直接生效/投票通过/否决/OP 与控制台直接/冷却/超时
- feat: NeoForge / Fabric / Forge 1.12.2 的服务端 + 客户端双模组，保留 `/ai screenshot`、`/ai file` 附件（24KB 分块，截图 ≤8MB、文件 ≤16MB）；无客户端时降级只发文字
- feat: Bukkit 插件完整实现 `/ai` + `/weather` + `/vote` 天气投票；通过 `PlayerCommandPreprocessEvent` 替换原版 `/weather`，控制台仍走原版直接执行
- feat: CI（GitHub Actions）分平台构建/矩阵构建，产物名即最终发布名
- docs: README 安装矩阵、PROTOCOL 多加载器协议说明
- chore: Forge 1.20.1 升到 1.3.0 并按新规范改名（功能不变）

## 1.2.0 — 2026-11-15

- feat: 游戏内天气投票——玩家用 `/weather clear|rain|thunder [持续秒数]` 改天气（替换原版需要 OP 的 `/weather`）；在线 ≤ `directThreshold`（默认 2）直接生效，3 人及以上发起投票
- feat: 新增 `/vote yes` / `/vote no` / `/vote status`；发起人自动算 1 票同意，默认 2 票通过、1 票反对作废、60 秒超时、60 秒发起冷却；控制台不受限
- feat: 读取 DSH 插件写入的 `config/mcai-weather.json`（每次都读，改配置即时生效）；文件缺失/损坏回落到默认值
- chore: 客户端模组同步升到 1.2.0（功能不变）

## 1.1.1 — 2026-11-08

- fix: `/ai file` 在引号里直接写 Windows 路径（如 `"C:\Users\me\a.md"`）会报「引号内的字符串包含无效的转义序列」；改为 greedy 参数自行解析，**反斜杠不用转义**，带空格的路径照常加引号即可
- fix: `/ai file` 的消息必填校验移到解析之后，缺消息时给出明确用法提示

## 1.1.0 — 2026-11-08

- feat: 拆成服务端 `mcai` + 客户端 `mcai-client` 两个模组：服务端必装，客户端选装
- feat: `/ai message <内容>` 只发文字；`/ai screenshot [内容]` 截图 + 文字；`/ai file <路径> <内容>` 玩家本地文件 + 文字（旧 `/ai <内容>` 保持兼容）
- feat: 客户端模组安装后走 Forge `mcai:net` 通道传附件（24KB 分块，协议 "1"）；图片按 PNG/原图发送，文本类小文件由 DSH 内联给模型
- feat: 未装客户端模组时截图/文件命令自动降级为只发文字并提示；附件仅支持专用服务器
- chore: `processResources` 固定 UTF-8 编码（`filteringCharset`），避免 GBK 机器上 `mods.toml` 中文乱码

## 1.0.0 — 2026-11-07

- feat: 首个版本：服务端 `/ai <内容>` 通过本机 DSH `/mc-ai` 和「小铜」对话
