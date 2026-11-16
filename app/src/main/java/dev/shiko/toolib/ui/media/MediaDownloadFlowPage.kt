package dev.shiko.toolib.ui.media

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import dev.shiko.toolib.logic.MediaDownloadManager
import dev.shiko.toolib.logic.PermissionSnapshot
import dev.shiko.toolib.logic.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.rememberMochiNavigator

/** Steps of the link -> playlist -> settings -> format flow. */
private enum class MediaFlowStage { Input, Playlist, Configure, Formats }

/**
 * Media download tool: inspects a link, lets the user pick playlist items, formats and options,
 * then hands the request to [MediaDownloadManager].
 */
@Composable
fun MediaDownloadFlowPage(
    onBack: () -> Unit = {},
    permissions: PermissionSnapshot = PermissionSnapshot(),
) {
    val context = LocalContext.current
    val manager = remember(context) { MediaDownloadManager(context) }
    val scope = rememberCoroutineScope()
    val mediaNavigator = rememberMochiNavigator(MediaFlowStage.Input.name)
    var url by remember { mutableStateOf("") }
    var inspection by remember { mutableStateOf<MediaDownloadManager.Inspection?>(null) }
    var selectedPlaylist by remember { mutableStateOf<Set<Int>>(emptySet()) }
    var configure by remember { mutableStateOf(MediaConfigureState()) }
    var status by remember { mutableStateOf(MediaDownloadStatus()) }
    var inspecting by remember { mutableStateOf(false) }
    var inspectError by remember { mutableStateOf<String?>(null) }

    fun navigateTo(next: MediaFlowStage) {
        if (mediaNavigator.current != next.name) mediaNavigator.push(next.name)
    }

    fun navigateBack() {
        if (!mediaNavigator.pop()) onBack()
    }

    fun inspectUrl() {
        if (url.isBlank() || inspecting) return
        inspecting = true
        inspectError = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { manager.inspect(url.trim()) }
                inspection = result
                selectedPlaylist = result.playlist.map { it.index }.toSet()
                configure = configure.copy(
                    selectedFormat = null,
                    clipRange = 0f..result.duration.toFloat().coerceAtLeast(1f),
                )
                status = MediaDownloadStatus()
                navigateTo(MediaFlowStage.Configure)
            } catch (cause: Throwable) {
                inspectError = cause.message
            } finally {
                inspecting = false
            }
        }
    }

    fun beginDownload() {
        val info = inspection ?: return
        if (status.phase == MediaDownloadPhase.Downloading) return
        status = MediaDownloadStatus(phase = MediaDownloadPhase.Downloading)
        val kind = if (configure.kindIndex == 0) MediaDownloadManager.Kind.VIDEO else MediaDownloadManager.Kind.AUDIO
        val duration = info.duration.toFloat()
        val hasClip = duration > 0f && (configure.clipRange.start > 0f || configure.clipRange.endInclusive < duration)
        val selectedFormat = configure.selectedFormat?.takeIf { it.isNotBlank() }?.let { id ->
            val format = info.formats.firstOrNull { it.id == id }
            if (format?.hasVideo == true && !format.hasAudio) "$id+bestaudio[ext=m4a]/$id" else id
        }
        val options = MediaDownloadManager.Options(
            kind = kind,
            selectedFormat = selectedFormat,
            playlist = configure.playlistEnabled,
            playlistItems = if (configure.playlistEnabled) selectedPlaylist.toList().sorted() else emptyList(),
            embedMetadata = configure.embedMetadata,
            embedThumbnail = configure.embedThumbnail,
            writeSubtitles = configure.writeSubtitles,
            embedSubtitles = configure.embedSubtitles,
            subtitleLanguages = configure.subtitleLanguages.ifBlank { "all" },
            clipStart = if (hasClip) formatTimestamp(configure.clipRange.start.toLong()) else "",
            clipEnd = if (hasClip) formatTimestamp(configure.clipRange.endInclusive.toLong()) else "",
            sourceTitle = info.title,
            sourceUploader = info.uploader,
            sourceThumbnail = info.thumbnail,
            sourceDuration = info.duration,
        )
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    manager.download(url.trim(), options) { event ->
                        when (event) {
                            is MediaDownloadManager.Event.Progress -> status = status.copy(
                                progress = (event.progressPercent / 100f).coerceIn(0f, 1f),
                                logLine = event.line.takeLast(180),
                            )
                            is MediaDownloadManager.Event.Output -> status = status.copy(logLine = event.line.takeLast(180))
                            else -> Unit
                        }
                    }
                }
                status = status.copy(
                    phase = MediaDownloadPhase.Completed,
                    progress = 1f,
                    savedFileCount = result.files.size,
                )
            } catch (cause: Throwable) {
                status = status.copy(phase = MediaDownloadPhase.Failed, error = cause.message)
            }
        }
    }

    MochiNavHost(mediaNavigator, Modifier.fillMaxSize()) { route ->
        when (MediaFlowStage.valueOf(route)) {
            MediaFlowStage.Input -> MediaInputStage(
                url = url,
                inspecting = inspecting,
                error = inspectError,
                permissions = permissions,
                onUrlChange = { url = it },
                onInspect = ::inspectUrl,
                onBack = ::navigateBack,
            )

            MediaFlowStage.Playlist -> inspection?.let { info ->
                MediaPlaylistStage(
                    info = info,
                    selected = selectedPlaylist,
                    onToggle = { index ->
                        selectedPlaylist = if (index in selectedPlaylist) selectedPlaylist - index else selectedPlaylist + index
                    },
                    onSelectAll = { selectedPlaylist = it },
                    onContinue = {
                        if (selectedPlaylist.isNotEmpty()) {
                            configure = configure.copy(playlistEnabled = true)
                            navigateBack()
                        }
                    },
                    onBack = ::navigateBack,
                )
            }

            MediaFlowStage.Configure -> inspection?.let { info ->
                MediaConfigureStage(
                    info = info,
                    state = configure.copy(selectedPlaylistCount = selectedPlaylist.size),
                    status = status,
                    onStateChange = { configure = it },
                    onSelectFormat = { navigateTo(MediaFlowStage.Formats) },
                    onSelectPlaylist = { if (info.isPlaylist) navigateTo(MediaFlowStage.Playlist) },
                    onDownload = ::beginDownload,
                    onBack = ::navigateBack,
                )
            }

            MediaFlowStage.Formats -> inspection?.let { info ->
                MediaFormatStage(
                    formats = info.formats,
                    kindIndex = configure.kindIndex,
                    selected = configure.selectedFormat,
                    onSelected = { configure = configure.copy(selectedFormat = it) },
                    onBack = ::navigateBack,
                )
            }
        }
    }
}
