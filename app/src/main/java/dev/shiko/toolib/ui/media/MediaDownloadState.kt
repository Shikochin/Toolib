package dev.shiko.toolib.ui.media

/** Lifecycle of a download request. Kept as state instead of comparing localized status text. */
internal enum class MediaDownloadPhase { Idle, Downloading, Completed, Failed }

/** Progress, log tail and error of the running (or last) download request. */
internal data class MediaDownloadStatus(
    val phase: MediaDownloadPhase = MediaDownloadPhase.Idle,
    val progress: Float = 0f,
    val savedFileCount: Int = 0,
    val logLine: String? = null,
    val error: String? = null,
)

/** Everything the "download settings" stage lets the user tweak. */
internal data class MediaConfigureState(
    val kindIndex: Int = 0,
    val selectedFormat: String? = null,
    val playlistEnabled: Boolean = false,
    val selectedPlaylistCount: Int = 0,
    val embedMetadata: Boolean = true,
    val embedThumbnail: Boolean = false,
    val writeSubtitles: Boolean = false,
    val embedSubtitles: Boolean = false,
    val subtitleLanguages: String = "all",
    val clipRange: ClosedFloatingPointRange<Float> = 0f..1f,
) {
    /** Switching between video and audio invalidates the picked format. */
    fun withKind(index: Int): MediaConfigureState = copy(kindIndex = index, selectedFormat = null)
}
