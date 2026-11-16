package dev.shiko.toolib.ui.about

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiAvatarButton
import ui.feice.mochi.basic.MochiAvatarDefaults
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.shape.MochiCapsule
import ui.feice.mochi.theme.MochiTheme

private const val DeveloperProfileUrl = "https://github.com/Shikochin"
private const val DeveloperAvatarUrl = "https://avatars.githubusercontent.com/u/60309542?v=4"
private const val RepositoryUrl = "https://github.com/Shikochin/Toolib"

/** 头像只在内存里缓存一次，进出页面不重复下载。 */
private val avatarCache = mutableMapOf<String, ImageBitmap>()

/** 关于页：应用信息、开发者、仓库与开源许可。 */
@Composable
fun AboutPage(onOpenLicense: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val version = remember(context) { appVersion(context) }
    val open: (String) -> Unit = { url -> openLink(context, uriHandler, url) }

    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.about_title),
        navigationIcon = { MochiBackButton() },
    ) {
        item { AboutHeader(version) }
        item { DeveloperSection(onOpenGithub = { open(DeveloperProfileUrl) }) }
        item {
            MochiListSection(header = stringResource(R.string.about_section_project)) {
                LinkRow(
                    title = stringResource(R.string.about_repository_title),
                    subtitle = stringResource(R.string.about_repository_subtitle),
                    icon = "git-branch",
                    onClick = { open(RepositoryUrl) },
                )
                LinkRow(
                    title = stringResource(R.string.about_license_title),
                    subtitle = stringResource(R.string.about_license_subtitle),
                    icon = "document-text",
                    onClick = onOpenLicense,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.about_footer),
                style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 8.dp),
            )
        }
    }
}

/** 图标 + 应用名 + 一句话 + 版本号。 */
@Composable
private fun AboutHeader(version: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 已裁掉四周留白的高清透明图案，直接按宽度铺满
        Image(
            painter = painterResource(R.drawable.ic_toolib_mark),
            contentDescription = null,
            modifier = Modifier.size(132.dp),
            contentScale = ContentScale.Fit,
        )
        Text(
            stringResource(R.string.app_name),
            style = MochiTheme.textStyles.largeTitle,
            modifier = Modifier.padding(top = 12.dp),
        )
        Text(
            stringResource(R.string.about_tagline),
            style = MochiTheme.textStyles.subhead.copy(textAlign = TextAlign.Center),
            color = MochiTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, start = 32.dp, end = 32.dp),
        )
        Box(
            modifier = Modifier
                .padding(top = 12.dp)
                .background(MochiTheme.colorScheme.surfaceContainerHigh, MochiCapsule)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Text(
                "v$version",
                style = MochiTheme.textStyles.footnote,
                color = MochiTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 开发者：头像（点开 GitHub 主页）、名字、账号，下面一行是 GitHub 链接。 */
@Composable
private fun DeveloperSection(onOpenGithub: () -> Unit) {
    val avatar = rememberRemoteImage(DeveloperAvatarUrl)
    MochiListSection(
        header = stringResource(R.string.about_section_developer),
        footer = stringResource(R.string.about_developer_footer),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MochiListDefaults.RowPadding, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MochiAvatarButton(onClick = onOpenGithub, size = 56.dp) {
                val picture = avatar
                if (picture != null) {
                    Image(
                        picture,
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    // 还没下载完或下载失败：和 iOS 通讯录一样显示文字头像
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MochiAvatarDefaults.Gradient),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "西",
                            style = MochiTheme.textStyles.body,
                            color = Color.White,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    stringResource(R.string.about_developer_name),
                    style = MochiTheme.textStyles.body,
                )
                Text(
                    stringResource(R.string.about_developer_handle),
                    style = MochiTheme.textStyles.subhead,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LinkRow(
            title = "GitHub",
            value = "Shikochin",
            icon = "logo-github",
            onClick = onOpenGithub,
        )
    }
}

@Composable
private fun LinkRow(
    title: String,
    icon: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    value: String? = null,
) {
    MochiListRow(
        title = title,
        subtitle = subtitle,
        value = value,
        onClick = onClick,
        chevron = true,
        leading = { MochiIonicon(icon, style = MochiIconStyle.Filled) },
    )
}

/** 下载网络图片（头像），带内存缓存；失败返回 null 由调用方回退。 */
@Composable
private fun rememberRemoteImage(url: String): ImageBitmap? {
    var image by remember(url) { mutableStateOf(avatarCache[url]) }
    LaunchedEffect(url) {
        if (image != null) return@LaunchedEffect
        val loaded = withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(url).openConnection().apply {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14)")
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                // 原图 460px 只用来显示 56dp，按 1/2 采样解码，省内存
                val options = BitmapFactory.Options().apply { inSampleSize = 2 }
                connection.getInputStream().use {
                    BitmapFactory.decodeStream(it, null, options)?.asImageBitmap()
                }
            }.getOrNull()
        }
        if (loaded != null) {
            avatarCache[url] = loaded
            image = loaded
        }
    }
    return image
}

private fun openLink(context: Context, uriHandler: UriHandler, url: String) {
    runCatching { uriHandler.openUri(url) }.onFailure {
        // 没有能打开链接的应用时把地址复制下来，用户自己粘贴
        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Toolib", url))
    }
}

@Suppress("DEPRECATION")
private fun appVersion(context: Context): String = runCatching {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName
}.getOrNull() ?: "?"
