# AGENTS.md

在这个仓库里写代码（包括 AI agent）时请遵守以下约定。

## 工具页

- **顶部卡片是强制参数**：每个工具页的入口 composable 必须接收 `header: @Composable () -> Unit`，
  并在页面最上方渲染它。卡片内容由导航层统一注入（`ToolHeaderCard(tool, permissions)`），
  工具页不要自己拼标题卡片，也不要写自己的标题文案。
- **不要重复标题**：卡片里已经有工具名了，页面骨架用
  `MochiLazyCompactTitleScaffold(title = "")` + `MochiBackButton`，不要再加大标题。
- 新工具要在 [`logic/ToolCatalog.kt`](app/src/main/java/dev/shiko/toolib/logic/ToolCatalog.kt) 注册
  （id / titleRes / descriptionRes / icon / permission），`MainActivity` 按 id 分发页面。
- 保存位置统一用 [`ui/components/SaveLocationSection`](app/src/main/java/dev/shiko/toolib/ui/components/SaveLocationSection.kt)
  （SAF 文件夹选择 + 一键恢复默认目录），不要每个工具各写一套。

## 文案

- 新增 key 要同时补 `values`、`values-zh`、`values-b+zh+Hant`、`values-ja` 四份，键集必须完全一致。
- 计数类文案用 `<plurals>`；`%1$d`、`%1$d%%` 这类占位符原样保留。
- 界面里不要硬编码中文，一律走 `stringResource` / `pluralStringResource`。

## 平台约束

- 不要在应用数据目录里 exec 二进制（Android 10+ 直接禁止），也不要用需要
  `jniLibs.useLegacyPackaging = true` 的库——那会把 APK 撑大且必须解压原生库。
- 需要 Python 用 Chaquopy（CPython 当原生库加载）；需要 FFmpeg 类能力优先用平台 API
  （`MediaMuxer` / `MediaExtractor` / `MediaCodec`）。
- `versionCode` 由 git commit 数自动生成，不要手改；CI 需要 `fetch-depth: 0`。
