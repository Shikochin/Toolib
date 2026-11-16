package dev.shiko.toolib.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.MediaDownloadManager
import dev.shiko.toolib.logic.formatDuration
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.theme.MochiTheme

/** Thumbnail, title and uploader summary of an inspected media item. */
@Composable
fun MediaPreviewCard(info: MediaDownloadManager.Inspection) {
    var thumbnail by remember(info.thumbnail) { mutableStateOf<ImageBitmap?>(null) }
    val thumbnailUrl = info.thumbnail.replaceFirst("http://", "https://")
    LaunchedEffect(thumbnailUrl) {
        thumbnail = withContext(Dispatchers.IO) {
            runCatching {
                if (thumbnailUrl.isBlank()) null else {
                    val connection = URL(thumbnailUrl).openConnection().apply {
                        setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
                        setRequestProperty("Referer", info.webpageUrl)
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                    connection.getInputStream().use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
                }
            }.getOrNull()
        }
    }
    MochiListSection(header = stringResource(R.string.media_section_preview)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(
                    RoundedCornerShape(
                        topStart = 26.dp,
                        topEnd = 26.dp,
                        bottomStart = 0.dp,
                        bottomEnd = 0.dp,
                    ),
                )
                .background(MochiTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = thumbnail
            if (bitmap != null) {
                Image(
                    bitmap,
                    contentDescription = stringResource(R.string.media_thumbnail_description),
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                MochiIonicon("play", style = MochiIconStyle.Filled, tint = MochiTheme.colorScheme.onPrimaryContainer)
            }
        }
        MochiListRow(
            title = info.title,
            subtitle = listOfNotNull(info.uploader.takeIf { it.isNotBlank() }, info.duration.takeIf { it > 0 }?.let(::formatDuration))
                .joinToString(" · "),
        )
    }
}
