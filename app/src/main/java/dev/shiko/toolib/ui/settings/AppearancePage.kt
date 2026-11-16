package dev.shiko.toolib.ui.settings

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.ThemeSettings
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiSwitch
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.picker.MochiColorWell
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.ColorSchemeMode
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.PaletteStyle

@Composable
fun AppearancePage(
    settings: ThemeSettings,
    onChange: ((ThemeSettings) -> ThemeSettings) -> Unit,
) {
    val modeIndex = when (settings.mode) {
        ColorSchemeMode.System -> 0
        ColorSchemeMode.Light -> 1
        ColorSchemeMode.Dark -> 2
        else -> 0
    }
    val paletteStyles = remember {
        listOf(
            PaletteStyle.TonalSpot,
            PaletteStyle.Expressive,
            PaletteStyle.Vibrant,
            PaletteStyle.Content,
            PaletteStyle.Fidelity,
            PaletteStyle.Neutral,
        )
    }
    val paletteLabels = remember { paletteStyles.map(PaletteStyle::name) }
    val paletteIndex = paletteStyles.indexOf(settings.paletteStyle).coerceAtLeast(0)
    var styleExpanded by remember { mutableStateOf(false) }
    var draftScale by remember(settings.pageScale) { mutableFloatStateOf(settings.pageScale) }
    val predictiveBackSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    val hdrSupported = MochiHdr.supported

    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.appearance_title),
        navigationIcon = { MochiBackButton() },
    ) {
        item { ThemeColorPalette() }
        item {
            MochiListSection(header = stringResource(R.string.appearance_section_theme)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MochiSegmentedControl(
                        selectedIndex = modeIndex,
                        onSelect = { index ->
                            val mode = when (index) {
                                1 -> ColorSchemeMode.Light
                                2 -> ColorSchemeMode.Dark
                                else -> ColorSchemeMode.System
                            }
                            onChange { it.copy(mode = mode) }
                        },
                        items = persistentListOf(
                            stringResource(R.string.appearance_mode_system),
                            stringResource(R.string.appearance_mode_light),
                            stringResource(R.string.appearance_mode_dark),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                MochiListRow(
                    title = stringResource(R.string.appearance_accent_title),
                    subtitle = stringResource(R.string.appearance_accent_subtitle),
                    leading = { MochiIonicon("color-palette", style = MochiIconStyle.Filled) },
                    trailing = {
                        MochiColorWell(
                            color = settings.accentColor,
                            onColorChange = { color -> onChange { it.copy(accentColor = color) } },
                            title = stringResource(R.string.appearance_accent_title),
                            supportsAlpha = false,
                        )
                    },
                )
                MochiListRow(
                    title = stringResource(R.string.appearance_palette_title),
                    subtitle = paletteLabels[paletteIndex],
                    leading = { MochiIonicon("color-filter", style = MochiIconStyle.Filled) },
                    trailing = {
                        MochiDropdown(
                            expanded = styleExpanded,
                            onExpandedChange = { styleExpanded = it },
                            items = persistentListOf(*paletteLabels.map(::MochiItem).toTypedArray()),
                            selectedIndex = paletteIndex,
                            onItemClick = { index ->
                                styleExpanded = false
                                onChange { it.copy(paletteStyle = paletteStyles[index]) }
                            },
                            buttonMinWidth = 120.dp,
                            buttonContent = {
                                Text(paletteLabels[paletteIndex], maxLines = 1)
                                Spacer(Modifier.width(6.dp))
                                MochiIonicon(
                                    "chevron-down",
                                    style = MochiIconStyle.Outline,
                                    tint = MochiTheme.colorScheme.primary,
                                    size = 16.dp,
                                )
                            },
                        )
                    },
                )
            }
        }
        item {
            MochiListSection(header = stringResource(R.string.appearance_section_interaction)) {
                MochiListRow(
                    title = stringResource(R.string.appearance_predictive_back_title),
                    subtitle = stringResource(
                        if (predictiveBackSupported) {
                            R.string.appearance_predictive_back_subtitle
                        } else {
                            R.string.appearance_predictive_back_unavailable
                        },
                    ),
                    leading = { MochiIonicon("arrow-back-circle", style = MochiIconStyle.Filled) },
                    enabled = predictiveBackSupported,
                    trailing = {
                        MochiSwitch(
                            checked = settings.predictiveBack && predictiveBackSupported,
                            onCheckedChange = { enabled -> onChange { it.copy(predictiveBack = enabled) } },
                            enabled = predictiveBackSupported,
                        )
                    },
                )
                MochiListRow(
                    title = stringResource(R.string.appearance_page_scale_title),
                    subtitle = stringResource(R.string.appearance_page_scale_subtitle),
                    value = "${(settings.pageScale * 100).toInt()}%",
                    leading = { MochiIonicon("resize", style = MochiIconStyle.Filled) },
                )
                MochiSlider(
                    value = draftScale,
                    onValueChange = { draftScale = it },
                    onValueChangeFinished = { onChange { it.copy(pageScale = draftScale) } },
                    valueRange = 0.8f..1.1f,
                    detents = listOf(0.8f, 0.9f, 1f, 1.1f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
                MochiListRow(
                    title = stringResource(R.string.appearance_hdr_title),
                    subtitle = stringResource(
                        if (hdrSupported) R.string.appearance_hdr_subtitle else R.string.appearance_hdr_unavailable,
                    ),
                    leading = { MochiIonicon("sunny", style = MochiIconStyle.Filled) },
                    enabled = hdrSupported,
                    trailing = {
                        MochiSwitch(
                            checked = MochiHdr.enabled && hdrSupported,
                            onCheckedChange = { enabled ->
                                MochiHdr.enabled = enabled
                                onChange { it.copy(hdr = enabled) }
                            },
                            enabled = hdrSupported,
                        )
                    },
                )
            }
        }
    }
}

private data class ThemeColorSwatch(
    val name: String,
    val token: String,
    val color: Color,
    val contentColor: Color,
)

@Composable
private fun ThemeColorPalette() {
    val colors = MochiTheme.colorScheme
    val swatches = listOf(
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_primary), "primary", colors.primary, colors.onPrimary),
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_primary_container), "primaryContainer", colors.primaryContainer, colors.onPrimaryContainer),
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_secondary), "secondary", colors.secondary, colors.onSecondary),
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_secondary_container), "secondaryContainer", colors.secondaryContainer, colors.onSecondaryContainer),
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_tertiary), "tertiary", colors.tertiary, colors.onTertiary),
        ThemeColorSwatch(stringResource(R.string.appearance_swatch_tertiary_container), "tertiaryContainer", colors.tertiaryContainer, colors.onTertiaryContainer),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        MochiListSectionHeader(stringResource(R.string.appearance_section_theme_colors))
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            swatches.chunked(2).forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(112.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    row.forEachIndexed { columnIndex, swatch ->
                        ThemeColorSwatchView(
                            swatch = swatch,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            shape = themeColorSwatchShape(rowIndex, columnIndex),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeColorSwatchView(
    swatch: ThemeColorSwatch,
    modifier: Modifier = Modifier,
    shape: Shape,
) {
    Box(
        modifier = modifier
            .background(swatch.color, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = swatch.name,
                style = MochiTheme.textStyles.labelLarge,
                color = swatch.contentColor,
            )
            Text(
                text = swatch.token,
                style = MochiTheme.textStyles.bodySmall,
                color = swatch.contentColor.copy(alpha = 0.78f),
            )
        }
    }
}

private fun themeColorSwatchShape(row: Int, column: Int): Shape {
    val shape = MochiRoundedRectangle(MochiListDefaults.SectionCorner)
    val corners = shape as CornerBasedShape
    return corners.copy(
        topStart = if (row == 0 && column == 0) corners.topStart else ZeroCornerSize,
        topEnd = if (row == 0 && column == 1) corners.topEnd else ZeroCornerSize,
        bottomEnd = if (row == 2 && column == 1) corners.bottomEnd else ZeroCornerSize,
        bottomStart = if (row == 2 && column == 0) corners.bottomStart else ZeroCornerSize,
    )
}
