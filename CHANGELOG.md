# Changelog

版本号对应 git tag `vX.Y.Z`。

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
