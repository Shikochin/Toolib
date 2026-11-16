package dev.shiko.toolib.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.MediaDownloadManager
import dev.shiko.toolib.logic.formatSpeed
import dev.shiko.toolib.logic.formatTimestamp
import dev.shiko.toolib.ui.components.MediaPreviewCard
import dev.shiko.toolib.ui.components.SaveLocationSection
import dev.shiko.toolib.ui.components.MochiRangeSlider
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonDefaults
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

/** Second stage: everything the user can tune before the download starts. */
@Composable
internal fun MediaConfigureStage(
    info: MediaDownloadManager.Inspection,
    state: MediaConfigureState,
    status: MediaDownloadStatus,
    onStateChange: (MediaConfigureState) -> Unit,
    onSelectFormat: () -> Unit,
    onSelectPlaylist: () -> Unit,
    onDownload: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val kind = if (state.kindIndex == 0) MediaDownloadManager.Kind.VIDEO else MediaDownloadManager.Kind.AUDIO
    val videoLabel = stringResource(R.string.media_format_video_label)
    val audioLabel = stringResource(R.string.media_format_audio_label)
    val downloading = status.phase == MediaDownloadPhase.Downloading

    Box(Modifier.fillMaxSize()) {
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.media_config_title),
        navigationIcon = { MochiBackButton(onClick = onBack) },
    ) {
        item { MediaPreviewCard(info) }
        item {
            MochiListSection(header = stringResource(R.string.media_section_download_type)) {
                Box(Modifier.padding(16.dp)) {
                    MochiSegmentedControl(
                        selectedIndex = state.kindIndex,
                        onSelect = { onStateChange(state.withKind(it)) },
                        items = persistentListOf(
                            stringResource(R.string.media_kind_video),
                            stringResource(R.string.media_kind_audio),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MochiListRow(
                    stringResource(R.string.media_format_row),
                    subtitle = state.selectedFormat
                        ?.let { id -> info.formats.firstOrNull { it.id == id }?.label(videoLabel, audioLabel) }
                        ?: stringResource(R.string.media_best_quality),
                    leading = { MochiIonicon("options", style = MochiIconStyle.Filled) },
                    onClick = onSelectFormat,
                    chevron = true,
                )
                if (info.isPlaylist) {
                    MochiListRow(
                        stringResource(R.string.media_playlist_entries),
                        subtitle = if (state.playlistEnabled) {
                            pluralStringResource(
                                R.plurals.media_playlist_selected_count,
                                state.selectedPlaylistCount,
                                state.selectedPlaylistCount,
                            )
                        } else {
                            stringResource(R.string.media_playlist_all_entries)
                        },
                        leading = { MochiIonicon("list", style = MochiIconStyle.Filled) },
                        onClick = onSelectPlaylist,
                        chevron = true,
                    )
                }
            }
        }
        if (info.isPlaylist) {
            item {
                MochiListSection(header = stringResource(R.string.media_section_download_options)) {
                    MochiListRow(
                        stringResource(R.string.media_download_playlist),
                        subtitle = if (state.playlistEnabled) {
                            pluralStringResource(
                                R.plurals.media_playlist_on_subtitle,
                                state.selectedPlaylistCount,
                                state.selectedPlaylistCount,
                            )
                        } else {
                            stringResource(R.string.media_playlist_off_subtitle)
                        },
                        trailing = { MochiSwitch(state.playlistEnabled, { onStateChange(state.copy(playlistEnabled = it)) }) },
                        leading = { MochiIonicon("list", style = MochiIconStyle.Filled) },
                    )
                }
            }
        }
        item {
            SaveLocationSection(
                treeUri = state.saveTreeUri,
                defaultLabel = MediaDownloadManager.displaySaveLocation(null, kind),
                onTreeUriChange = { onStateChange(state.copy(saveTreeUri = it)) },
            )
        }
        item {
            MochiListSection(header = stringResource(R.string.media_section_video_processing)) {
                val maxDuration = info.duration.toFloat().coerceAtLeast(1f)
                MochiListRow(
                    title = stringResource(R.string.media_clip_range),
                    subtitle = if (info.duration > 0L) {
                        "${formatTimestamp(state.clipRange.start.toLong())} - " +
                            formatTimestamp(state.clipRange.endInclusive.toLong())
                    } else {
                        stringResource(R.string.media_duration_unavailable)
                    },
                    leading = { MochiIonicon("scan", style = MochiIconStyle.Filled) },
                )
                MochiRangeSlider(
                    value = state.clipRange.start.coerceIn(0f, maxDuration)..state.clipRange.endInclusive.coerceIn(0f, maxDuration),
                    onValueChange = { range -> onStateChange(state.copy(clipRange = range.start..range.endInclusive)) },
                    valueRange = 0f..maxDuration,
                    enabled = info.duration > 0L,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
        }
        if (status.phase == MediaDownloadPhase.Completed && status.files.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MochiButton(
                        onClick = { shareMedia(context, status.files.first(), status.mimeType) },
                        modifier = Modifier.weight(1f),
                        style = MochiButtonStyle.Filled,
                    ) {
                        MochiIonicon("share-social", style = MochiIconStyle.Filled)
                        Text(stringResource(R.string.media_action_share))
                    }
                    MochiButton(
                        onClick = { openMedia(context, status.files.first(), status.mimeType) },
                        modifier = Modifier.weight(1f),
                        style = MochiButtonStyle.Outlined,
                    ) {
                        MochiIonicon("open", style = MochiIconStyle.Filled)
                        Text(stringResource(R.string.media_action_open))
                    }
                }
            }
        } else {
        item {
            val buttonColors = MochiButtonDefaults.colors(MochiButtonStyle.Filled).copy(
                container = Color.Transparent,
                content = MochiTheme.colorScheme.onSurface,
                outline = Color.Transparent,
                pressedContainer = MochiTheme.colorScheme.primary.copy(alpha = 0.12f),
                disabledContainer = Color.Transparent,
                disabledContent = MochiTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            val progressFillColor = MochiTheme.colorScheme.primaryContainer
            val busy = status.phase.isBusy()
            MochiButton(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MochiRoundedRectangle(MochiButtonDefaults.Height / 2))
                    .background(MochiTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        val fraction = when (status.phase) {
                            MediaDownloadPhase.Downloading -> status.progress.coerceIn(0f, 1f)
                            // 合并 / 裁剪没有真实进度：整块强调色铺满，不动
                            MediaDownloadPhase.Muxing, MediaDownloadPhase.Trimming -> 1f
                            MediaDownloadPhase.Completed -> 1f
                            else -> 0f
                        }
                        if (fraction > 0f) {
                            drawRoundRect(
                                color = progressFillColor,
                                size = size.copy(width = size.width * fraction),
                                cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
                            )
                        }
                    },
                enabled = !busy,
                colors = buttonColors,
                style = MochiButtonStyle.Filled,
            ) {
                MochiIonicon("download", style = MochiIconStyle.Filled)
                Text(downloadButtonLabel(status))
            }
        }
        }
        if (status.phase == MediaDownloadPhase.Failed && status.error != null) {
            item {
                Text(
                    status.error,
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.error,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }
    }
    }
}

/** 下载中 / 后处理中：按钮不可点，并且显示进度。 */
private fun MediaDownloadPhase.isBusy(): Boolean = this == MediaDownloadPhase.Downloading ||
    this == MediaDownloadPhase.Muxing ||
    this == MediaDownloadPhase.Trimming

/** 按钮上的文案：直接反映当前阶段。 */
@Composable
private fun downloadButtonLabel(status: MediaDownloadStatus): String = when (status.phase) {
    MediaDownloadPhase.Downloading -> {
        val percent = (status.progress * 100).toInt().coerceIn(0, 100)
        if (status.speedBytesPerSecond > 0) {
            stringResource(
                R.string.media_download_progress_speed,
                percent,
                formatSpeed(status.speedBytesPerSecond),
            )
        } else {
            stringResource(R.string.media_download_progress, percent)
        }
    }
    MediaDownloadPhase.Muxing -> stringResource(R.string.media_stage_muxing)
    MediaDownloadPhase.Trimming -> stringResource(R.string.media_stage_trimming)
    MediaDownloadPhase.Completed -> stringResource(R.string.media_download_complete)
    MediaDownloadPhase.Failed -> stringResource(R.string.media_action_retry_download)
    MediaDownloadPhase.Idle -> stringResource(R.string.media_action_start_download)
}

/** 用系统应用打开刚下载的文件。 */
private fun openMedia(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType.ifBlank { "*/*" })
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}

/** 分享刚下载的文件。 */
private fun shareMedia(context: Context, uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mimeType.ifBlank { "*/*" }
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null)) }
}
