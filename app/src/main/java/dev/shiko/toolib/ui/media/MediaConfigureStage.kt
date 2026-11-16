package dev.shiko.toolib.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.MediaDownloadManager
import dev.shiko.toolib.logic.formatTimestamp
import dev.shiko.toolib.ui.components.MediaPreviewCard
import dev.shiko.toolib.ui.components.MochiRangeSlider
import kotlinx.collections.immutable.persistentListOf
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
    val videoLabel = stringResource(R.string.media_format_video_label)
    val audioLabel = stringResource(R.string.media_format_audio_label)
    val downloading = status.phase == MediaDownloadPhase.Downloading

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
        item {
            MochiListSection(header = stringResource(R.string.media_section_download_options)) {
                MochiListRow(
                    stringResource(R.string.media_embed_metadata),
                    trailing = { MochiSwitch(state.embedMetadata, { onStateChange(state.copy(embedMetadata = it)) }) },
                    leading = { MochiIonicon("information-circle", style = MochiIconStyle.Filled) },
                )
                MochiListRow(
                    stringResource(R.string.media_embed_thumbnail),
                    trailing = { MochiSwitch(state.embedThumbnail, { onStateChange(state.copy(embedThumbnail = it)) }) },
                    leading = { MochiIonicon("image", style = MochiIconStyle.Filled) },
                )
                MochiListRow(
                    stringResource(R.string.media_write_subtitles),
                    trailing = { MochiSwitch(state.writeSubtitles, { onStateChange(state.copy(writeSubtitles = it)) }) },
                    leading = { MochiIonicon("text", style = MochiIconStyle.Filled) },
                )
                if (state.writeSubtitles) {
                    MochiListRow(
                        stringResource(R.string.media_embed_subtitles),
                        trailing = { MochiSwitch(state.embedSubtitles, { onStateChange(state.copy(embedSubtitles = it)) }) },
                        leading = { MochiIonicon("text", style = MochiIconStyle.Filled) },
                    )
                    MochiListRow(
                        stringResource(R.string.media_subtitle_languages),
                        trailing = {
                            MochiTextField(
                                state.subtitleLanguages,
                                { onStateChange(state.copy(subtitleLanguages = it)) },
                                modifier = Modifier.width(150.dp),
                                clearButton = false,
                            )
                        },
                        leading = { MochiIonicon("language", style = MochiIconStyle.Filled) },
                    )
                }
                if (info.isPlaylist) {
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
            MochiButton(
                onClick = onDownload,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(MochiRoundedRectangle(MochiButtonDefaults.Height / 2))
                    .background(MochiTheme.colorScheme.surfaceVariant)
                    .drawBehind {
                        val fraction = when {
                            downloading -> status.progress.coerceIn(0f, 1f)
                            status.phase == MediaDownloadPhase.Completed -> 1f
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
                enabled = !downloading,
                colors = buttonColors,
                style = MochiButtonStyle.Filled,
            ) {
                MochiIonicon("download", style = MochiIconStyle.Filled)
                Text(
                    when {
                        downloading -> stringResource(
                            R.string.media_download_progress,
                            (status.progress * 100).toInt(),
                        )
                        status.phase == MediaDownloadPhase.Completed -> stringResource(R.string.media_download_complete)
                        status.phase == MediaDownloadPhase.Failed -> stringResource(R.string.media_action_retry_download)
                        else -> stringResource(R.string.media_action_start_download)
                    },
                )
            }
        }
        if (status.phase != MediaDownloadPhase.Idle) {
            item {
                MochiListSection(header = stringResource(R.string.media_section_status)) {
                    val failed = status.phase == MediaDownloadPhase.Failed
                    val completed = status.phase == MediaDownloadPhase.Completed
                    MochiListRow(
                        title = when (status.phase) {
                            MediaDownloadPhase.Completed -> pluralStringResource(
                                R.plurals.media_download_saved,
                                status.savedFileCount,
                                status.savedFileCount,
                            )
                            MediaDownloadPhase.Failed -> stringResource(R.string.media_download_failed)
                            else -> stringResource(R.string.media_action_downloading)
                        },
                        subtitle = status.error ?: status.logLine,
                        leading = {
                            MochiIonicon(
                                when {
                                    failed -> "close-circle"
                                    completed -> "checkmark-circle"
                                    else -> "information-circle"
                                },
                                style = MochiIconStyle.Filled,
                                tint = if (failed) MochiTheme.colorScheme.error else MochiTheme.colorScheme.primary,
                            )
                        },
                    )
                }
            }
        }
    }
}
