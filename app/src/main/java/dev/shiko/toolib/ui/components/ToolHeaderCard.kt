package dev.shiko.toolib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.logic.PermissionLevel
import dev.shiko.toolib.logic.PermissionSnapshot
import dev.shiko.toolib.logic.ToolDefinition
import dev.shiko.toolib.logic.isAvailable
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.shape.MochiSuperellipse
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

private val ToolIntroIconShape: Shape = MochiSuperellipse(5f)

/** 每个工具页顶部的统一卡片：直接用工具注册表里的条目。 */
@Composable
fun ToolHeaderCard(tool: ToolDefinition, permissions: PermissionSnapshot) {
    ToolHeaderCard(
        title = stringResource(tool.titleRes),
        description = stringResource(tool.descriptionRes),
        icon = tool.icon,
        permission = tool.permission,
        permissions = permissions,
    )
}

/** Large header card used at the top of a tool page. */
@Composable
fun ToolHeaderCard(
    title: String,
    description: String,
    icon: String,
    permission: PermissionLevel,
    permissions: PermissionSnapshot,
) {
    val cardShape = MochiTheme.shapes.extraLargeIncreased
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .background(MochiDefaults.groupedContainer(), cardShape),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(ToolIntroIconShape)
                    .background(MochiTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                MochiIonicon(
                    icon,
                    style = MochiIconStyle.Filled,
                    tint = MochiTheme.colorScheme.onPrimaryContainer,
                    size = 38.dp,
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MochiTheme.textStyles.largeTitle)
                Text(
                    description,
                    style = MochiTheme.textStyles.bodyLarge,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(0.5.dp).background(MochiListDefaults.colors().divider))
        ToolPermissionDeclaration(permission, permissions)
    }
}

@Composable
private fun ToolPermissionDeclaration(
    permission: PermissionLevel,
    permissions: PermissionSnapshot,
) {
    Column(Modifier.padding(8.dp)) {
        MochiListRow(
            title = stringResource(permission.titleRes),
            subtitle = stringResource(permission.explanationRes),
            leading = {
                MochiIonicon(
                    when (permission) {
                        PermissionLevel.NONE -> "lock-open"
                        PermissionLevel.SHELL -> "terminal"
                        PermissionLevel.ROOT -> "shield-checkmark"
                    },
                    style = MochiIconStyle.Filled,
                )
            },
            trailing = {
                val available = permission.isAvailable(permissions)
                MochiIonicon(
                    if (available) "checkmark-circle" else "close-circle",
                    style = MochiIconStyle.Filled,
                    tint = if (available) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.error,
                )
            },
        )
    }
}
