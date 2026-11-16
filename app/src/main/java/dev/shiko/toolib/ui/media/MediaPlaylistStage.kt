package dev.shiko.toolib.ui.media

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.MediaDownloadManager
import dev.shiko.toolib.logic.formatDuration
import dev.shiko.toolib.ui.components.MediaPreviewCard
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.theme.MochiTheme

/** Playlist picker shown when the inspected link contains multiple entries. */
@Composable
internal fun MediaPlaylistStage(
    info: MediaDownloadManager.Inspection,
    selected: Set<Int>,
    onToggle: (Int) -> Unit,
    onSelectAll: (Set<Int>) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val allIndexes = remember(info) { info.playlist.map { it.index }.toSet() }
    val allSelected = selected.containsAll(allIndexes)
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.media_playlist_title),
        navigationIcon = { MochiBackButton(onClick = onBack) },
    ) {
        item { MediaPreviewCard(info) }
        item {
            MochiButton(
                onClick = { onSelectAll(if (allSelected) emptySet() else allIndexes) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                style = MochiButtonStyle.Outlined,
            ) {
                MochiIonicon("checkmark-done", style = MochiIconStyle.Filled)
                Text(
                    if (allSelected) {
                        stringResource(R.string.media_action_deselect_all)
                    } else {
                        pluralStringResource(
                            R.plurals.media_action_select_all,
                            info.playlist.size,
                            info.playlist.size,
                        )
                    },
                )
            }
        }
        items(info.playlist, key = { it.index }) { entry ->
            MochiListRow(
                title = entry.title,
                subtitle = if (entry.duration > 0) {
                    formatDuration(entry.duration)
                } else {
                    stringResource(R.string.media_playlist_item_fallback, entry.index)
                },
                leading = {
                    MochiIonicon(
                        if (entry.index in selected) "checkmark-circle" else "ellipse-outline",
                        style = MochiIconStyle.Filled,
                        tint = MochiTheme.colorScheme.primary,
                    )
                },
                onClick = { onToggle(entry.index) },
            )
        }
        item {
            MochiButton(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                enabled = selected.isNotEmpty(),
                style = MochiButtonStyle.Filled,
            ) {
                MochiIonicon("arrow-forward", style = MochiIconStyle.Filled)
                Text(stringResource(R.string.media_action_continue, selected.size))
            }
        }
    }
}
