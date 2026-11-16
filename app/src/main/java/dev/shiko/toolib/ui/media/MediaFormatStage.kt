package dev.shiko.toolib.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.MediaDownloadManager
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold

/** Format picker: best quality or one concrete yt-dlp format. */
@Composable
internal fun MediaFormatStage(
    formats: List<MediaDownloadManager.FormatInfo>,
    kindIndex: Int,
    selected: String?,
    onSelected: (String?) -> Unit,
    onBack: () -> Unit,
) {
    val videoLabel = stringResource(R.string.media_format_video_label)
    val audioLabel = stringResource(R.string.media_format_audio_label)
    val visibleFormats = formats.filter { format -> if (kindIndex == 0) format.hasVideo else !format.hasVideo }
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.media_format_title),
        navigationIcon = { MochiBackButton(onClick = onBack) },
    ) {
        item {
            MochiListSection(header = stringResource(R.string.media_section_format)) {
                MochiListRow(
                    title = stringResource(R.string.media_best_quality),
                    subtitle = stringResource(R.string.media_best_quality_subtitle),
                    checked = selected.isNullOrBlank(),
                    onClick = { onSelected(null) },
                )
                visibleFormats.take(60).forEach { format ->
                    MochiListRow(
                        title = format.label(videoLabel, audioLabel),
                        subtitle = listOfNotNull(
                            format.videoCodec.takeIf { it.isNotBlank() && it != "none" },
                            format.audioCodec.takeIf { it.isNotBlank() && it != "none" },
                        ).joinToString(" + "),
                        checked = selected == format.id,
                        onClick = { onSelected(format.id) },
                    )
                }
                if (visibleFormats.isEmpty()) {
                    MochiListRow(stringResource(R.string.media_no_formats))
                }
            }
        }
    }
}
