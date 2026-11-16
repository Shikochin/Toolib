package dev.shiko.toolib.ui.symmetry

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.drawable.AnimatedImageDrawable
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import dev.shiko.toolib.logic.AnimatedWebpWriter
import dev.shiko.toolib.logic.GifWriter
import dev.shiko.toolib.logic.ImageExporter
import dev.shiko.toolib.logic.ImageFormat
import dev.shiko.toolib.logic.SaveLocationStore
import dev.shiko.toolib.logic.SymmetryMode
import dev.shiko.toolib.logic.SymmetryRenderer
import dev.shiko.toolib.ui.components.SaveLocationSection
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSlider
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiToastState
import ui.feice.mochi.icons.MochiIconStyle
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.list.MochiListSectionHeader
import ui.feice.mochi.navigation.MochiBackButton
import ui.feice.mochi.navigation.MochiLazyCompactTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme

private const val SourceMaxSize = 2400
private const val PreviewMaxSize = 720
private const val FrameIntervalMillis = 66L

/** 动图每帧 1/100 秒数（约 14fps）。 */
private const val AnimatedDelayCentiseconds = 7

/** 只是防止「动画永远回不到第一帧」导致死循环的兜底，正常动图靠循环检测收尾。 */
private const val CaptureSafetyFrames = 1000

/** 对称艺术：选图 → 拖反色线定对称轴 → 实时预览 → 继续对称 / 保存。 */
@Composable
fun SymmetryPage(
    header: @Composable () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val store = remember(context) { SaveLocationStore(context) }
    val toast = rememberMochiToastState()

    var source by remember { mutableStateOf<Bitmap?>(null) }
    var frames by remember { mutableStateOf<List<Bitmap>?>(null) }
    var previewSource by remember { mutableStateOf<Bitmap?>(null) }
    var preview by remember { mutableStateOf<Bitmap?>(null) }
    var mode by remember { mutableStateOf(SymmetryMode.Left) }
    var axisX by remember { mutableFloatStateOf(0.5f) }
    var axisY by remember { mutableFloatStateOf(0.5f) }
    var format by remember { mutableStateOf(ImageFormat.Png) }
    var quality by remember { mutableFloatStateOf(92f) }
    var formatExpanded by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var saveTreeUri by remember { mutableStateOf(store.load(SaveLocationStore.SymmetryTool)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            val loaded = withContext(Dispatchers.IO) { loadBitmap(context, uri, SourceMaxSize) }
            // 动图先把帧抓下来：之后的预览、应用并继续、导出都用这份帧
            val captured = withContext(Dispatchers.Main.immediate) { loadAnimatedFrames(context, uri) }
            if (loaded == null) {
                toast.show(resources.getString(R.string.symmetry_load_failed))
            } else {
                // 输出格式默认跟随输入格式
                val mime = withContext(Dispatchers.IO) { context.contentResolver.getType(uri) }
                format = ImageExporter.formatForMime(mime)
                source = loaded
                frames = captured
                previewSource = captured?.firstOrNull()
                    ?: withContext(Dispatchers.Default) { SymmetryRenderer.scaleDown(loaded, PreviewMaxSize) }
            }
            busy = false
        }
    }

    // 动图：按帧循环播放，编辑区和预览一起动
    LaunchedEffect(frames) {
        val list = frames?.takeIf { it.isNotEmpty() } ?: return@LaunchedEffect
        var index = 0
        while (true) {
            previewSource = list[index % list.size]
            index++
            delay(FrameIntervalMillis)
        }
    }

    LaunchedEffect(previewSource, mode, axisX, axisY) {
        val base = previewSource ?: run {
            preview = null
            return@LaunchedEffect
        }
        preview = withContext(Dispatchers.Default) { SymmetryRenderer.render(base, mode, axisX, axisY) }
    }

    fun updateSaveLocation(treeUri: String?) {
        saveTreeUri = treeUri
        store.save(SaveLocationStore.SymmetryTool, treeUri)
    }

    fun save() {
        val animatedFrames = frames
        val static = source
        if (animatedFrames == null && static == null) return
        scope.launch {
            busy = true
            val outcome = withContext(Dispatchers.IO) {
                runCatching {
                    val animatedOutput = animatedFrames != null &&
                        (format == ImageFormat.Gif || format == ImageFormat.Webp)
                    val rendered = if (animatedOutput) {
                        animatedFrames.orEmpty().map { SymmetryRenderer.render(it, mode, axisX, axisY) }
                    } else {
                        listOf(SymmetryRenderer.render(static ?: error("no frame"), mode, axisX, axisY))
                    }
                    val bytes = when {
                        rendered.size <= 1 -> ImageExporter.encode(rendered.first(), format, quality.toInt())
                        format == ImageFormat.Gif -> {
                            val pixels = rendered.map { frame ->
                                IntArray(frame.width * frame.height).also {
                                    frame.getPixels(it, 0, frame.width, 0, 0, frame.width, frame.height)
                                }
                            }
                            GifWriter.encodeAnimation(
                                frames = pixels,
                                width = rendered.first().width,
                                height = rendered.first().height,
                                delayCentiseconds = AnimatedDelayCentiseconds,
                            )
                        }
                        format == ImageFormat.Webp -> AnimatedWebpWriter.encode(
                            frameFiles = rendered.map { ImageExporter.encodeLosslessWebp(it) },
                            width = rendered.first().width,
                            height = rendered.first().height,
                            durationMillis = AnimatedDelayCentiseconds * 10,
                        )
                        else -> ImageExporter.encode(rendered.first(), format, quality.toInt())
                    }
                    rendered.forEach { it.recycle() }
                    val name = ImageExporter.buildFileName(format)
                    val tree = saveTreeUri?.takeIf { it.isNotBlank() }?.let(Uri::parse)
                    if (tree != null) {
                        ImageExporter.saveToTree(context, tree, bytes, name, format.mimeType)
                        dev.shiko.toolib.logic.SafPaths.treeDisplayPath(tree)
                    } else {
                        ImageExporter.saveToPictures(context, bytes, name, format.mimeType)
                        ImageExporter.defaultDirectoryLabel()
                    }
                }
            }
            busy = false
            outcome.fold(
                onSuccess = { where -> toast.show(resources.getString(R.string.symmetry_saved_to, where)) },
                onFailure = { error ->
                    toast.show(
                        resources.getString(R.string.symmetry_saved_failed),
                        subtitle = error.message,
                    )
                },
            )
        }
    }

    /** 动图逐帧对称，结果还是动图；静态图照旧。 */
    fun applyAndContinue() {
        val animatedFrames = frames
        val static = source
        if (animatedFrames == null && static == null) return
        scope.launch {
            busy = true
            if (animatedFrames != null) {
                val rendered = withContext(Dispatchers.Default) {
                    animatedFrames.map { SymmetryRenderer.render(it, mode, axisX, axisY) }
                }
                frames = rendered
                source = rendered.firstOrNull()
                previewSource = rendered.firstOrNull()
            } else {
                val rendered = withContext(Dispatchers.Default) {
                    SymmetryRenderer.render(static!!, mode, axisX, axisY)
                }
                source = rendered
                previewSource = withContext(Dispatchers.Default) {
                    SymmetryRenderer.scaleDown(rendered, PreviewMaxSize)
                }
            }
            axisX = 0.5f
            axisY = 0.5f
            busy = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        MochiLazyCompactTitleScaffold(
            title = "",
            navigationIcon = { MochiBackButton(onClick = onBack) },
        ) {
            item { header() }

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
                    Column(modifier = Modifier.fillMaxWidth()) {
                        MochiListSectionHeader(
                            stringResource(R.string.symmetry_preview),
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        val rendered = preview!!
                        Image(
                            rendered.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .aspectRatio(rendered.width.toFloat() / rendered.height.toFloat())
                                .clip(MochiRoundedRectangle(MochiListDefaults.SectionCorner)),
                            contentScale = ContentScale.FillBounds,
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
                                stringResource(R.string.symmetry_mode_left),
                                stringResource(R.string.symmetry_mode_right),
                                stringResource(R.string.symmetry_mode_top),
                                stringResource(R.string.symmetry_mode_bottom),
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
                SaveLocationSection(
                    treeUri = saveTreeUri,
                    defaultLabel = ImageExporter.defaultDirectoryLabel(),
                    onTreeUriChange = ::updateSaveLocation,
                )
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MochiButton(
                        onClick = {
                            picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
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
                MochiButton(
                    onClick = ::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    enabled = source != null && !busy,
                    style = MochiButtonStyle.Filled,
                ) {
                    MochiIonicon("download", style = MochiIconStyle.Filled)
                    Text(stringResource(R.string.symmetry_save))
                }
            }
        }
        MochiToastHost(toast)
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
    // 左右对称拖竖线，上下对称拖横线，四向两条都有
    val showsVerticalAxis = mode == SymmetryMode.Left || mode == SymmetryMode.Right || mode == SymmetryMode.Both
    val showsHorizontalAxis = mode == SymmetryMode.Top || mode == SymmetryMode.Bottom || mode == SymmetryMode.Both
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
                        val x = if (showsVerticalAxis) {
                            (position.x / size.width).coerceIn(0.02f, 0.98f)
                        } else {
                            null
                        }
                        val y = if (showsHorizontalAxis) {
                            (position.y / size.height).coerceIn(0.02f, 0.98f)
                        } else {
                            null
                        }
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
            if (showsVerticalAxis) {
                val x = size.width * axisX
                drawLine(
                    color = Color.White,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = stroke,
                    blendMode = BlendMode.Difference,
                )
            }
            if (showsHorizontalAxis) {
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

/**
 * 按固定间隔把 AnimatedImageDrawable 采样成若干帧，检测到循环（画面回到第一帧）就停。
 * 必须在主线程调用：预览循环也在主线程画同一个 drawable，串行执行才不会打架。
 */
private suspend fun captureFrames(
    drawable: AnimatedImageDrawable,
    maxSize: Int,
    maxFrames: Int = CaptureSafetyFrames,
    intervalMillis: Long = FrameIntervalMillis,
): List<Bitmap> {
    val scale = minOf(1f, maxSize.toFloat() / maxOf(drawable.intrinsicWidth, drawable.intrinsicHeight))
    val width = (drawable.intrinsicWidth * scale).toInt().coerceAtLeast(1)
    val height = (drawable.intrinsicHeight * scale).toInt().coerceAtLeast(1)
    val snapshot = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(snapshot)
    val frames = mutableListOf<Bitmap>()
    var first: IntArray? = null
    drawable.setBounds(0, 0, width, height)
    drawable.start()
    try {
        while (frames.size < maxFrames) {
            delay(intervalMillis)
            drawable.draw(canvas)
            val frame = snapshot.copy(Bitmap.Config.ARGB_8888, false)
            val signature = signatureOf(frame)
            val start = first
            if (start != null && frames.size >= 6 && similarSignature(start, signature)) {
                frame.recycle()
                break
            }
            if (start == null) first = signature
            frames.add(frame)
        }
    } finally {
        // 采样只是借这个 drawable 画几帧，结束后要继续播放，否则预览会停在最后一帧
        drawable.start()
    }
    if (frames.isEmpty()) frames.add(snapshot.copy(Bitmap.Config.ARGB_8888, false))
    return frames
}

/** 8x8 缩略签名，用来判断动图是否回到第一帧。 */
private fun signatureOf(bitmap: Bitmap): IntArray {
    val scaled = Bitmap.createScaledBitmap(bitmap, 8, 8, true)
    val pixels = IntArray(64)
    scaled.getPixels(pixels, 0, 8, 0, 0, 8, 8)
    if (scaled !== bitmap) scaled.recycle()
    return pixels
}

private fun similarSignature(a: IntArray, b: IntArray): Boolean {
    var diff = 0L
    for (i in a.indices) {
        diff += kotlin.math.abs(((a[i] shr 16) and 0xFF) - ((b[i] shr 16) and 0xFF))
        diff += kotlin.math.abs(((a[i] shr 8) and 0xFF) - ((b[i] shr 8) and 0xFF))
        diff += kotlin.math.abs((a[i] and 0xFF) - (b[i] and 0xFF))
    }
    return diff / (a.size * 3) < 10
}

/** 动图先把每一帧抓成位图（预览尺寸）；静态图返回 null。 */
private suspend fun loadAnimatedFrames(context: Context, uri: Uri): List<Bitmap>? {
    val drawable = loadAnimated(context, uri) ?: return null
    return captureFrames(drawable, SourceMaxSize)
}

/** 动图（GIF / 动图 WebP）返回 AnimatedImageDrawable，静态图返回 null。 */
private fun loadAnimated(context: Context, uri: Uri): AnimatedImageDrawable? = runCatching {
    val source = ImageDecoder.createSource(context.contentResolver, uri)
    ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable
}.getOrNull()

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
