package dev.shiko.toolib.ui.symmetry

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.shiko.toolib.R
import dev.shiko.toolib.logic.ImageExporter
import dev.shiko.toolib.logic.ImageFormat
import dev.shiko.toolib.logic.SymmetryMode
import dev.shiko.toolib.logic.SymmetryRenderer
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

private const val SourceMaxSize = 2400
private const val PreviewMaxSize = 720

/** 对称艺术：选图 → 拖反色线定对称轴 → 实时预览 → 继续对称 / 保存。 */
@Composable
fun SymmetryPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var source by remember { mutableStateOf<Bitmap?>(null) }
    var previewSource by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(SymmetryMode.LeftRight) }
    var axisX by remember { mutableFloatStateOf(0.5f) }
    var axisY by remember { mutableFloatStateOf(0.5f) }
    var format by remember { mutableStateOf(ImageFormat.Png) }
    var quality by remember { mutableFloatStateOf(92f) }
    var formatExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            message = null
            val loaded = withContext(Dispatchers.IO) { loadBitmap(context, uri, SourceMaxSize) }
            if (loaded == null) {
                message = resources.getString(R.string.symmetry_load_failed)
            } else {
                source = loaded
                previewSource = withContext(Dispatchers.Default) {
                    SymmetryRenderer.scaleDown(loaded, PreviewMaxSize)
                }
            }
            busy = false
        }
    }

    val saveAsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data
        val bitmap = previewSource
        if (uri == null || bitmap == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val rendered = SymmetryRenderer.render(source ?: bitmap, mode, axisX, axisY)
                    val bytes = ImageExporter.encode(rendered, format, quality.toInt())
                    ImageExporter.saveToDocument(context, uri, bytes)
                }
            }
            busy = false
            message = if (outcome.isSuccess) {
                resources.getString(R.string.symmetry_saved)
            } else {
                outcome.exceptionOrNull()?.message
            }
        }
    }

    LaunchedEffect(previewSource, mode, axisX, axisY) {
        val base = previewSource ?: run {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.Default) { SymmetryRenderer.render(base, mode, axisX, axisY) }
    }

    fun saveToDefault() {
        val bitmap = source ?: return
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val rendered = SymmetryRenderer.render(bitmap, mode, axisX, axisY)
                    val bytes = ImageExporter.encode(rendered, format, quality.toInt())
                    val name = ImageExporter.buildFileName(format)
                    ImageExporter.saveToPictures(context, bytes, name, format.mimeType)
                    ImageExporter.defaultDirectoryLabel()
                }
            }
            busy = false
            message = outcome.fold(
                onSuccess = { directory -> resources.getString(R.string.symmetry_saved_to, directory) },
                onFailure = { it.message },
            )
        }
    }

    fun applyAndContinue() {
        val bitmap = source ?: return
        scope.launch {
            busy = true
            val rendered = withContext(Dispatchers.Default) {
                SymmetryRenderer.render(bitmap, mode, axisX, axisY)
            }
            source = rendered
            previewSource = withContext(Dispatchers.Default) { SymmetryRenderer.scaleDown(rendered, PreviewMaxSize) }
            axisX = 0.5f
            axisY = 0.5f
            busy = false
        }
    }

    MochiLazyLargeTitleScaffold(
        title = stringResource(R.string.symmetry_title),
        navigationIcon = { MochiBackButton(onClick = onBack) },
    ) {
        item {
            val bitmap = previewSource
            if (bitmap == null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(1f)
                        .clip(MochiRoundedRectangle(24.dp))
                        .background(MochiTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MochiIonicon(
                            "images",
                            style = MochiIconStyle.Outline,
                            tint = MochiTheme.colorScheme.onSurfaceVariant,
                            size = 40.dp,
                        )
                        Text(
                            stringResource(R.string.symmetry_empty),
                            style = MochiTheme.textStyles.subhead,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                SymmetryEditor(
                    bitmap = bitmap,
                    mode = mode,
                    axisX = axisX,
                    axisY = axisY,
                    onAxisChange = { x, y ->
                        if (x != null) axisX = x
                        if (y != null) axisY = y
                    },
                )
                Text(
                    stringResource(R.string.symmetry_drag_hint),
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
                )
            }
        }

        if (preview != null) {
            item {
                MochiListSection(header = stringResource(R.string.symmetry_preview)) {
                    Image(
                        preview!!.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                            .clip(MochiRoundedRectangle(16.dp)),
                        contentScale = ContentScale.FillWidth,
                    )
                }
            }
        }

        item {
            MochiListSection(header = stringResource(R.string.symmetry_mode)) {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    MochiSegmentedControl(
                        selectedIndex = mode.ordinal,
                        onSelect = { index -> mode = SymmetryMode.entries[index] },
                        items = persistentListOf(
                            stringResource(R.string.symmetry_mode_left_right),
                            stringResource(R.string.symmetry_mode_top_bottom),
                            stringResource(R.string.symmetry_mode_both),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        item {
            MochiListSection(header = stringResource(R.string.symmetry_output)) {
                MochiListRow(
                    title = stringResource(R.string.symmetry_output_format),
                    subtitle = format.extension.uppercase(),
                    leading = { MochiIonicon("document-text", style = MochiIconStyle.Filled) },
                    trailing = {
                        MochiDropdown(
                            expanded = formatExpanded,
                            onExpandedChange = { formatExpanded = it },
                            items = persistentListOf(
                                *ImageFormat.entries.map { MochiItem(it.extension.uppercase()) }.toTypedArray(),
                            ),
                            selectedIndex = format.ordinal,
                            onItemClick = { index ->
                                format = ImageFormat.entries[index]
                                formatExpanded = false
                            },
                            buttonMinWidth = 96.dp,
                            buttonContent = {
                                Text(format.extension.uppercase(), maxLines = 1)
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
                if (format.supportsQuality) {
                    MochiListRow(
                        title = stringResource(R.string.symmetry_quality),
                        subtitle = "${quality.toInt()}%",
                        leading = { MochiIonicon("options", style = MochiIconStyle.Filled) },
                    )
                    MochiSlider(
                        value = quality,
                        onValueChange = { quality = it },
                        valueRange = 10f..100f,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MochiButton(
                    onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.weight(1f),
                    style = MochiButtonStyle.Outlined,
                ) {
                    MochiIonicon("images", style = MochiIconStyle.Filled)
                    Text(stringResource(R.string.symmetry_pick))
                }
                MochiButton(
                    onClick = ::applyAndContinue,
                    modifier = Modifier.weight(1f),
                    enabled = source != null && !busy,
                    style = MochiButtonStyle.Outlined,
                ) {
                    MochiIonicon("sync", style = MochiIconStyle.Filled)
                    Text(stringResource(R.string.symmetry_apply))
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MochiButton(
                    onClick = ::saveToDefault,
                    modifier = Modifier.weight(1f),
                    enabled = source != null && !busy,
                    style = MochiButtonStyle.Filled,
                ) {
                    MochiIonicon("download", style = MochiIconStyle.Filled)
                    Text(stringResource(R.string.symmetry_save))
                }
                MochiButton(
                    onClick = {
                        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                            addCategory(Intent.CATEGORY_OPENABLE)
                            type = format.mimeType
                            putExtra(Intent.EXTRA_TITLE, ImageExporter.buildFileName(format))
                        }
                        runCatching { saveAsLauncher.launch(intent) }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = source != null && !busy,
                    style = MochiButtonStyle.Outlined,
                ) {
                    MochiIonicon("folder-open", style = MochiIconStyle.Filled)
                    Text(stringResource(R.string.symmetry_save_as))
                }
            }
        }

        item {
            Text(
                message ?: stringResource(R.string.symmetry_save_location, ImageExporter.defaultDirectoryLabel()),
                style = MochiTheme.textStyles.footnote,
                color = if (message != null) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

/** 图片 + 反色的对称轴，可拖动。 */
@Composable
private fun SymmetryEditor(
    bitmap: Bitmap,
    mode: SymmetryMode,
    axisX: Float,
    axisY: Float,
    onAxisChange: (Float?, Float?) -> Unit,
) {
    val aspect = bitmap.width.toFloat() / bitmap.height.toFloat()
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(aspect)
            .clip(MochiRoundedRectangle(24.dp))
            .background(MochiTheme.colorScheme.surfaceVariant)
            .pointerInput(mode) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    fun update(position: Offset) {
                        val x = if (mode == SymmetryMode.TopBottom) null
                        else (position.x / size.width).coerceIn(0.02f, 0.98f)
                        val y = if (mode == SymmetryMode.LeftRight) null
                        else (position.y / size.height).coerceIn(0.02f, 0.98f)
                        onAxisChange(x, y)
                    }
                    update(down.position)
                    down.consume()
                    drag(down.id) { change ->
                        update(change.position)
                        change.consume()
                    }
                }
            },
    ) {
        Image(
            bitmap.asImageBitmap(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
        )
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 2.dp.toPx()
            if (mode != SymmetryMode.TopBottom) {
                val x = size.width * axisX
                drawLine(
                    color = Color.White,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                    blendMode = BlendMode.Difference,
                )
            }
            if (mode != SymmetryMode.LeftRight) {
                val y = size.height * axisY
                drawLine(
                    color = Color.White,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = stroke,
                    blendMode = BlendMode.Difference,
                )
            }
        }
    }
}

private fun loadBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val width = info.size.width
        val height = info.size.height
        val longest = maxOf(width, height)
        if (longest > maxSize) {
            val scale = maxSize.toFloat() / longest
            decoder.setTargetSize(
                (width * scale).toInt().coerceAtLeast(1),
                (height * scale).toInt().coerceAtLeast(1),
            )
        }
    }
}.getOrNull()
