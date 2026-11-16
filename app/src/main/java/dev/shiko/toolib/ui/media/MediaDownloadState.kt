package dev.shiko.toolib.ui.media

import android.net.Uri

/** 下载请求的生命周期，直接驱动下载按钮上的文案。 */
internal enum class MediaDownloadPhase { Idle, Downloading, Muxing, Trimming, Completed, Failed }

/** 进度、速度、日志尾巴和错误；按钮和状态行都从这里取。 */
internal data class MediaDownloadStatus(
    val phase: MediaDownloadPhase = MediaDownloadPhase.Idle,
    val progress: Float = 0f,
    val speedBytesPerSecond: Long = 0L,
    val files: List<Uri> = emptyList(),
    val mimeType: String = "",
    val logLine: String? = null,
    val error: String? = null,
)

/** "下载前配置" 阶段让用户调整的选项。 */
internal data class MediaConfigureState(
    val kindIndex: Int = 0,
    val selectedFormat: String? = null,
    val playlistEnabled: Boolean = false,
    val selectedPlaylistCount: Int = 0,
    val clipRange: ClosedFloatingPointRange<Float> = 0f..1f,
    /** 本次下载的保存文件夹（SAF tree URI，临时、不落盘）；null 表示用默认目录。 */
    val saveTreeUri: String? = null,
) {
    /** Switching between video and audio invalidates the picked format. */
    fun withKind(index: Int): MediaConfigureState = copy(kindIndex = index, selectedFormat = null)
}
