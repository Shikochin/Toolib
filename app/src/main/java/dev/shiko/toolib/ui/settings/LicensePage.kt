package dev.shiko.toolib.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.generatedDependencyLicenses
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold

@Composable
fun LicensePage() {
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.license_title),
        navigationIcon = { MochiBackButton() },
    ) {
        item {
            MochiListSection(header = stringResource(R.string.license_section_project)) {
                MochiListRow(
                    title = stringResource(R.string.license_mit),
                    subtitle = stringResource(R.string.license_mit_subtitle),
                    leading = { MochiIonicon("document-text", style = MochiIconStyle.Filled) },
                )
            }
        }
        item {
            MochiListSection(
                header = stringResource(R.string.license_section_dependencies),
                footer = stringResource(R.string.license_dependencies_footer),
            ) {
                generatedDependencyLicenses.forEach { (name, license) ->
                    MochiListRow(name, subtitle = license)
                }
            }
        }
    }
}
