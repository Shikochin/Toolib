package dev.shiko.toolib.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.SafPaths
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection

/**
 * 所有工具共用的"保存位置"分组：点一行就打开系统的文件夹选择器（SAF），
 * 选过之后可以一键恢复默认目录。
 *
 * @param treeUri 当前选中的文件夹（tree URI 字符串），null = 用默认目录
 * @param defaultLabel 默认目录在界面上的显示文案
 */
@Composable
fun SaveLocationSection(
    treeUri: String?,
    defaultLabel: String,
    onTreeUriChange: (String?) -> Unit,
) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            onTreeUriChange(uri.toString())
        }
    }
    MochiListSection(header = stringResource(R.string.media_save_location_title)) {
        MochiListRow(
            title = stringResource(R.string.media_save_location_title),
            subtitle = treeUri?.let { SafPaths.treeDisplayPath(Uri.parse(it)) } ?: defaultLabel,
            leading = { MochiIonicon("folder-open", style = MochiIconStyle.Filled) },
            chevron = true,
            onClick = { picker.launch(null) },
        )
        if (treeUri != null) {
            MochiListRow(
                title = stringResource(R.string.media_save_location_reset),
                leading = { MochiIonicon("refresh", style = MochiIconStyle.Filled) },
                onClick = { onTreeUriChange(null) },
            )
        }
    }
}
