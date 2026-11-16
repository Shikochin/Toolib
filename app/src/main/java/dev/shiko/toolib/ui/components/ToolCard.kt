package dev.shiko.toolib.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.logic.PermissionSnapshot
import dev.shiko.toolib.logic.ToolCatalog
import dev.shiko.toolib.logic.ToolDefinition
import dev.shiko.toolib.logic.isAvailable
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.navigation.mochiNavSource
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiTheme

private val ToolCardCorner = 26.dp

/** Compact home grid entry for a tool. */
@Composable
fun ToolCard(
    tool: ToolDefinition,
    permissions: PermissionSnapshot,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = tool.permission.isAvailable(permissions)
    MochiListCard(
        modifier = modifier
            .height(148.dp)
            .mochiNavSource(ToolCatalog.source(tool), ToolCardCorner, MochiDefaults.groupedContainer()),
        showDividers = false,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(onClick = onClick)
                .semantics { role = Role.Button }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(MochiRoundedRectangle(11.dp))
                    .background(if (available) MochiTheme.colorScheme.primaryContainer else MochiTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                MochiIonicon(
                    tool.icon,
                    style = MochiIconStyle.Filled,
                    tint = if (available) MochiTheme.colorScheme.onPrimaryContainer else MochiTheme.colorScheme.onSurfaceVariant,
                    size = 20.dp,
                )
            }
            Text(
                stringResource(tool.titleRes),
                style = MochiTheme.textStyles.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(tool.descriptionRes),
                style = MochiTheme.textStyles.bodySmall,
                color = MochiTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
