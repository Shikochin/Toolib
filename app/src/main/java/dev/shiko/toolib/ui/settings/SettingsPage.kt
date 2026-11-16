package dev.shiko.toolib.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.PermissionLevel
import dev.shiko.toolib.logic.PermissionSnapshot
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.theme.MochiTheme

@Composable
fun SettingsPage(
    permissions: PermissionSnapshot,
    onOpenAppearance: () -> Unit,
    onOpenAbout: () -> Unit,
    onRequestShell: () -> Unit,
) {
    MochiLazyLargeTitleScaffold(title = stringResource(R.string.settings_title)) {
        item {
            MochiListSection(header = stringResource(R.string.settings_section_appearance)) {
                MochiListRow(
                    title = stringResource(R.string.settings_section_appearance),
                    subtitle = stringResource(R.string.settings_appearance_subtitle),
                    leading = { MochiIonicon("color-palette", style = MochiIconStyle.Filled) },
                    chevron = true,
                    onClick = onOpenAppearance,
                )
            }
        }
        item {
            Column {
                MochiListSectionHeader(stringResource(R.string.settings_section_permissions))
                MochiListCard {
                    PermissionRow(PermissionLevel.NONE, true)
                    PermissionRow(PermissionLevel.SHELL, permissions.shellGranted, onClick = onRequestShell)
                    PermissionRow(PermissionLevel.ROOT, permissions.rootGranted)
                }
            }
        }
        item {
            MochiListSection(header = stringResource(R.string.settings_section_about)) {
                MochiListRow(
                    title = stringResource(R.string.settings_about_title),
                    subtitle = stringResource(R.string.settings_about_subtitle),
                    leading = { MochiIonicon("information-circle", style = MochiIconStyle.Filled) },
                    chevron = true,
                    onClick = onOpenAbout,
                )
            }
        }
    }
}

@Composable
private fun PermissionRow(level: PermissionLevel, granted: Boolean, onClick: (() -> Unit)? = null) {
    val available = level == PermissionLevel.NONE || granted
    MochiListRow(
        title = stringResource(level.titleRes),
        subtitle = stringResource(level.explanationRes),
        onClick = onClick,
        leading = {
            MochiIonicon(
                when (level) {
                    PermissionLevel.NONE -> "lock-open"
                    PermissionLevel.SHELL -> "terminal"
                    PermissionLevel.ROOT -> "shield-checkmark"
                },
                style = MochiIconStyle.Filled,
            )
        },
        trailing = {
            MochiIonicon(
                if (available) "checkmark" else "close",
                style = MochiIconStyle.Filled,
                tint = if (available) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.error,
            )
        },
    )
}
