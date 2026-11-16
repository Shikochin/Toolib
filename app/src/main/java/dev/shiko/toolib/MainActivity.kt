package dev.shiko.toolib

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.logic.PermissionProbe
import dev.shiko.toolib.logic.PermissionSnapshot
import dev.shiko.toolib.logic.ShizukuPermissionRequest
import dev.shiko.toolib.logic.ThemePreferences
import dev.shiko.toolib.logic.ThemeSettings
import dev.shiko.toolib.logic.ToolCatalog
import dev.shiko.toolib.logic.ToolDefinition
import dev.shiko.toolib.logic.pollPermissionSnapshot
import dev.shiko.toolib.ui.about.AboutPage
import dev.shiko.toolib.ui.components.ToolCard
import dev.shiko.toolib.ui.media.MediaDownloadFlowPage
import dev.shiko.toolib.ui.settings.AppearancePage
import dev.shiko.toolib.ui.settings.LicensePage
import dev.shiko.toolib.ui.settings.SettingsPage
import dev.shiko.toolib.ui.symmetry.SymmetryPage
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiTabItem
import ui.feice.mochi.effect.overscroll.ProvideMochiOverscroll
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.navigation.MochiNavHost
import ui.feice.mochi.navigation.MochiTabScaffold
import ui.feice.mochi.navigation.rememberMochiNavigator
import ui.feice.mochi.navigation.rememberMochiTabScaffoldState
import ui.feice.mochi.theme.MochiDefaults
import ui.feice.mochi.theme.MochiHdr
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.theme.ThemeController
import ui.feice.mochi.utils.mochiEnableEdgeToEdge

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        mochiEnableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent { ToolibApp() }
    }
}

private const val RootRoute = "root"
private const val ToolRoutePrefix = "tool/"
private const val AboutRoute = "about"
private const val LicenseRoute = "license"
private const val AppearanceRoute = "appearance"

@Composable
private fun ToolibApp() {
    val context = LocalContext.current
    val themePreferences = remember(context) { ThemePreferences(context) }
    var themeSettings by remember(themePreferences) { mutableStateOf(themePreferences.load()) }
    val baseDensity = LocalDensity.current
    val controller = remember(themeSettings) {
        ThemeController(
            colorSchemeMode = themeSettings.mode,
            keyColor = themeSettings.accentColor,
            paletteStyle = themeSettings.paletteStyle,
        )
    }
    val navigator = rememberMochiNavigator(RootRoute)
    var permissions by remember { mutableStateOf(PermissionSnapshot()) }

    fun updateTheme(transform: (ThemeSettings) -> ThemeSettings) {
        themeSettings = transform(themeSettings)
        themePreferences.save(themeSettings)
    }

    MochiHdr.enabled = themeSettings.hdr

    LaunchedEffect(context) {
        pollPermissionSnapshot { permissions = it }
    }

    CompositionLocalProvider(
        LocalDensity provides Density(
            density = baseDensity.density * themeSettings.pageScale,
            fontScale = baseDensity.fontScale,
        ),
    ) {
        MochiTheme(controller = controller) {
            ProvideMochiOverscroll {
                MochiNavHost(
                    navigator,
                    Modifier.fillMaxSize(),
                    predictiveBack = themeSettings.predictiveBack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE,
                ) { route ->
                    when {
                        route == RootRoute -> RootPage(
                            permissions = permissions,
                            onOpenAppearance = { navigator.push(AppearanceRoute) },
                            onToolClick = { tool -> navigator.push(ToolRoutePrefix + tool.id, from = ToolCatalog.source(tool)) },
                            onOpenAbout = { navigator.push(AboutRoute) },
                            onRequestShell = { ShizukuPermissionRequest.request { permissions = PermissionProbe.snapshot() } },
                        )
                        route == AboutRoute -> AboutPage(onOpenLicense = { navigator.push(LicenseRoute) })
                        route == LicenseRoute -> LicensePage()
                        route == AppearanceRoute -> AppearancePage(themeSettings, ::updateTheme)
                        route.startsWith(ToolRoutePrefix) -> when (ToolCatalog.find(route.removePrefix(ToolRoutePrefix))?.id) {
                            ToolCatalog.MEDIA_DOWNLOAD_ID -> MediaDownloadFlowPage(
                                onBack = { navigator.pop() },
                                permissions = permissions,
                            )
                            ToolCatalog.SYMMETRY_ID -> SymmetryPage(onBack = { navigator.pop() })
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

/** Tab container: the tool grid and the settings list. */
@Composable
private fun RootPage(
    permissions: PermissionSnapshot,
    onOpenAppearance: () -> Unit,
    onToolClick: (ToolDefinition) -> Unit,
    onOpenAbout: () -> Unit,
    onRequestShell: () -> Unit,
) {
    val toolsLabel = stringResource(R.string.tab_tools)
    val settingsLabel = stringResource(R.string.tab_settings)
    val tabs = remember(toolsLabel, settingsLabel) {
        persistentListOf(
            MochiTabItem(
                label = toolsLabel,
                selectedIcon = { tint -> MochiIonicon("apps", style = MochiIconStyle.Filled, tint = tint) },
                icon = { tint -> MochiIonicon("apps", tint = tint) },
            ),
            MochiTabItem(
                label = settingsLabel,
                selectedIcon = { tint -> MochiIonicon("settings", style = MochiIconStyle.Filled, tint = tint) },
                icon = { tint -> MochiIonicon("settings", tint = tint) },
            ),
        )
    }
    val tabState = rememberMochiTabScaffoldState()
    MochiTabScaffold(
        items = tabs,
        state = tabState,
        collapseOnScroll = true,
        background = MochiDefaults.groupedBackground(),
    ) { index ->
        if (index == 0) {
            HomePage(permissions, onToolClick)
        } else {
            SettingsPage(
                permissions = permissions,
                onOpenAppearance = onOpenAppearance,
                onOpenAbout = onOpenAbout,
                onRequestShell = onRequestShell,
            )
        }
    }
}

@Composable
private fun HomePage(permissions: PermissionSnapshot, onToolClick: (ToolDefinition) -> Unit) {
    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.app_name),
        contentSpacing = 16.dp,
    ) {
        toolRows(permissions, onToolClick)
    }
}

/** Two cards per row; a trailing spacer keeps an odd card at half width. */
private fun LazyListScope.toolRows(
    permissions: PermissionSnapshot,
    onToolClick: (ToolDefinition) -> Unit,
) {
    val rows = ToolCatalog.tools.chunked(2)
    items(rows.size) { rowIndex ->
        val row = rows[rowIndex]
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            row.forEach { tool ->
                ToolCard(
                    tool = tool,
                    permissions = permissions,
                    onClick = { onToolClick(tool) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
    }
}
