package dev.shiko.toolib.ui.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.PermissionLevel
import dev.shiko.toolib.logic.PermissionSnapshot
import dev.shiko.toolib.ui.components.ToolHeaderCard
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiTextField
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold

/** First stage: paste a link and inspect it. */
@Composable
internal fun MediaInputStage(
    url: String,
    inspecting: Boolean,
    error: String?,
    permissions: PermissionSnapshot,
    onUrlChange: (String) -> Unit,
    onInspect: () -> Unit,
    onBack: () -> Unit,
) {
    MochiLazyCompactTitleScaffold(title = "", navigationIcon = { MochiBackButton(onClick = onBack) }) {
        item {
            ToolHeaderCard(
                title = stringResource(R.string.media_title),
                description = stringResource(R.string.media_description),
                icon = "download",
                permission = PermissionLevel.NONE,
                permissions = permissions,
            )
        }
        item {
            MochiListSection(header = stringResource(R.string.media_section_link)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MochiTextField(
                        value = url,
                        onValueChange = onUrlChange,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = stringResource(R.string.media_url_placeholder),
                        leading = { MochiIonicon("link", style = MochiIconStyle.Filled) },
                        enabled = !inspecting,
                    )
                    MochiButton(
                        onClick = onInspect,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = url.isNotBlank() && !inspecting,
                        style = MochiButtonStyle.Filled,
                    ) {
                        MochiIonicon("search", style = MochiIconStyle.Filled)
                        Text(
                            stringResource(
                                if (inspecting) R.string.media_action_inspecting else R.string.media_action_inspect,
                            ),
                        )
                    }
                }
            }
        }
        if (error != null) {
            item {
                Text(
                    error,
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
