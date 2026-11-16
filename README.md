# Toolib

一个轻量的 Android 工具箱，以权限等级为基线构建上层建筑。

界面用 Jetpack Compose + [Mochi Compose](https://github.com/feice11/mochi-compose) 实现。

## 工具

- **媒体下载**：yt-dlp 在应用进程内运行（Chaquopy），支持格式选择、播放列表、音视频合并、片段裁剪；默认存到相册/音乐，也可用系统选择器临时指定位置。
- **对称艺术**：选图后用反色线拖动对称轴，左右 / 上下 / 四向对称，下面实时预览，可「应用并继续」反复叠加；输出 PNG / JPEG / WEBP / GIF，默认保存到 `Pictures/Toolib`。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin + Jetpack Compose + Mochi Compose |
| 构建 | compileSdk 37 / minSdk 29 / Gradle 9.9.1 |
| 媒体引擎 | Chaquopy 17.2（CPython 3.13 当原生库加载）+ yt-dlp；合并、裁剪用系统 `MediaMuxer` |
| 图像 | 平台 `Bitmap` / `ImageDecoder`；GIF 编码是自带的纯 Kotlin 实现 |

> 媒体下载不用 `youtubedl-android`：那个库要从 `nativeLibraryDir` 解压并执行 Python/FFmpeg 二进制，必须开 `useLegacyPackaging`，APK 也会被四个 ABI 撑到 200 MB 以上。

## 构建

需要 JDK 17 和 **Python 3.13**（Chaquopy 构建期要跑 pip 装 yt-dlp），确保 `python3.13` 在 `PATH` 里，或用 `TOOLIB_BUILD_PYTHON` 指定。

```bash
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # R8 压缩/混淆
```

`versionCode` 由 git commit 数自动生成，CI 需要 `fetch-depth: 0`。

## 许可证

本项目采用 MIT 许可证。
